package tritium.utils.network;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import tritium.utils.other.multithreading.MultiThreadingUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpUtils {

    public static InputStream get(String url, Map<String, String> params) throws IOException {
        return get(url, params, null);
    }

    public static String getString(String url, Map<String, String> params) throws IOException {
        return readString(get(url, params));
    }

    public static String deleteString(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return readString(delete(url, params, headers));
    }

    public static String putString(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return readString(put(url, params, headers));
    }

    public static InputStream get(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return request(mapToString(url, params, "?"), null, headers, "GET");
    }

    public static String getString(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return readString(get(url, params, headers));
    }

    public static void getAsync(String url, Map<String, String> params, OnHttpResult onHttpResult) {
        getAsync(url, params, null, onHttpResult);
    }

    public static void getAsync(String url, Map<String, String> params, Map<String, String> headers, OnHttpResult onHttpResult) {
        requestAsync(mapToString(url, params, "?"), null, headers, "GET", onHttpResult);
    }

    public static InputStream post(String url, Map<String, String> params) throws IOException {
        return post(url, params, null);
    }

    public static String postString(String url, Map<String, String> params) throws IOException {
        return readString(post(url, params));
    }

    public static String postString(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return readString(post(url, params, headers));
    }

    public static InputStream post(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return request(url, mapToString(null, params, null), headers, "POST");
    }

    public static void postAsyn(String url, Map<String, String> params, OnHttpResult onHttpResult) {
        postAsyn(url, params, null, onHttpResult);
    }

    public static void postAsyn(String url, Map<String, String> params, Map<String, String> headers, OnHttpResult onHttpResult) {
        requestAsync(url, mapToString(null, params, null), headers, "POST", onHttpResult);
    }

    public static InputStream put(String url, Map<String, String> params) throws IOException {
        return put(url, params, null);
    }

    public static InputStream put(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return request(url, mapToString(null, params, null), headers, "PUT");
    }

    public static void putAsyn(String url, Map<String, String> params, OnHttpResult onHttpResult) {
        putAsyn(url, params, null, onHttpResult);
    }

    public static void putAsyn(String url, Map<String, String> params, Map<String, String> headers, OnHttpResult onHttpResult) {
        requestAsync(url, mapToString(null, params, null), headers, "PUT", onHttpResult);
    }

    public static InputStream delete(String url, Map<String, String> params) throws IOException {
        return delete(url, params, null);
    }

    public static InputStream delete(String url, Map<String, String> params, Map<String, String> headers) throws IOException {
        return request(mapToString(url, params, "?"), null, headers, "DELETE");
    }

    public static void deleteAsync(String url, Map<String, String> params, OnHttpResult onHttpResult) {
        deleteAsync(url, params, null, onHttpResult);
    }

    public static void deleteAsync(String url, Map<String, String> params, Map<String, String> headers, OnHttpResult onHttpResult) {
        requestAsync(mapToString(url, params, "?"), null, headers, "DELETE", onHttpResult);
    }

    public static InputStream request(String url, String params, Map<String, String> headers, String method) throws IOException {
        return request(url, params, headers, method, "application/x-www-form-urlencoded");
    }

    public static InputStream request(String url, String params, Map<String, String> headers, String method, String mediaType) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        method = method.toUpperCase();
        OutputStreamWriter writer = null;
        URL httpUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) httpUrl.openConnection();
        if (method.equals("POST") || method.equals("PUT")) {
            conn.setDoOutput(true);
            conn.setUseCaches(false);
        }
        conn.setReadTimeout(8000);
        conn.setConnectTimeout(5000);
        conn.setRequestMethod(method);
//        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
//        conn.setRequestProperty("Accept-Charset", "utf-8");
//        conn.setRequestProperty("Content-Type", mediaType);
        // 添加请求�?
        if (headers != null) {
            for (String key : headers.keySet()) {
                conn.setRequestProperty(key, headers.get(key));
            }
        }
        // 添加参数
        if (params != null) {
            conn.setRequestProperty("Content-Length", String.valueOf(params.length()));
            writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(params);
            writer.flush();
        }

        BufferedInputStream bin;
        // 判断连接状�??
        if (conn.getResponseCode() >= 300) {
            throw new RuntimeException("HTTP Request is not success, Response code is " + conn.getResponseCode());
//            bin = new BufferedInputStream(conn.getErrorStream());
        }
        // 获取返回数据

//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        int size;
//        byte[] buf = new byte[1024];
//        while ((size = bin.read(buf)) != -1) {
//            baos.write(buf, 0, size);
//        }
//
//        bin.close();

//        result = new ByteArrayInputStream(baos.toByteArray());

        int contentLength = conn.getContentLength();
        InputStream inputStream = conn.getInputStream();

        OutputStreamWriter finalWriter = writer;
        return new InputStream() {

            @Override
            public int read() throws IOException {
                return inputStream.read();
            }

            @Override
            public int available() {
                return contentLength;
            }

            @Override
            public void close() throws IOException {
                inputStream.close();
                if (finalWriter != null) {
                    try {
                        finalWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    @SneakyThrows
    public static InputStream download(String urlPath) {
        URL url = new URL(urlPath);

        URLConnection urlConnection = url.openConnection();
        HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
        httpURLConnection.setRequestMethod("GET");
        httpURLConnection.setRequestProperty("Charset", "UTF-8");
        httpURLConnection.connect();

        int fileLength = httpURLConnection.getContentLength();

        String fileName = httpURLConnection.getURL().getFile();

        InputStream inputStream = httpURLConnection.getInputStream();

        return new InputStream() {

            @Override
            public int read() throws IOException {
                return inputStream.read();
            }

            @Override
            public int available() {
                return fileLength;
            }

            @Override
            public void close() throws IOException {
                inputStream.close();
            }
        };
    }

    public static void requestAsync(String url, String params, Map<String, String> headers, String method, OnHttpResult onHttpResult) {
        requestAsync(url, params, headers, method, "application/x-www-form-urlencoded", onHttpResult);
    }

    public static void requestAsync(String url, String params, Map<String, String> headers, String method, String mediaType, OnHttpResult onHttpResult) {
        MultiThreadingUtil.runAsync(() -> {
            try {
                InputStream result = request(url, params, headers, method, mediaType);
                onHttpResult.onSuccess(result);
            } catch (Exception e) {
                onHttpResult.onError(e.getMessage());
            }
        });
    }

    @SneakyThrows
    public static String mapToString(String url, Map<String, String> params, String first) {
        StringBuilder sb;
        if (url != null) {
            sb = new StringBuilder(url);
        } else {
            sb = new StringBuilder();
        }
        if (params != null) {
            boolean isFirst = true;
            for (String key : params.keySet()) {
                if (isFirst) {
                    if (first != null) {
                        sb.append(first);
                    }
                    isFirst = false;
                } else {
                    sb.append("&");
                }
                sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                sb.append("=");
                sb.append(URLEncoder.encode(params.get(key), StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    @SneakyThrows
    public static String readString(InputStream stream) {
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }
    @Getter
    @Setter
    private static int retryTimes = 10;

    public static InputStream downloadStream(String path) {
        return downloadStream(path, 0);
    }

    public static InputStream downloadStream(String path, int retry) {
        InputStream bin = null;
        try {
            URL url = new URL(path);
            URLConnection urlConnection = url.openConnection();
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Charset", "UTF-8");
            httpURLConnection.setReadTimeout(10 * 1000);
            httpURLConnection.connect();

            bin = httpURLConnection.getInputStream();
        } catch (Exception err) {
            if (retry >= retryTimes) {
                throw new RuntimeException("Max retry time reached for url " + path);
            }
            return downloadStream(path, ++retry);
        }
        return bin;
    }

    public interface OnHttpResult {
        void onSuccess(InputStream result);

        void onError(String message);
    }
}