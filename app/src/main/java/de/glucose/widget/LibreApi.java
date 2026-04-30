package de.glucose.widget;

import org.json.JSONObject;
import okhttp3.*;
import java.security.MessageDigest;

public class LibreApi {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // SHA256-Hash der Account-ID
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static Headers buildHeaders(String token, String accountId) {
        Headers.Builder b = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("product", "llu.android")
                .add("version", "4.16.0")
                .add("Accept", "application/json")
                .add("Cache-Control", "no-cache")
                .add("Connection", "keep-alive")
                .add("Pragma", "no-cache")
                .add("User-Agent", "okhttp/4.9.3");
        if (token != null)     b.add("Authorization", "Bearer " + token);
        if (accountId != null && !accountId.isEmpty())
                               b.add("account-id", sha256(accountId));
        return b.build();
    }

    public static JSONObject login(String email, String password, String region) throws Exception {
        String url = "https://api-" + region + ".libreview.io/llu/auth/login";
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);

        Request req = new Request.Builder()
                .url(url)
                .headers(buildHeaders(null, null))
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String raw = resp.body().string();
            android.util.Log.d("LibreApi", "Login: " + raw);
            return new JSONObject(raw);
        }
    }

    public static JSONObject getGlucose(String token, String accountId, String region) throws Exception {
        String url = "https://api-" + region + ".libreview.io/llu/connections";

        Request req = new Request.Builder()
                .url(url)
                .headers(buildHeaders(token, accountId))
                .get()
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() == 401) return new JSONObject("{\"status\":401}");
            String raw = resp.body().string();
            android.util.Log.d("LibreApi", "Glucose: " + raw);
            return new JSONObject(raw);
        }
    }
}
