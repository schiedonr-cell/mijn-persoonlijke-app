package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class FoodWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_food);
            WidgetStyle.applyFood(v, context);
            v.setOnClickPendingIntent(R.id.food_root, WidgetLinks.open(context, "food", 302));
            manager.updateAppWidget(id, v);
        }
    }
}
