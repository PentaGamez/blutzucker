package de.glucose.widget;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.*;
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
                // Versuche Login mit gewählter Region
                JSONObject result = LibreApi.login(email, password, region);
                
                // Debug: zeige rohe Antwort
                String rawResponse = result.toString();
                android.util.Log.d("GlucoseApp", "Login response: " + rawResponse);

                int status = result.optInt("status", -1);
                
                // Region-Weiterleitung
                JSONObject data = result.optJSONObject("data");
                if (data != null && data.optBoolean("redirect", false)) {
                    String newRegion = data.optString("region", "eu");
                    // Nochmal mit neuer Region versuchen
                    result = LibreApi.login(email, password, newRegion);
                    data = result.optJSONObject("data");
                    prefs.edit().putString("region", newRegion).apply();
                }

                if (data == null) {
                    // Zeige was die API tatsächlich zurückgibt
                    showError("API Antwort: " + rawResponse.substring(0, Math.min(200, rawResponse.length())));
                    handler.post(() -> { btnLogin.setEnabled(true); btnLogin.setText("Verbinden"); });
                    return;
                }

                JSONObject ticket = data.optJSONObject("authTicket");
                if (ticket == null) {
                    showError("Kein Token erhalten. Antwort: " + data.toString().substring(0, Math.min(200, data.toString().length())));
                    handler.post(() -> { btnLogin.setEnabled(true); btnLogin.setText("Verbinden"); });
                    return;
                }

                String token = ticket.getString("token");

                prefs.edit()
                    .putString("token", token)
                    .putString("region", region)
                    .putString("email", email)
                    .putString("password", password)
                    .apply();

                handler.post(() -> {
                    showMain();
                    loadGlucose();
                    scheduleWorker();
                });

            } catch (Exception e) {
                showError("Fehler: " + e.getMessage());
                handler.post(() -> { btnLogin.setEnabled(true); btnLogin.setText("Verbinden"); });
            }
        }).start();
    }

    void loadGlucose() {
        tvLastUpdated.setText("Wird geladen…");
        String token  = prefs.getString("token", null);
        String region = prefs.getString("region", "eu");

        new Thread(() -> {
            try {
                JSONObject data = LibreApi.getGlucose(token, region);
                android.util.Log.d("GlucoseApp", "Glucose response: " + data.toString());

                if (data.optInt("status") == 401) {
                    relogin();
                    return;
                }

                // Prüfe ob data-Array vorhanden
                if (!data.has("data") || data.isNull("data")) {
                    handler.post(() -> tvLastUpdated.setText("Keine Daten. Ist LibreLinkUp aktiviert?"));
                    return;
                }

                var dataArr = data.getJSONArray("data");
                if (dataArr.length() == 0) {
                    handler.post(() -> tvLastUpdated.setText("Keine Verbindungen gefunden. LibreLinkUp prüfen."));
                    return;
                }

                JSONObject measurement = dataArr
                    .getJSONObject(0)
                    .getJSONObject("glucoseMeasurement");

                double value   = measurement.getDouble("Value");
                int trendArrow = measurement.optInt("TrendArrow", 3);
                boolean isMmol = measurement.optInt("GlucoseUnits", 0) == 0;

                String[] arrows    = {"", "↓↓", "↓", "→", "↑", "↑↑"};
                String[] trendNames = {"", "Schnell fallend", "Fallend", "Stabil", "Steigend", "Schnell steigend"};
                String arrow     = trendArrow >= 1 && trendArrow <= 5 ? arrows[trendArrow] : "→";
                String trendName = trendArrow >= 1 && trendArrow <= 5 ? trendNames[trendArrow] : "Stabil";
                String valStr    = isMmol ? String.format("%.1f", value) : String.valueOf((int) value);
                String unit      = isMmol ? "mmol/L" : "mg/dL";

                double v = isMmol ? value : value / 18.0;
                String statusText;
                int statusColor;
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

                GlucoseWidget.updateAll(this);

                handler.post(() -> {
                    tvValue.setText(valStr);
                    tvValue.setTextColor(statusColor);
                    tvTrend.setText(arrow);
                    tvTrend.setTextColor(statusColor);
                    tvStatus.setText(statusText + " · " + unit);
                    tvStatus.setTextColor(statusColor);
                    tvTime.setText(trendName);

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
            String email    = prefs.getString("email", "");
            String password = prefs.getString("password", "");
            String region   = prefs.getString("region", "eu");
            JSONObject result = LibreApi.login(email, password, region);
            JSONObject data   = result.optJSONObject("data");
            if (data != null) {
                JSONObject ticket = data.optJSONObject("authTicket");
                if (ticket != null) {
                    prefs.edit().putString("token", ticket.getString("token")).apply();
                    loadGlucose();
                    return;
                }
            }
            handler.post(this::logout);
        } catch (Exception e) {
            handler.post(this::logout);
        }
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
        handler.post(() -> {
            mainLayout.setVisibility(View.GONE);
            loginLayout.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(true);
            btnLogin.setText("Verbinden");
        });
    }
}
