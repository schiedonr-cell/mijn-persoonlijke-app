package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class FocusWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_focus);
            WidgetStyle.applyFocus(v, context, id);
            v.setOnClickPendingIntent(R.id.focus_root, WidgetLinks.open(context, "focus", 301));
            manager.updateAppWidget(id, v);
        }
    }
}
