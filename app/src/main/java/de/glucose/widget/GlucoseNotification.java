package de.glucose.widget;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class GlucoseNotification {

    private static final String CHANNEL_ID = "glucose_live";
    private static final int    NOTIF_ID   = 1001;

    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Blutzucker Live",
                NotificationManager.IMPORTANCE_DEFAULT  // zeigt Icon oben links in Statusleiste
            );
            channel.setDescription("Dauerhafter Blutzuckerwert");
            channel.setShowBadge(false);
            channel.setSound(null, null);               // kein Ton
            channel.enableVibration(false);             // keine Vibration
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    public static void update(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("glucose", Context.MODE_PRIVATE);
        String value  = prefs.getString("last_value",  "-- mmol/L");
        String trend  = prefs.getString("last_trend",  "→");
        String status = prefs.getString("last_status", "");
        int    color  = prefs.getInt("last_color",     0xFF00FF88);

        // Intent: Tap öffnet die App
        Intent openApp = new Intent(ctx, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)      // Icon oben links in Statusleiste
                .setContentTitle(value + "  " + trend)
                .setContentText(status)
                .setContentIntent(pi)
                .setOngoing(true)                       // angeheftet, nicht wegwischbar
                .setSilent(true)                        // kein Ton, keine Vibration
                .setShowWhen(false)                     // keine Uhrzeit
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(color)
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // ganz oben, Icon in Statusleiste
                .build();

        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif);
    }

    public static void cancel(Context ctx) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID);
    }
}
