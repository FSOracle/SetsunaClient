package com.setsuna.util.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.setsuna.Setsuna;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Implements the Microsoft, Xbox Live, XSTS and Minecraft authentication chain. */
public final class MicrosoftAuthService {

    private static final String CLIENT_ID = "c52aed44-3b4d-4215-99c5-824033d2bc0f";
    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String AUTH_SCOPE = "XboxLive.signin offline_access";

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    public CompletableFuture<Alt> loginDeviceCode(LoginCallback callback) {
        LoginCallback listener = callback == null ? LoginCallback.NOOP : callback;
        return CompletableFuture.supplyAsync(() -> {
            try {
                listener.setStatus("Requesting Microsoft device code...");
                DeviceCodeResponse device = requestDeviceCode();
                copyUserCode(device.userCode());
                listener.setStatus("Copied code " + device.userCode() + ". Open " + device.verificationUri());
                openVerificationUri(device.verificationUri());
                OAuthToken token = pollDeviceToken(device, listener);
                return completeLogin(token, listener);
            } catch (Exception error) {
                listener.onFailed(error);
                throw new AuthenticationException(error);
            }
        }, Util.ioPool());
    }

    public CompletableFuture<Alt> refresh(Alt alt, LoginCallback callback) {
        LoginCallback listener = callback == null ? LoginCallback.NOOP : callback;
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (alt == null || alt.getRefreshToken().isBlank()) {
                    throw new IOException("Microsoft refresh token is empty");
                }
                listener.setStatus("Refreshing Microsoft token...");
                OAuthToken token = requestRefreshToken(alt.getRefreshToken());
                return completeLogin(token, listener);
            } catch (Exception error) {
                listener.onFailed(error);
                throw new AuthenticationException(error);
            }
        }, Util.ioPool());
    }

    public CompletableFuture<Alt> loginMinecraftToken(String minecraftAccessToken, LoginCallback callback) {
        LoginCallback listener = callback == null ? LoginCallback.NOOP : callback;
        return CompletableFuture.supplyAsync(() -> {
            try {
                String accessToken = normalizeBearerToken(minecraftAccessToken);
                if (accessToken.isBlank()) {
                    throw new IOException("Minecraft token is empty");
                }

                listener.setStatus("Checking Minecraft token...");
                MinecraftProfile profile = fetchMinecraftProfile(accessToken);
                long issuedAt = parseJwtLong(accessToken, "iat");
                if (issuedAt <= 0L) {
                    issuedAt = System.currentTimeMillis() / 1000L;
                }
                String xuid = parseJwtString(accessToken, "xuid");

                Alt alt = new Alt(profile.name(), "", accessToken, profile.id(), issuedAt, xuid);
                listener.onSucceed(alt);
                return alt;
            } catch (Exception error) {
                listener.onFailed(error);
                throw new AuthenticationException(error);
            }
        }, Util.ioPool());
    }

    private DeviceCodeResponse requestDeviceCode() throws IOException, InterruptedException {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("client_id", CLIENT_ID);
        parameters.put("scope", AUTH_SCOPE);
        JsonObject json = postForm(DEVICE_CODE_URL, parameters);
        requireNoError(json, "Microsoft device code");
        String verificationUri = json.has("verification_uri")
                ? json.get("verification_uri").getAsString()
                : getString(json, "verification_url");
        return new DeviceCodeResponse(
                getString(json, "device_code"),
                getString(json, "user_code"),
                verificationUri,
                json.has("interval") ? json.get("interval").getAsInt() : 5,
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 900
        );
    }

    private OAuthToken pollDeviceToken(DeviceCodeResponse device, LoginCallback callback)
            throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + device.expiresIn() * 1000L;
        int interval = Math.max(1, device.interval());
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);

            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            parameters.put("client_id", CLIENT_ID);
            parameters.put("device_code", device.deviceCode());
            JsonObject json = postForm(TOKEN_URL, parameters);

            if (json.has("access_token")) {
                return parseOAuthToken(json, "");
            }

            String error = json.has("error") ? json.get("error").getAsString() : "";
            if ("authorization_pending".equals(error)) {
                callback.setStatus("Waiting for Microsoft login... Copied code: " + device.userCode());
                continue;
            }
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            requireNoError(json, "Microsoft device token");
        }
        throw new IOException("Microsoft login timed out");
    }

    private OAuthToken requestRefreshToken(String refreshToken) throws IOException, InterruptedException {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("client_id", CLIENT_ID);
        parameters.put("refresh_token", refreshToken);
        parameters.put("grant_type", "refresh_token");
        parameters.put("scope", AUTH_SCOPE);
        JsonObject json = postForm(TOKEN_URL, parameters);
        requireNoError(json, "Microsoft refresh");
        return parseOAuthToken(json, refreshToken);
    }

    private Alt completeLogin(OAuthToken oauthToken, LoginCallback callback)
            throws IOException, InterruptedException {
        callback.setStatus("Signing in to Xbox Live...");
        XboxToken xboxLive = authenticateXboxLive(oauthToken.accessToken());

        callback.setStatus("Authorizing XSTS...");
        XboxToken xsts = authorizeXsts(xboxLive.token());

        callback.setStatus("Getting Minecraft token...");
        MinecraftToken minecraftToken = loginMinecraft(xsts);

        callback.setStatus("Getting Minecraft profile...");
        MinecraftProfile profile = fetchMinecraftProfile(minecraftToken.accessToken());
        Alt alt = new Alt(
                profile.name(),
                oauthToken.refreshToken(),
                minecraftToken.accessToken(),
                profile.id(),
                System.currentTimeMillis() / 1000L,
                xsts.uhs()
        );
        callback.onSucceed(alt);
        return alt;
    }

    private XboxToken authenticateXboxLive(String accessToken) throws IOException, InterruptedException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + accessToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        JsonObject json = postJson("https://user.auth.xboxlive.com/user/authenticate", body, Map.of());
        requireNoError(json, "Xbox Live");
        return parseXboxToken(json);
    }

    private XboxToken authorizeXsts(String xboxLiveToken) throws IOException, InterruptedException {
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xboxLiveToken);
        properties.add("UserTokens", tokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        JsonObject json = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body, Map.of());
        if (json.has("XErr")) {
            throw new IOException(xstsError(json.get("XErr").getAsLong()));
        }
        requireNoError(json, "XSTS");
        return parseXboxToken(json);
    }

    private MinecraftToken loginMinecraft(XboxToken xsts) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + xsts.uhs() + ";" + xsts.token());
        JsonObject json = postJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                body,
                Map.of()
        );
        requireNoError(json, "Minecraft Services");
        return new MinecraftToken(getString(json, "access_token"));
    }

    private MinecraftProfile fetchMinecraftProfile(String minecraftAccessToken)
            throws IOException, InterruptedException {
        JsonObject json = getJson(
                "https://api.minecraftservices.com/minecraft/profile",
                Map.of("Authorization", "Bearer " + minecraftAccessToken)
        );
        requireNoError(json, "Minecraft profile");
        if (!json.has("id") || !json.has("name")) {
            throw new IOException("This Microsoft account does not own Minecraft Java Edition");
        }
        return new MinecraftProfile(getString(json, "id"), getString(json, "name"));
    }

    private JsonObject postForm(String url, Map<String, String> parameters)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return sendJson(request);
    }

    private JsonObject postJson(String url, JsonObject body, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        headers.forEach(builder::header);
        return sendJson(builder.build());
    }

    private JsonObject getJson(String url, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET();
        headers.forEach(builder::header);
        return sendJson(builder.build());
    }

    private JsonObject sendJson(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        String body = response.body() == null ? "" : response.body();
        JsonObject json;
        try {
            json = body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Invalid JSON response (HTTP " + response.statusCode() + ")", error);
        }
        if (response.statusCode() >= 500) {
            throw new IOException("HTTP " + response.statusCode() + ": " + body);
        }
        return json;
    }

    private String normalizeBearerToken(String token) {
        String value = token == null ? "" : token.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String parseJwtString(String token, String key) {
        JsonObject payload = parseJwtPayload(token);
        return payload != null && payload.has(key) ? payload.get(key).getAsString() : "";
    }

    private long parseJwtLong(String token, String key) {
        JsonObject payload = parseJwtPayload(token);
        return payload != null && payload.has(key) ? payload.get(key).getAsLong() : 0L;
    }

    private JsonObject parseJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private OAuthToken parseOAuthToken(JsonObject json, String fallbackRefreshToken) throws IOException {
        String refreshToken = json.has("refresh_token")
                ? json.get("refresh_token").getAsString()
                : fallbackRefreshToken;
        return new OAuthToken(getString(json, "access_token"), refreshToken);
    }

    private XboxToken parseXboxToken(JsonObject json) throws IOException {
        try {
            String userHash = json.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui")
                    .get(0)
                    .getAsJsonObject()
                    .get("uhs")
                    .getAsString();
            return new XboxToken(getString(json, "Token"), userHash);
        } catch (RuntimeException error) {
            throw new IOException("Invalid Xbox authentication response", error);
        }
    }

    private void requireNoError(JsonObject json, String step) throws IOException {
        if (json.has("error")) {
            String error = json.get("error").getAsString();
            String description = json.has("error_description")
                    ? json.get("error_description").getAsString()
                    : error;
            throw new IOException(step + " failed: " + description);
        }
    }

    private String getString(JsonObject json, String key) throws IOException {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IOException("Missing response field: " + key);
        }
        return json.get(key).getAsString();
    }

    private void openVerificationUri(String uri) {
        try {
            Util.getPlatform().openUri(uri);
        } catch (Exception primaryError) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(uri));
                }
            } catch (Exception ignored) {
                Setsuna.LOGGER.warn("Unable to open Microsoft login page: {}", uri, primaryError);
            }
        }
    }

    private void copyUserCode(String userCode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || userCode == null || userCode.isBlank()) {
            return;
        }
        minecraft.execute(() -> minecraft.keyboardHandler.setClipboard(userCode));
    }

    private String xstsError(long code) {
        if (code == 2148916233L) {
            return "This account does not have an Xbox account";
        }
        if (code == 2148916235L) {
            return "Xbox Live is not available for this account region";
        }
        if (code == 2148916236L || code == 2148916237L) {
            return "This account needs adult verification";
        }
        if (code == 2148916238L) {
            return "This child account cannot continue without family approval";
        }
        return "Unknown XSTS error: " + code;
    }

    public interface LoginCallback {

        LoginCallback NOOP = new LoginCallback() {
            @Override
            public void setStatus(String status) {
            }

            @Override
            public void onSucceed(Alt alt) {
            }

            @Override
            public void onFailed(Exception error) {
            }
        };

        void setStatus(String status);

        void onSucceed(Alt alt);

        void onFailed(Exception error);
    }

    private static final class AuthenticationException extends RuntimeException {

        private AuthenticationException(Exception cause) {
            super(cause);
        }
    }

    private record DeviceCodeResponse(
            String deviceCode,
            String userCode,
            String verificationUri,
            int interval,
            int expiresIn
    ) {
    }

    private record OAuthToken(String accessToken, String refreshToken) {
    }

    private record XboxToken(String token, String uhs) {
    }

    private record MinecraftToken(String accessToken) {
    }

    private record MinecraftProfile(String id, String name) {
    }
}
