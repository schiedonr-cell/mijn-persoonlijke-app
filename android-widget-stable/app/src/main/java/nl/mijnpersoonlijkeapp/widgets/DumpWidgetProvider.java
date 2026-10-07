package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class DumpWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_inbox);
            WidgetStyle.applyInbox(v, context, id);
            v.setOnClickPendingIntent(R.id.inbox_root, WidgetLinks.open(context, "dump", 501));
            manager.updateAppWidget(id, v);
        }
    }
}
