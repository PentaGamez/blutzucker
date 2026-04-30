package de.glucose.widget;

import android.app.Activity;
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

    // Views – Login
    View loginLayout, mainLayout;
    EditText etEmail, etPassword;
    Spinner spRegion;
    Button btnLogin;
    TextView tvLoginError;

    // Views – Main
    TextView tvValue, tvTrend, tvStatus, tvTime, tvLastUpdated;
    Button btnRefresh, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("glucose", MODE_PRIVATE);

        // Bind views
        loginLayout    = findViewById(R.id.loginLayout);
        mainLayout     = findViewById(R.id.mainLayout);
        etEmail        = findViewById(R.id.etEmail);
        etPassword     = findViewById(R.id.etPassword);
        spRegion       = findViewById(R.id.spRegion);
        btnLogin       = findViewById(R.id.btnLogin);
        tvLoginError   = findViewById(R.id.tvLoginError);
        tvValue        = findViewById(R.id.tvValue);
        tvTrend        = findViewById(R.id.tvTrend);
        tvStatus       = findViewById(R.id.tvStatus);
        tvTime         = findViewById(R.id.tvTime);
        tvLastUpdated  = findViewById(R.id.tvLastUpdated);
        btnRefresh     = findViewById(R.id.btnRefresh);
        btnLogout      = findViewById(R.id.btnLogout);

        // Region spinner
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"eu", "de", "us", "ap"});
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRegion.setAdapter(regionAdapter);

        btnLogin.setOnClickListener(v -> doLogin());
        btnRefresh.setOnClickListener(v -> loadGlucose());
        btnLogout.setOnClickListener(v -> logout());

        // Auto-login
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
            tvLoginError.setText("Bitte E-Mail und Passwort eingeben.");
            tvLoginError.setVisibility(View.VISIBLE);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verbinden…");
        tvLoginError.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                JSONObject result = LibreApi.login(email, password, region);

                // Region redirect
                if (result.optJSONObject("data") != null &&
                    result.optJSONObject("data").optBoolean("redirect", false)) {
                    String newRegion = result.getJSONObject("data").optString("region", "eu");
                    handler.post(() -> {
                        tvLoginError.setText("Region zu \"" + newRegion.toUpperCase() + "\" geändert → nochmal versuchen");
                        tvLoginError.setVisibility(View.VISIBLE);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Verbinden");
                    });
                    return;
                }

                String token = result
                    .getJSONObject("data")
                    .getJSONObject("authTicket")
                    .getString("token");

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
                handler.post(() -> {
                    tvLoginError.setText("Fehler: " + e.getMessage());
                    tvLoginError.setVisibility(View.VISIBLE);
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Verbinden");
                });
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

                // Token abgelaufen
                if (data.optInt("status") == 401) {
                    relogin();
                    return;
                }

                JSONObject measurement = data
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONObject("glucoseMeasurement");

                double value    = measurement.getDouble("Value");
                int trendArrow  = measurement.optInt("TrendArrow", 3);
                int glucoseUnit = measurement.optInt("GlucoseUnits", 0);
                String timestamp = measurement.optString("FactoryTimestamp", "");

                boolean isMmol = glucoseUnit == 0;
                String[] arrows = {"", "↓↓", "↓", "→", "↑", "↑↑"};
                String[] trendNames = {"", "Schnell fallend", "Fallend", "Stabil", "Steigend", "Schnell steigend"};
                String arrow = trendArrow >= 1 && trendArrow <= 5 ? arrows[trendArrow] : "→";
                String trendName = trendArrow >= 1 && trendArrow <= 5 ? trendNames[trendArrow] : "Stabil";

                String valStr = isMmol
                    ? String.format("%.1f", value)
                    : String.valueOf((int) value);
                String unit = isMmol ? "mmol/L" : "mg/dL";

                double v = isMmol ? value : value / 18.0;
                String statusText;
                int statusColor;
                if (v < 3.9)       { statusText = "ZU NIEDRIG ⚠"; statusColor = 0xFFFF3B5C; }
                else if (v < 4.4)  { statusText = "NIEDRIG";       statusColor = 0xFFFF9500; }
                else if (v <= 10)  { statusText = "ZIELBEREICH ✓"; statusColor = 0xFF00FF88; }
                else if (v <= 13.9){ statusText = "ERHÖHT";         statusColor = 0xFFFF9500; }
                else               { statusText = "ZU HOCH ⚠";     statusColor = 0xFFFF3B5C; }

                int valueColor = statusColor;

                // Save for widget
                prefs.edit()
                    .putString("last_value", valStr + " " + unit)
                    .putString("last_trend", arrow)
                    .putString("last_status", statusText)
                    .putInt("last_color", statusColor)
                    .apply();

                GlucoseWidget.updateAll(this);

                handler.post(() -> {
                    tvValue.setText(valStr);
                    tvValue.setTextColor(valueColor);
                    tvTrend.setText(arrow);
                    tvTrend.setTextColor(valueColor);
                    tvStatus.setText(statusText + " · " + unit);
                    tvStatus.setTextColor(valueColor);
                    tvTime.setText(trendName);

                    java.time.LocalTime now = java.time.LocalTime.now();
                    tvLastUpdated.setText(String.format("Zuletzt: %02d:%02d",
                        now.getHour(), now.getMinute()));
                });

            } catch (Exception e) {
                handler.post(() -> tvLastUpdated.setText("Fehler: " + e.getMessage()));
            }
        }).start();
    }

    void relogin() {
        String email    = prefs.getString("email", "");
        String password = prefs.getString("password", "");
        String region   = prefs.getString("region", "eu");
        try {
            JSONObject result = LibreApi.login(email, password, region);
            String token = result
                .getJSONObject("data")
                .getJSONObject("authTicket")
                .getString("token");
            prefs.edit().putString("token", token).apply();
            loadGlucose();
        } catch (Exception e) {
            handler.post(this::logout);
        }
    }

    void scheduleWorker() {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                GlucoseWorker.class, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "glucose_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                work);
    }

    void showMain() {
        loginLayout.setVisibility(View.GONE);
        mainLayout.setVisibility(View.VISIBLE);
    }

    void logout() {
        prefs.edit().clear().apply();
        WorkManager.getInstance(this).cancelAllWork();
        mainLayout.setVisibility(View.GONE);
        loginLayout.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(true);
        btnLogin.setText("Verbinden");
    }
}
