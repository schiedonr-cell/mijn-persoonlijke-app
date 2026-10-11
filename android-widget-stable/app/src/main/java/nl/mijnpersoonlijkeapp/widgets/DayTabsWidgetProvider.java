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
    private static final String KEY = "selected_day";
    private static final String ACTION = "nl.mijnpersoonlijkeapp.widgets.CHOOSE_DAY";

    static boolean isTomorrow(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION.equals(intent.getAction())) {
            boolean tomorrow = intent.getBooleanExtra("tomorrow", false);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY, tomorrow).apply();
            SnapshotStore.refreshCategoryWidgets(context);
            TodayWidgetProvider.refreshAll(context);
            TodayTimelineWidgetProvider.refreshAll(context);
            refreshAll(context);
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(
                new ComponentName(context, DayTabsWidgetProvider.class))) update(context, manager, id);
    }

    static PendingIntent click(Context context, boolean tomorrow) {
        Intent intent = new Intent(context, DayTabsWidgetProvider.class);
        intent.setAction(ACTION);
        intent.putExtra("tomorrow", tomorrow);
        return PendingIntent.getBroadcast(context, tomorrow ? 7272 : 7271, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_day_tabs);
        boolean tomorrow = isTomorrow(context);
        views.setOnClickPendingIntent(R.id.day_tab_today, click(context, false));
        views.setOnClickPendingIntent(R.id.day_tab_tomorrow, click(context, true));
        views.setTextColor(R.id.day_tab_today, tomorrow ? Color.rgb(190,190,190) : Color.WHITE);
        views.setTextColor(R.id.day_tab_tomorrow, tomorrow ? Color.WHITE : Color.rgb(190,190,190));
        views.setInt(R.id.day_tab_today, "setBackgroundResource",
                tomorrow ? R.drawable.widget_bg_transparent : R.drawable.widget_bg_dark);
        views.setInt(R.id.day_tab_tomorrow, "setBackgroundResource",
                tomorrow ? R.drawable.widget_bg_dark : R.drawable.widget_bg_transparent);
        manager.updateAppWidget(id, views);
    }
}
