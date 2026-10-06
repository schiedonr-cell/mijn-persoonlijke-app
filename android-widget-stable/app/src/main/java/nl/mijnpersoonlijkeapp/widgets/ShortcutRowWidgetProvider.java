package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class ShortcutRowWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_shortcut_row);
            WidgetStyle.applyShortcutRow(v, context);
            v.setOnClickPendingIntent(R.id.row_focus_root, WidgetLinks.open(context, "focus", 401));
            v.setOnClickPendingIntent(R.id.row_food_root, WidgetLinks.open(context, "food", 402));
            v.setOnClickPendingIntent(R.id.row_notes_root, WidgetLinks.open(context, "notes", 403));
            manager.updateAppWidget(id, v);
        }
    }
}
