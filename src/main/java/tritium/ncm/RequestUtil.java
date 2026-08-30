package tritium.ncm;

import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import tritium.utils.json.JsonUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网易云音乐API请求工具类
 */
public class RequestUtil {

    private static final SecureRandom random = new SecureRandom();

    private static final Map<String, OsConfig> OS_MAP = new HashMap<>();

    public static String globalDeviceId = "";

    private static final Map<String, Map<String, String>> USER_AGENT_MAP = new HashMap<>();

    static {
        Map<String, String> weapiUA = new HashMap<>();
        weapiUA.put("pc", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.0.0");
        USER_AGENT_MAP.put("weapi", weapiUA);

        Map<String, String> linuxapiUA = new HashMap<>();
        linuxapiUA.put("linux", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36");
        USER_AGENT_MAP.put("linuxapi", linuxapiUA);

        Map<String, String> apiUA = new HashMap<>();
        apiUA.put("pc", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152");
        apiUA.put("android", "NeteaseMusic/9.1.65.240927161425(9001065);Dalvik/2.1.0 (Linux; U; Android 14; 23013RK75C Build/UKQ1.230804.001)");
        apiUA.put("iphone", "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)");
        USER_AGENT_MAP.put("api", apiUA);
    }


    @Data
    @Builder
    public static class AppConfig {
        private String domain;
        private String apiDomain;
        private boolean encrypt;
        private boolean encryptResponse;
    }

    private static final AppConfig APP_CONF = AppConfig.builder()
            .domain("https://music.163.com")
            .apiDomain("https://interfacepc.music.163.com")
            .encrypt(true)
            .encryptResponse(false)
            .build();

    static {
        OS_MAP.put("pc", OsConfig.builder()
                .os("pc")
                .appver("3.1.12.204072")
                .osver("Microsoft-Windows-11-Professional-build-26100-64bit")
                .channel("netease")
                .build());

        OS_MAP.put("linux", OsConfig.builder()
                .os("linux")
                .appver("1.2.1.0428")
                .osver("Deepin 20.9")
                .channel("netease")
                .build());

        OS_MAP.put("android", OsConfig.builder()
                .os("android")
                .appver("8.20.20.231215173437")
                .osver("14")
                .channel("xiaomi")
                .build());

        OS_MAP.put("iphone", OsConfig.builder()
                .os("iPhone OS")
                .appver("9.0.90")
                .osver("16.2")
                .channel("distribution")
                .build());

//        RequestAnswer request = CloudMusicApi.registerAnonimous();
//
//        String register = "";
//
//        for (String cookie : request.getCookies()) {
//
//            String[] split = cookie.split("=");
//
//            if (split[0].equals("MUSIC_A")) {
//                register = split[1].substring(0, split[1].lastIndexOf(";"));
//                break;
//            }
//
//        }
//
//        ANONYMOUS_TOKEN = register;
    }

    @Data
    @Builder
    public static class OsConfig {
        private String os;
        private String appver;
        private String osver;
        private String channel;
    }

    @Data
    @Builder
    public static class RequestOptions {
        private Map<String, String> headers;
        private String realIP;
        private String ip;
        private Object cookie;
        private String crypto;
        private String ua;
        private String proxy;
        /**
         * 如果为 null 的话则使用 APP_CONF 的默认值
         */
        private Boolean encryptedResponse;
    }

    @Data
    @Builder
    public static class RequestAnswer {
        private int status;
        private Object body;
        private String[] cookies;

        public static RequestAnswer of(JsonObject object, int status, String[] cookies) {
            return new RequestAnswer(status, object, cookies);
        }

        public String toString() {
            return JsonUtils.toJsonString(this.body);
        }

        public JsonObject toJsonObject() {
            return JsonUtils.parse(toString(), JsonObject.class);
        }

//        public JsonObject toJsonObjectPrettyPrinting() {
//            return JsonUtils.parse(toString(), JsonObject.class);
//        }
    }

    /**
     * 生成WNMCID
     */
    private static String generateWNMCID() {
        String characters = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder randomString = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            randomString.append(characters.charAt(random.nextInt(characters.length())));
        }
        return randomString + "." + System.currentTimeMillis() + ".01.0";
    }

    /**
     * 生成随机字符串
     */
    private static String generateRandomString(int length) {
        byte[] bytes = generateRandomBytes(length);
        return bytesToHex(bytes);
    }

    public static byte[] generateRandomBytes(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 选择User-Agent
     */
    private static String chooseUserAgent(String crypto, String uaType) {
        if (uaType == null) uaType = "pc";
        Map<String, String> cryptoMap = USER_AGENT_MAP.get(crypto);
        if (cryptoMap != null) {
            return cryptoMap.getOrDefault(uaType, "");
        }
        return "";
    }

    /**
     * Cookie字符串转Map
     */
    private static Map<String, String> cookieToMap(String cookieStr) {
        Map<String, String> cookieMap = new HashMap<>();
        if (StringUtils.isNotBlank(cookieStr)) {
            String[] pairs = cookieStr.split(";\\s*");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    cookieMap.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return cookieMap;
    }

    /**
     * Cookie Map转字符串
     */
    @SneakyThrows
    private static String cookieMapToString(Map<String, String> cookieMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * 构建表单数据
     */
    private static String buildFormData(Map<String, String> formData) {
        return formData.entrySet().stream()
                .map(entry -> {
                    try {
                        return URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) +
                                "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return entry.getKey() + "=" + entry.getValue();
                    }
                })
                .collect(Collectors.joining("&"));
    }

    /**
     * 创建HTTP请求
     */
    public static RequestAnswer createRequest(String uri, Object data, RequestOptions options) {
        try {
            Map<String, String> headers = options.getHeaders() != null ?
                    new HashMap<>(options.getHeaders()) : new HashMap<>();

            String ip = StringUtils.isNotBlank(options.getRealIP()) ?
                    options.getRealIP() : options.getIp();

            if (StringUtils.isNotBlank(ip)) {
                headers.put("X-Real-IP", ip);
                headers.put("X-Forwarded-For", ip);
            }

            Map<String, String> cookie = new HashMap<>();
            if (options.getCookie() instanceof String) {
                cookie = cookieToMap((String) options.getCookie());
            } else if (options.getCookie() instanceof Map) {
                cookie = (Map<String, String>) options.getCookie();
            }

            String nuid = generateRandomString(32);
            OsConfig osConfig = OS_MAP.getOrDefault(cookie.get("os"), OS_MAP.get("pc"));

            cookie.putIfAbsent("__remember_me", "true");
            cookie.putIfAbsent("ntes_kaola_ad", "1");
            cookie.putIfAbsent("_ntes_nuid", nuid);
            cookie.putIfAbsent("_ntes_nnid", nuid + "," + System.currentTimeMillis());
            cookie.putIfAbsent("WNMCID", generateWNMCID());
            cookie.putIfAbsent("WEVNSM", "1.0.0");
            cookie.putIfAbsent("osver", osConfig.getOsver());
            cookie.putIfAbsent("deviceId", generateRandomString(26).toUpperCase() + "\\r");
            cookie.putIfAbsent("os", osConfig.getOs());
            cookie.putIfAbsent("channel", osConfig.getChannel());
            cookie.putIfAbsent("appver", osConfig.getAppver());

            if (!uri.contains("login")) {
                cookie.put("NMTID", generateRandomString(16));
            }

//            if (!cookie.containsKey("MUSIC_U") && !uri.contains("anonimous")) {
//                cookie.putIfAbsent("MUSIC_A", ANONYMOUS_TOKEN);
//            }

            headers.put("Cookie", cookieMapToString(cookie));

            String crypto = StringUtils.isNotBlank(options.getCrypto()) ?
                    options.getCrypto() : (APP_CONF.isEncrypt() ? "eapi" : "api");

            String url = "";
            String postData = "";
            String csrfToken = cookie.getOrDefault("__csrf", "");

            switch (crypto) {
                case "weapi":
                    headers.put("Referer", APP_CONF.getDomain());
                    headers.put("User-Agent", StringUtils.isNotBlank(options.getUa()) ?
                            options.getUa() : chooseUserAgent("weapi", "pc"));

                    Map<String, Object> weapiData = new HashMap<>();
                    if (data instanceof Map) {
                        weapiData.putAll((Map<String, Object>) data);
                    }
                    weapiData.put("csrf_token", csrfToken);

                    CryptoUtil.WeapiResult weapiResult = CryptoUtil.weapi(weapiData);
                    url = APP_CONF.getDomain() + "/weapi/" + uri.substring(5);

                    Map<String, String> weapiFormData = new HashMap<>();
                    weapiFormData.put("params", weapiResult.getParams());
                    weapiFormData.put("encSecKey", weapiResult.getEncSecKey());
                    postData = buildFormData(weapiFormData);
                    break;

                case "linuxapi":
                    headers.put("User-Agent", StringUtils.isNotBlank(options.getUa()) ?
                            options.getUa() : chooseUserAgent("linuxapi", "linux"));

                    Map<String, Object> linuxApiRequest = new HashMap<>();
                    linuxApiRequest.put("method", "POST");
                    linuxApiRequest.put("url", APP_CONF.getDomain() + uri);
                    linuxApiRequest.put("params", data);

                    CryptoUtil.LinuxapiResult linuxResult = CryptoUtil.linuxapi(linuxApiRequest);
                    url = APP_CONF.getDomain() + "/api/linux/forward";

                    Map<String, String> linuxFormData = new HashMap<>();
                    linuxFormData.put("eparams", linuxResult.getEparams());
                    postData = buildFormData(linuxFormData);
                    break;

                case "eapi":
                case "api":
                    Map<String, String> header = new HashMap<>();
                    header.put("osver", cookie.get("osver"));
                    header.put("deviceId", cookie.get("deviceId"));
                    header.put("os", cookie.get("os"));
                    header.put("appver", cookie.get("appver"));
                    header.put("versioncode", cookie.getOrDefault("versioncode", "140"));
                    header.put("mobilename", cookie.getOrDefault("mobilename", ""));
                    header.put("buildver", cookie.getOrDefault("buildver",
                            String.valueOf(System.currentTimeMillis()).substring(0, 10)));
                    header.put("resolution", cookie.getOrDefault("resolution", "1920x1080"));
                    header.put("__csrf", csrfToken);
                    header.put("channel", cookie.get("channel"));
                    header.put("requestId", System.currentTimeMillis() + "_" +
                            String.format("%04d", random.nextInt(1000)));

                    if (cookie.containsKey("MUSIC_U")) {
                        header.put("MUSIC_U", cookie.get("MUSIC_U"));
                    }
                    if (cookie.containsKey("MUSIC_A")) {
                        header.put("MUSIC_A", cookie.get("MUSIC_A"));
                    }

                    String value = cookieMapToString(header);
                    headers.put("Cookie", value);
                    headers.put("User-Agent", StringUtils.isNotBlank(options.getUa()) ?
                            options.getUa() : chooseUserAgent("api", "pc"));

                    if ("eapi".equals(crypto)) {
                        Map<String, Object> eapiData = new HashMap<>();
                        if (data instanceof Map) {
                            eapiData.putAll((Map<String, Object>) data);
                        }
                        eapiData.put("header", header);

                        boolean eR = options.getEncryptedResponse() != null ? options.getEncryptedResponse() : APP_CONF.isEncryptResponse();
                        eapiData.put("e_r", eR);

                        CryptoUtil.EapiResult eapiResult = CryptoUtil.eapi(uri, eapiData);
                        url = APP_CONF.getApiDomain() + "/eapi/" + uri.substring(5);

                        Map<String, String> eapiFormData = new HashMap<>();
                        eapiFormData.put("params", eapiResult.getParams());
                        postData = buildFormData(eapiFormData);
                    } else {
                        url = APP_CONF.getApiDomain() + uri;
                        postData = JsonUtils.toJsonString(data);
                    }
                    break;

                default:
                    System.err.println("Unknown Crypto: " + crypto);
                    throw new IllegalArgumentException("Unknown crypto type: " + crypto);
            }

            URL requestUrl = new URL(url);
            HttpURLConnection connection = null;

            if (StringUtils.isNotBlank(options.getProxy())) {
                String[] proxyParts = options.getProxy().split(":");
                if (proxyParts.length == 2) {
                    String proxyHost = proxyParts[0];
                    int proxyPort = Integer.parseInt(proxyParts[1]);
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                    connection = (HttpURLConnection) requestUrl.openConnection(proxy);
                } else {
                    connection = (HttpURLConnection) requestUrl.openConnection();
                }
            } else {
                connection = (HttpURLConnection) requestUrl.openConnection();
            }

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setDoInput(true);

            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            if (postData != null && !postData.isEmpty()) {
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = connection.getResponseCode();

            String responseBody = "";
            InputStream inputStream = null;
            try {
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }

                if (inputStream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        responseBody = response.toString();
                    }
                }
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

            RequestAnswer answer = RequestAnswer.builder()
                    .status(responseCode)
                    .build();

            if (!responseBody.isEmpty()) {
                if (Boolean.TRUE.equals(options.getEncryptedResponse()) && "eapi".equals(crypto)) {
                    try {
                        answer.setBody(CryptoUtil.eapiResDecrypt(responseBody));
                    } catch (Exception e) {
                        answer.setBody(JsonUtils.parse(responseBody, Object.class));
                    }
                } else {
                    try {
                        answer.setBody(JsonUtils.parse(responseBody, Object.class));
                    } catch (Exception e) {
                        answer.setBody(responseBody);
                    }
                }
            }

            Map<String, List<String>> headerFields = connection.getHeaderFields();
            List<String> setCookieHeaders = headerFields.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (setCookieHeaders != null && !setCookieHeaders.isEmpty()) {
                answer.setCookies(setCookieHeaders.toArray(new String[0]));
            }

            connection.disconnect();

//            System.out.println("POST " + url);
//            System.out.println("Response: " + responseCode);
//            System.out.println("Response Body: " + answer.toString());

            return answer;

        } catch (Exception e) {
            System.err.println("Request failed");
            e.printStackTrace();

            Map<String, Object> map = new HashMap<>();
            map.put("code", 502);
            map.put("msg", e.getMessage());

            return RequestAnswer.builder()
                    .status(502)
                    .body(map)
                    .build();
        }
    }
}
