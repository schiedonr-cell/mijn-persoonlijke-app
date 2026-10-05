package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class TodayWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today);
            v.setOnClickPendingIntent(R.id.today_root, WidgetLinks.open(context, "today", 201));
            v.setOnClickPendingIntent(R.id.btn_today_open, WidgetLinks.open(context, "today", 202));
            manager.updateAppWidget(id, v);
        }
    }
}
