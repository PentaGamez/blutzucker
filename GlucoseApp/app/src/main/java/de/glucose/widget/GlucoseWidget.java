package de.glucose.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class GlucoseWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName comp = new ComponentName(context, GlucoseWidget.class);
        int[] ids = manager.getAppWidgetIds(comp);
        for (int id : ids) updateWidget(context, manager, id);
    }

    static void updateWidget(Context context, AppWidgetManager manager, int id) {
        SharedPreferences prefs = context.getSharedPreferences("glucose", Context.MODE_PRIVATE);

        String value  = prefs.getString("last_value", "-- mmol/L");
        String trend  = prefs.getString("last_trend", "→");
        String status = prefs.getString("last_status", "");
        int color     = prefs.getInt("last_color", 0xFF00FF88);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.wValue, value);
        views.setTextViewText(R.id.wTrend, trend);
        views.setTextViewText(R.id.wStatus, status);
        views.setTextColor(R.id.wValue, color);
        views.setTextColor(R.id.wTrend, color);

        manager.updateAppWidget(id, views);
    }
}
