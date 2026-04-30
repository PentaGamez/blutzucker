package de.glucose.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                    GlucoseWorker.class, 15, TimeUnit.MINUTES).build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "glucose_refresh",
                    ExistingPeriodicWorkPolicy.KEEP,
                    work);
        }
    }
}
