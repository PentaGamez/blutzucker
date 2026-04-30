package de.glucose.widget;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

public class GlucoseNotification {

    private static final String CHANNEL_ID = "glucose_live";
    private static final int    NOTIF_ID   = 1001;

    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Blutzucker Live",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Dauerhafter Blutzuckerwert");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    /**
     * Rendert Wert + Trendpfeil als weißes Bitmap-Icon.
     * In der Statusleiste oben links sichtbar (Android erzwingt Weiß dort).
     * Im Notification Shade wird es in der richtigen Farbe angezeigt.
     *
     * Beispiel: "5.4→"  oder  "17.6↑"
     */
    private static Bitmap createValueIcon(String value, String trend) {
        // Breites Bitmap damit Wert + Pfeil nebeneinander passen
        int width  = 160;
        int height = 80;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        String label = value + trend;  // z.B. "5.4→" oder "17.6↑"

        // Schriftgröße je nach Länge anpassen
        float textSize;
        if (label.length() <= 4)      textSize = 44f;
        else if (label.length() <= 5) textSize = 38f;
        else                          textSize = 30f;

        paint.setTextSize(textSize);

        // Vertikal zentrieren
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = height / 2f - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(label, width / 2f, y, paint);
        return bmp;
    }

    public static void update(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("glucose", Context.MODE_PRIVATE);
        String value  = prefs.getString("last_value",  "--");
        String trend  = prefs.getString("last_trend",  "→");
        String status = prefs.getString("last_status", "");
        int    color  = prefs.getInt("last_color",     0xFF00FF88);

        // Nur Zahl extrahieren: "17.6 mmol/L" → "17.6"
        String shortValue = value.split(" ")[0];

        Bitmap iconBitmap = createValueIcon(shortValue, trend);

        Intent openApp = new Intent(ctx, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(IconCompat.createWithBitmap(iconBitmap))  // Wert+Pfeil in Statusleiste
                .setContentTitle(value + "  " + trend)
                .setContentText(status)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(color)
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif);
    }

    public static void cancel(Context ctx) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID);
    }
}
