package de.glucose.widget;

import org.json.JSONObject;
import java.io.IOException;
import okhttp3.*;

public class LibreApi {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static Headers libreHeaders(String token) {
        Headers.Builder b = new Headers.Builder()
                .add("Content-Type", "application/json")
                .add("product", "llu.android")
                .add("version", "4.7.0")
                .add("Accept", "application/json")
                .add("User-Agent", "Mozilla/5.0");
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
            String responseBody = resp.body().string();
            return new JSONObject(responseBody);
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
            if (resp.code() == 401) {
                return new JSONObject("{\"status\":401}");
            }
            String responseBody = resp.body().string();
            return new JSONObject(responseBody);
        }
    }
}
