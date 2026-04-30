package de.glucose.widget;

import org.json.JSONObject;
import okhttp3.*;

public class LibreApi {

    // OkHttp handhabt gzip automatisch wenn wir Accept-Encoding NICHT manuell setzen
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static Headers libreHeaders(String token) {
        Headers.Builder b = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("product", "llu.android")
                .add("version", "4.16.0")
                .add("Accept", "application/json")
                .add("Cache-Control", "no-cache")
                .add("Connection", "keep-alive")
                .add("Pragma", "no-cache")
                .add("account-id", "")
                .add("User-Agent", "okhttp/4.9.3");
        if (token != null) b.add("Authorization", "Bearer " + token);
        return b.build();
    }

    public static JSONObject login(String email, String password, String region) throws Exception {
        String url = "https://api-" + region + ".libreview.io/llu/auth/login";
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);

        Request req = new Request.Builder()
                .url(url)
                .headers(libreHeaders(null))
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String bodyStr = resp.body().string();
            android.util.Log.d("LibreApi", "Login raw: " + bodyStr);
            return new JSONObject(bodyStr);
        }
    }

    public static JSONObject getGlucose(String token, String region) throws Exception {
        String url = "https://api-" + region + ".libreview.io/llu/connections";

        Request req = new Request.Builder()
                .url(url)
                .headers(libreHeaders(token))
                .get()
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() == 401) return new JSONObject("{\"status\":401}");
            String bodyStr = resp.body().string();
            android.util.Log.d("LibreApi", "Glucose raw: " + bodyStr);
            return new JSONObject(bodyStr);
        }
    }
}
