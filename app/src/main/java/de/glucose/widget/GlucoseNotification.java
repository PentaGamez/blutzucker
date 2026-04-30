package de.glucose.widget;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
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
                NotificationManager.IMPORTANCE_LOW  // kein Sound, kein Popup
            );
            channel.setDescription("Dauerhafter Blutzuckerwert in der Statusleiste");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    /**
     * Erzeugt ein Bitmap-Icon mit dem Blutzuckerwert als Text.
     * Android zeigt dieses Icon oben in der Statusleiste an –
     * der Wert ist direkt sichtbar ohne die Benachrichtigung aufzuklappen.
     *
     * @param value  z.B. "5.4" oder "17.6"
     * @param color  Farbe des Textes (wird von Android in der Statusleiste auf Weiß erzwungen,
     *               aber im Notification Shade in der richtigen Farbe angezeigt)
     */
    private static Bitmap createTextBitmap(String value, int color) {
        // Größe: 96x96px – Android skaliert das Icon intern
        int size = 96;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE); // Statusleiste zeigt immer Weiß
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);

        // Schriftgröße je nach Länge anpassen (z.B. "5.4" vs "17.6")
        float textSize = value.length() <= 3 ? 44f : 36f;
        paint.setTextSize(textSize);

        // Vertikal zentrieren
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = size / 2f - (fm.ascent + fm.descent) / 2f;

        canvas.drawText(value, size / 2f, y, paint);
        return bmp;
    }

    public static void update(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("glucose", Context.MODE_PRIVATE);
        String value  = prefs.getString("last_value",  "-- mmol/L");
        String trend  = prefs.getString("last_trend",  "→");
        String status = prefs.getString("last_status", "");
        int    color  = prefs.getInt("last_color",     0xFF00FF88);

        // Nur die Zahl extrahieren (ohne " mmol/L" oder " mg/dL")
        // z.B. "17.6 mmol/L" → "17.6"  |  "126 mg/dL" → "126"
        String shortValue = value.split(" ")[0];

        // Icon mit Zahlenwert als Bitmap
        Bitmap iconBitmap = createTextBitmap(shortValue, color);

        // Intent: Tap öffnet die App
        Intent openApp = new Intent(ctx, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(value + "  " + trend)
                .setContentText(status)
                .setContentIntent(pi)
                .setOngoing(true)           // nicht wegwischbar
                .setSilent(true)            // kein Ton
                .setShowWhen(false)         // keine Uhrzeit
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(color)
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // Bitmap-Icon setzen (zeigt den Wert in der Statusleiste)
        // IconCompat direkt übergeben – nicht .toIcon() aufrufen, da setSmallIcon(IconCompat) erwartet wird
        builder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap));

        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, builder.build());
    }

    public static void cancel(Context ctx) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID);
    }
}
