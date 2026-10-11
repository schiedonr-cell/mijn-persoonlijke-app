package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

abstract class CategoryWidgetProvider extends AppWidgetProvider {
    private static final int[] ROW_IDS = {
            R.id.category_row_1, R.id.category_row_2, R.id.category_row_3,
            R.id.category_row_4, R.id.category_row_5, R.id.category_row_6,
            R.id.category_row_7, R.id.category_row_8, R.id.category_row_9,
            R.id.category_row_10, R.id.category_row_11, R.id.category_row_12
    };

    abstract String kind();
    abstract String title();
    abstract String emptyText();
    abstract String target();
    abstract int iconRes();
    abstract int requestCode();
    boolean showTime() { return false; }

    String formatLine(JSONObject row) {
        String text = row.optString("text", "");
        String time = row.optString("time", "").trim();
        return showTime() && !time.isEmpty() ? time + "  " + text : "○  " + text;
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        updateOne(context, manager, appWidgetId);
    }

    void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_category);
        v.setTextViewText(R.id.category_title, title());
        v.setImageViewResource(R.id.category_icon, iconRes());
        WidgetStyle.applyCategory(v, context, id);
        v.setOnClickPendingIntent(R.id.category_root, WidgetLinks.open(context, target(), requestCode()));

        boolean tomorrow = DayTabsWidgetProvider.isTomorrow(context);
        int base = WidgetStyle.background(context, id);
        String mode = WidgetStyle.textMode(context, id);
        boolean lightText = WidgetStyle.TEXT_LIGHT.equals(mode)
                || (!WidgetStyle.TEXT_DARK.equals(mode) && Color.luminance(base) < 0.43);
        int selected = lightText ? Color.rgb(248, 249, 247) : Color.rgb(31, 36, 32);
        int unselected = lightText ? Color.rgb(172, 179, 173) : Color.rgb(112, 121, 113);
        v.setTextColor(R.id.category_tab_today, tomorrow ? unselected : selected);
        v.setTextColor(R.id.category_tab_tomorrow, tomorrow ? selected : unselected);
        v.setOnClickPendingIntent(R.id.category_tab_today, DayTabsWidgetProvider.click(context, false));
        v.setOnClickPendingIntent(R.id.category_tab_tomorrow, DayTabsWidgetProvider.click(context, true));

        JSONObject snapshot = SnapshotStore.read(context);
        JSONArray rows = snapshot.optJSONArray(tomorrow ? "tomorrowRows" : "rows");
        if (!SnapshotStore.todayKey().equals(snapshot.optString("date", "")) || rows == null) {
            fill(v, new ArrayList<>(), ROW_IDS.length, "Open Mijn dag");
            manager.updateAppWidget(id, v);
            return;
        }

        List<JSONObject> items = collect(rows, kind());
        fill(v, items, ROW_IDS.length,
                tomorrow ? ("Agenda".equals(kind()) ? "Geen afspraken morgen" :
                    "Taak".equals(kind()) ? "Nog niet ingepland" : "Niets gepland voor morgen") : emptyText());
        manager.updateAppWidget(id, v);
    }

    private List<JSONObject> collect(JSONArray rows, String kind) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && kind.equals(row.optString("kind", ""))) out.add(row);
        }
        return out;
    }

    private void fill(RemoteViews v, List<JSONObject> items, int limit, String empty) {
        int visible = Math.min(Math.min(limit, ROW_IDS.length), items.size());
        for (int i = 0; i < ROW_IDS.length; i++) {
            if (i < visible) {
                JSONObject row = items.get(i);
                String line = formatLine(row);
                v.setTextViewText(ROW_IDS[i], line);
                v.setViewVisibility(ROW_IDS[i], View.VISIBLE);
            } else if (i == 0 && items.isEmpty() && limit > 0) {
                v.setTextViewText(ROW_IDS[i], empty);
                v.setViewVisibility(ROW_IDS[i], View.VISIBLE);
            } else {
                v.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }
        if (items.size() > visible) {
            v.setTextViewText(R.id.category_more, "+ " + (items.size() - visible) + " meer");
            v.setViewVisibility(R.id.category_more, View.VISIBLE);
        } else {
            v.setViewVisibility(R.id.category_more, View.GONE);
        }
    }

}