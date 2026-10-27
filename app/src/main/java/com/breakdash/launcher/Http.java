package com.breakdash.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class Http {
    private Http() {}
    static HttpURLConnection open(String address, String method) throws Exception {
        java.net.URL url = new URI(address).toURL();
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("HTTPS is required");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000); connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "BreakDash/" + BuildConfig.VERSION_NAME + " (Android car launcher)");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }
    static HttpURLConnection get(String address) throws Exception {
        String current = address;
        for (int i = 0; i < 6; i++) {
            checkInterrupted();
            HttpURLConnection connection = open(current, "GET");
            int status;
            try { status = connection.getResponseCode(); }
            catch (Exception error) { connection.disconnect(); throw error; }
            if (status >= 300 && status < 400) {
                String redirect = connection.getHeaderField("Location"); connection.disconnect();
                if (redirect == null) throw new IOException("Missing download redirect");
                current = new URI(current).resolve(redirect).toString();
            } else if (status >= 200 && status < 300) return connection;
            else { connection.disconnect(); throw new IOException("HTTP " + status + (status == 403 || status == 429 ? " (access or rate limit)" : "")); }
        }
        throw new IOException("Too many redirects");
    }
    static String json(String address) throws Exception {
        HttpURLConnection connection = get(address);
        try (InputStream stream = connection.getInputStream()) { return read(stream, 6 * 1024 * 1024); }
        finally { connection.disconnect(); }
    }
    static String read(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = stream.read(buffer)) != -1) {
            checkInterrupted();
            if (output.size() + count > limit) throw new IOException("Response exceeds size limit");
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
    static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
    }
}
