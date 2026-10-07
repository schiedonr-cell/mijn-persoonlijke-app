package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

abstract class SimpleShortcutWidgetProvider extends AppWidgetProvider {
    abstract String title();
    abstract String icon();
    int iconRes() { return 0; }
    abstract String target();
    abstract int requestCode();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_shortcut_single);
            v.setTextViewText(R.id.shortcut_title, title());
            if (iconRes() != 0) {
                v.setViewVisibility(R.id.shortcut_icon, View.GONE);
                v.setImageViewResource(R.id.shortcut_icon_image, iconRes());
                v.setViewVisibility(R.id.shortcut_icon_image, View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.shortcut_icon_image, View.GONE);
                v.setTextViewText(R.id.shortcut_icon, icon());
                v.setViewVisibility(R.id.shortcut_icon, View.VISIBLE);
            }
            WidgetStyle.applySimpleShortcut(v, context, id);
            v.setOnClickPendingIntent(R.id.shortcut_root, WidgetLinks.open(context, target(), requestCode()));
            manager.updateAppWidget(id, v);
        }
    }
}