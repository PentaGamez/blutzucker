package de.glucose.widget;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

public class GlucoseWorker extends Worker {

    public GlucoseWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("glucose", Context.MODE_PRIVATE);

        String token  = prefs.getString("token", null);
        String region = prefs.getString("region", "eu");
        String email  = prefs.getString("email", null);
        String pass   = prefs.getString("password", null);

        if (token == null) return Result.failure();

        try {
            JSONObject data = LibreApi.getGlucose(token, region);

            // Token abgelaufen → neu einloggen
            if (data.optInt("status") == 401 && email != null && pass != null) {
                JSONObject loginResult = LibreApi.login(email, pass, region);
                token = loginResult.getJSONObject("data")
                        .getJSONObject("authTicket")
                        .getString("token");
                prefs.edit().putString("token", token).apply();
                data = LibreApi.getGlucose(token, region);
            }

            JSONObject m = data
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getJSONObject("glucoseMeasurement");

            double value   = m.getDouble("Value");
            int trend      = m.optInt("TrendArrow", 3);
            boolean isMmol = m.optInt("GlucoseUnits", 0) == 0;

            String[] arrows = {"", "↓↓", "↓", "→", "↑", "↑↑"};
            String valStr = isMmol
                    ? String.format("%.1f", value) + " mmol/L"
                    : (int) value + " mg/dL";
            String arrow = trend >= 1 && trend <= 5 ? arrows[trend] : "→";

            double v = isMmol ? value : value / 18.0;
            String statusText;
            int statusColor;
            if (v < 3.9)       { statusText = "ZU NIEDRIG ⚠"; statusColor = 0xFFFF3B5C; }
            else if (v < 4.4)  { statusText = "NIEDRIG";       statusColor = 0xFFFF9500; }
            else if (v <= 10)  { statusText = "ZIELBEREICH";   statusColor = 0xFF00FF88; }
            else if (v <= 13.9){ statusText = "ERHÖHT";         statusColor = 0xFFFF9500; }
            else               { statusText = "ZU HOCH ⚠";     statusColor = 0xFFFF3B5C; }

            prefs.edit()
                    .putString("last_value", valStr)
                    .putString("last_trend", arrow)
                    .putString("last_status", statusText)
                    .putInt("last_color", statusColor)
                    .apply();

            GlucoseWidget.updateAll(getApplicationContext());
            return Result.success();

        } catch (Exception e) {
            return Result.retry();
        }
    }
}
