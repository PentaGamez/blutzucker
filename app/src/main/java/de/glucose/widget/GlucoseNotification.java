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
     * Rendert den Wert als Icon.
     * Trick: Wir nutzen ein quadratisches Bitmap mit sehr großer Schrift,
     * damit Android nach dem Verkleinern noch lesbare Zahlen zeigt.
     * Der Text wird so groß wie möglich gezeichnet und füllt das Bitmap fast komplett aus.
     */
    private static Bitmap createValueIcon(String value, String trend) {
        int size = 256; // groß rendern, Android skaliert runter – Qualität bleibt
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        String label = value + trend; // z.B. "5.4→"

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // Schriftgröße automatisch so groß wie möglich wählen damit Text das Icon füllt
        // Zielbreite: 90% des Bitmaps
        float targetWidth = size * 0.90f;
        float textSize = 200f;
        paint.setTextSize(textSize);

        // Solange verkleinern bis Text reinpasst
        while (paint.measureText(label) > targetWidth && textSize > 20f) {
            textSize -= 2f;
            paint.setTextSize(textSize);
        }

        // Vertikal zentrieren
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(label, size / 2f, y, paint);
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
                .setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
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
