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
     * Großes rundes Icon mit Wert + Pfeil – wird als LargeIcon angezeigt
     * (links in der Notification, groß wie WhatsApp-Profilbild).
     */
    private static Bitmap createLargeIcon(String value, String trend, int color) {
        int size = 256;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Dunkler Kreis als Hintergrund
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.argb(220, 20, 20, 20));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);

        // Farbiger Ring außen
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(color);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(10f);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f, ringPaint);

        String label = value + trend; // z.B. "5.4→"

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(color);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // Schriftgröße automatisch anpassen
        float targetWidth = size * 0.80f;
        float textSize = 120f;
        textPaint.setTextSize(textSize);
        while (textPaint.measureText(label) > targetWidth && textSize > 20f) {
            textSize -= 2f;
            textPaint.setTextSize(textSize);
        }

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, size / 2f, y, textPaint);

        return bmp;
    }

    /**
     * Kleines weißes Icon für die schmale Statusleiste oben –
     * Android erzwingt hier immer Weiß und ~24dp, Text wäre zu klein.
     * Daher nur die Träne als Erkennungszeichen.
     */
    private static Bitmap createSmallIcon(String value, String trend) {
        int size = 256;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        String label = value + trend;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        float targetWidth = size * 0.90f;
        float textSize = 180f;
        paint.setTextSize(textSize);
        while (paint.measureText(label) > targetWidth && textSize > 20f) {
            textSize -= 2f;
            paint.setTextSize(textSize);
        }

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

        String shortValue = value.split(" ")[0]; // "17.6 mmol/L" → "17.6"

        Bitmap largeIcon = createLargeIcon(shortValue, trend, color);
        Bitmap smallIcon = createSmallIcon(shortValue, trend);

        Intent openApp = new Intent(ctx, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(IconCompat.createWithBitmap(smallIcon)) // oben links (immer klein)
                .setLargeIcon(largeIcon)                              // großes Icon links in Notification
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
