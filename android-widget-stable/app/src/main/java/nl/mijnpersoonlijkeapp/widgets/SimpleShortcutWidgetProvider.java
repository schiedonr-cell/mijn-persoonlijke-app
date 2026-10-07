package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

abstract class SimpleShortcutWidgetProvider extends AppWidgetProvider {
    abstract String title();
    abstract String icon();
    abstract String target();
    abstract int requestCode();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_shortcut_single);
            v.setTextViewText(R.id.shortcut_title, title());
            v.setTextViewText(R.id.shortcut_icon, icon());
            WidgetStyle.applySimpleShortcut(v, context);
            v.setOnClickPendingIntent(R.id.shortcut_root, WidgetLinks.open(context, target(), requestCode()));
            manager.updateAppWidget(id, v);
        }
    }
}