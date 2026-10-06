package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class NotesWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_notes);
            WidgetStyle.applyNotes(v, context);
            v.setOnClickPendingIntent(R.id.notes_root, WidgetLinks.open(context, "notes", 303));
            manager.updateAppWidget(id, v);
        }
    }
}
