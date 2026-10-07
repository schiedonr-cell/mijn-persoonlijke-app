package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

abstract class BaseWidgetProvider extends AppWidgetProvider {
    abstract int layoutId();

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), layoutId());
            WidgetStyle.applyQuick(v, context, id);
            bind(v, context);
            manager.updateAppWidget(id, v);
        }
    }

    void bind(RemoteViews v, Context c) {
        bindIfPresent(v, c, R.id.btn_today, "today", 101);
        bindIfPresent(v, c, R.id.btn_food, "food", 102);
        bindIfPresent(v, c, R.id.btn_projects, "projects", 103);
        bindIfPresent(v, c, R.id.btn_notes, "notes", 104);
        bindIfPresent(v, c, R.id.btn_focus, "focus", 105);
    }

    private void bindIfPresent(RemoteViews v, Context c, int viewId, String target, int request) {
        try { v.setOnClickPendingIntent(viewId, WidgetLinks.open(c, target, request)); } catch (Exception ignored) {}
    }
}
