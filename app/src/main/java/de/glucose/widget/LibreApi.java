package de.glucose.widget;

import org.json.JSONObject;
import okhttp3.*;

public class LibreApi {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static Headers baseHeaders() {
        return new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("product", "llu.android")
                .add("version", "4.16.0")
                .add("Accept", "application/json")
                .add("Cache-Control", "no-cache")
                .add("Connection", "keep-alive")
                .add("Pragma", "no-cache")
                .add("User-Agent", "okhttp/4.9.3")
                .build();
    }

    // Login – gibt das gesamte JSONObject zurück
    public static JSONObject login(String email, String password, String region) throws Exception {
        String url = "https://api-" + region + ".libreview.io/llu/auth/login";
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);

        Request req = new Request.Builder()
                .url(url)
                .headers(baseHeaders())
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String raw = resp.body().string();
            android.util.Log.d("LibreApi", "Login raw: " + raw);
            return new JSONObject(raw);
        }
    }

    // Glucose – braucht token UND accountId
    public static JSONObject getGlucose(String token, String accountId, String region) throws Exception {
        String url = "https://api-" + region + ".libreview.io/llu/connections";

        Headers headers = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("product", "llu.android")
                .add("version", "4.16.0")
                .add("Accept", "application/json")
                .add("Cache-Control", "no-cache")
                .add("Connection", "keep-alive")
                .add("Pragma", "no-cache")
                .add("User-Agent", "okhttp/4.9.3")
                .add("Authorization", "Bearer " + token)
                .add("account-id", accountId != null ? accountId : "")
                .build();

        Request req = new Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() == 401) return new JSONObject("{\"status\":401}");
            String raw = resp.body().string();
            android.util.Log.d("LibreApi", "Glucose raw: " + raw);
            return new JSONObject(raw);
        }
    }
}
