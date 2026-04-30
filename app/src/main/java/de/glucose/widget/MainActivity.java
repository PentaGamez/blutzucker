package de.glucose.widget;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    SharedPreferences prefs;
    Handler handler = new Handler(Looper.getMainLooper());

    View loginLayout, mainLayout;
    EditText etEmail, etPassword;
    Spinner spRegion;
    Button btnLogin;
    TextView tvLoginError;
    TextView tvValue, tvTrend, tvStatus, tvTime, tvLastUpdated;
    Button btnRefresh, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("glucose", MODE_PRIVATE);

        loginLayout   = findViewById(R.id.loginLayout);
        mainLayout    = findViewById(R.id.mainLayout);
        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        spRegion      = findViewById(R.id.spRegion);
        btnLogin      = findViewById(R.id.btnLogin);
        tvLoginError  = findViewById(R.id.tvLoginError);
        tvValue       = findViewById(R.id.tvValue);
        tvTrend       = findViewById(R.id.tvTrend);
        tvStatus      = findViewById(R.id.tvStatus);
        tvTime        = findViewById(R.id.tvTime);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        btnRefresh    = findViewById(R.id.btnRefresh);
        btnLogout     = findViewById(R.id.btnLogout);

        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"eu", "de", "us", "ap", "ae"});
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRegion.setAdapter(regionAdapter);

        btnLogin.setOnClickListener(v -> doLogin());
        btnRefresh.setOnClickListener(v -> loadGlucose());
        btnLogout.setOnClickListener(v -> logout());

        // Notification Channel erstellen
        GlucoseNotification.createChannel(this);

        // Benachrichtigungs-Permission anfragen (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        String token = prefs.getString("token", null);
        if (token != null) {
            showMain();
            loadGlucose();
            scheduleWorker();
        }
    }

    void doLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String region   = spRegion.getSelectedItem().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Bitte E-Mail und Passwort eingeben.");
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verbinden…");
        tvLoginError.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                JSONObject result = LibreApi.login(email, password, region);
                String raw = result.toString();

                JSONObject data = result.optJSONObject("data");
                if (data != null && data.optBoolean("redirect", false)) {
                    String newRegion = data.optString("region", "eu");
                    result = LibreApi.login(email, password, newRegion);
                    data = result.optJSONObject("data");
                    prefs.edit().putString("region", newRegion).apply();
                }

                if (data == null) {
                    showError("Login fehlgeschlagen:\n" + raw.substring(0, Math.min(300, raw.length())));
                    return;
                }

                JSONObject ticket = data.optJSONObject("authTicket");
                if (ticket == null) {
                    showError("Kein Token:\n" + data.toString().substring(0, Math.min(300, data.toString().length())));
                    return;
                }

                String token     = ticket.getString("token");
                JSONObject user  = data.optJSONObject("user");
                String accountId = user != null ? user.optString("id", "") : "";

                prefs.edit()
                    .putString("token", token)
                    .putString("region", region)
                    .putString("email", email)
                    .putString("password", password)
                    .putString("accountId", accountId)
                    .apply();

                handler.post(() -> {
                    showMain();
                    loadGlucose();
                    scheduleWorker();
                });

            } catch (Exception e) {
                showError("Fehler: " + e.getMessage());
            }
        }).start();
    }

    void loadGlucose() {
        handler.post(() -> tvLastUpdated.setText("Wird geladen…"));
        String token     = prefs.getString("token", null);
        String region    = prefs.getString("region", "eu");
        String accountId = prefs.getString("accountId", "");

        new Thread(() -> {
            try {
                JSONObject resp = LibreApi.getGlucose(token, accountId, region);
                String raw = resp.toString();

                if (resp.optInt("status") == 401) { relogin(); return; }

                JSONArray dataArr = null;
                if (resp.has("data")) {
                    Object dataObj = resp.get("data");
                    if (dataObj instanceof JSONArray) dataArr = (JSONArray) dataObj;
                    else if (dataObj instanceof JSONObject) {
                        String info = ((JSONObject) dataObj).toString();
                        handler.post(() -> tvLastUpdated.setText("API: " + info.substring(0, Math.min(200, info.length()))));
                        return;
                    }
                }

                if (dataArr == null || dataArr.length() == 0) {
                    handler.post(() -> tvLastUpdated.setText("Keine Daten.\n" + raw.substring(0, Math.min(150, raw.length()))));
                    return;
                }

                JSONObject m   = dataArr.getJSONObject(0).getJSONObject("glucoseMeasurement");
                double value   = m.getDouble("Value");
                int trend      = m.optInt("TrendArrow", 3);
                boolean isMmol = m.optInt("GlucoseUnits", 0) == 0;

                String[] arrows     = {"", "↓↓", "↓", "→", "↑", "↑↑"};
                String[] trendNames = {"", "Schnell fallend", "Fallend", "Stabil", "Steigend", "Schnell steigend"};
                String arrow     = trend >= 1 && trend <= 5 ? arrows[trend] : "→";
                String trendName = trend >= 1 && trend <= 5 ? trendNames[trend] : "Stabil";
                String valStr    = isMmol ? String.format("%.1f", value) : String.valueOf((int) value);
                String unit      = isMmol ? "mmol/L" : "mg/dL";

                double v = isMmol ? value : value / 18.0;
                String statusText; int statusColor;
                if (v < 3.9)        { statusText = "ZU NIEDRIG ⚠"; statusColor = 0xFFFF3B5C; }
                else if (v < 4.4)   { statusText = "NIEDRIG";       statusColor = 0xFFFF9500; }
                else if (v <= 10.0) { statusText = "ZIELBEREICH ✓"; statusColor = 0xFF00FF88; }
                else if (v <= 13.9) { statusText = "ERHÖHT";         statusColor = 0xFFFF9500; }
                else                { statusText = "ZU HOCH ⚠";     statusColor = 0xFFFF3B5C; }

                prefs.edit()
                    .putString("last_value", valStr + " " + unit)
                    .putString("last_trend", arrow)
                    .putString("last_status", statusText)
                    .putInt("last_color", statusColor)
                    .apply();

                // Widget + Benachrichtigung aktualisieren
                GlucoseWidget.updateAll(this);
                GlucoseNotification.update(this);

                final String fVal = valStr, fUnit = unit, fArrow = arrow, fStatus = statusText, fTrend = trendName;
                final int fColor = statusColor;
                handler.post(() -> {
                    tvValue.setText(fVal);
                    tvValue.setTextColor(fColor);
                    tvTrend.setText(fArrow);
                    tvTrend.setTextColor(fColor);
                    tvStatus.setText(fStatus + " · " + fUnit);
                    tvStatus.setTextColor(fColor);
                    tvTime.setText(fTrend);
                    java.time.LocalTime now = java.time.LocalTime.now();
                    tvLastUpdated.setText(String.format("Zuletzt: %02d:%02d", now.getHour(), now.getMinute()));
                });

            } catch (Exception e) {
                handler.post(() -> tvLastUpdated.setText("Fehler: " + e.getMessage()));
            }
        }).start();
    }

    void relogin() {
        try {
            JSONObject result = LibreApi.login(
                prefs.getString("email", ""),
                prefs.getString("password", ""),
                prefs.getString("region", "eu")
            );
            JSONObject data = result.optJSONObject("data");
            if (data != null) {
                JSONObject ticket = data.optJSONObject("authTicket");
                JSONObject user   = data.optJSONObject("user");
                if (ticket != null) {
                    prefs.edit()
                        .putString("token", ticket.getString("token"))
                        .putString("accountId", user != null ? user.optString("id", "") : "")
                        .apply();
                    loadGlucose();
                    return;
                }
            }
        } catch (Exception ignored) {}
        handler.post(this::logout);
    }

    void scheduleWorker() {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                GlucoseWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "glucose_refresh", ExistingPeriodicWorkPolicy.KEEP, work);
    }

    void showMain() {
        handler.post(() -> {
            loginLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
        });
    }

    void showError(String msg) {
        handler.post(() -> {
            tvLoginError.setText(msg);
            tvLoginError.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(true);
            btnLogin.setText("Verbinden");
        });
    }

    void logout() {
        prefs.edit().clear().apply();
        WorkManager.getInstance(this).cancelAllWork();
        GlucoseNotification.cancel(this);
        handler.post(() -> {
            mainLayout.setVisibility(View.GONE);
            loginLayout.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(true);
            btnLogin.setText("Verbinden");
        });
    }
}
