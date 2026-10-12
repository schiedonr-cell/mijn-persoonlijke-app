package nl.mijnpersoonlijkeapp.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.RemoteViews;

public class DayTabsWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "day_tab_selection";
    private static final String KEY_PREFIX = "widget_day_";
    private static final String ACTION = "nl.mijnpersoonlijkeapp.widgets.CHOOSE_DAY";

    // Iedere Android-widgetinstantie bewaart zijn eigen keuze.
    private static String keyFor(int widgetId) { return KEY_PREFIX + widgetId; }

    static boolean isTomorrow(Context context, int widgetId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(keyFor(widgetId), false);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
        if (info == null || info.provider == null ||
                !context.getPackageName().equals(info.provider.getPackageName())) return;

        boolean tomorrow = intent.getBooleanExtra("tomorrow", false);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(keyFor(widgetId), tomorrow).apply();

        // Ververs uitsluitend het aangetikte vak: de andere widgets houden hun dag.
        Intent refresh = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        refresh.setComponent(info.provider);
        refresh.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        context.sendBroadcast(refresh);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(
                new ComponentName(context, DayTabsWidgetProvider.class))) update(context, manager, id);
    }

    static PendingIntent click(Context context, int widgetId, boolean tomorrow) {
        Intent intent = new Intent(context, DayTabsWidgetProvider.class);
        intent.setAction(ACTION);
        // De data-URI maakt de twee knoppen van ieder vak echt onafhankelijk.
        intent.setData(android.net.Uri.parse("mijndag://dagtab/" + widgetId
                + (tomorrow ? "/morgen" : "/vandaag")));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.putExtra("tomorrow", tomorrow);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day_tabs);
        boolean tomorrow = isTomorrow(context, id);
        views.setOnClickPendingIntent(R.id.day_tab_today, click(context, id, false));
        views.setOnClickPendingIntent(R.id.day_tab_tomorrow, click(context, id, true));
        views.setTextColor(R.id.day_tab_today, tomorrow ? Color.rgb(190,190,190) : Color.WHITE);
        views.setTextColor(R.id.day_tab_tomorrow, tomorrow ? Color.WHITE : Color.rgb(190,190,190));
        views.setInt(R.id.day_tab_today, "setBackgroundResource",
                tomorrow ? R.drawable.widget_bg_transparent : R.drawable.widget_bg_dark);
        views.setInt(R.id.day_tab_tomorrow, "setBackgroundResource",
                tomorrow ? R.drawable.widget_bg_dark : R.drawable.widget_bg_transparent);
        manager.updateAppWidget(id, views);
    }
}
