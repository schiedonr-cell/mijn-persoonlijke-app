package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
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

        int bg = WidgetStyle.background(context, id);
        String mode = WidgetStyle.textMode(context, id);
        boolean lightText = WidgetStyle.TEXT_LIGHT.equals(mode)
                || (!WidgetStyle.TEXT_DARK.equals(mode) && Color.luminance(bg) < 0.43);
        int doneColor = lightText ? Color.rgb(202, 207, 202) : Color.rgb(103, 108, 103);

        JSONObject snapshot = SnapshotStore.read(context);
        boolean tomorrow = DayTabsWidgetProvider.isTomorrow(context);
        JSONArray rows = snapshot.optJSONArray(tomorrow ? "tomorrowRows" : "rows");
        if (!SnapshotStore.todayKey().equals(snapshot.optString("date", "")) || rows == null) {
            fill(v, new ArrayList<>(), ROW_IDS.length, "Open Mijn dag", doneColor);
            manager.updateAppWidget(id, v);
            return;
        }

        List<JSONObject> items = collect(rows, kind());
        if (!tomorrow) {
            JSONArray completed = snapshot.optJSONArray("completedRows");
            if (completed != null) items.addAll(collect(completed, kind()));
        }
        fill(v, items, ROW_IDS.length,
                tomorrow ? ("Agenda".equals(kind()) ? "Geen afspraken morgen" :
                    "Taak".equals(kind()) ? "Nog niet ingepland" : "Niets gepland voor morgen") : emptyText(), doneColor);
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

    private void fill(RemoteViews v, List<JSONObject> items, int limit, String empty, int doneColor) {
        int visible = Math.min(Math.min(limit, ROW_IDS.length), items.size());
        for (int i = 0; i < ROW_IDS.length; i++) {
            if (i < visible) {
                JSONObject row = items.get(i);
                String line = formatLine(row);
                if (row.optBoolean("done", false)) {
                    SpannableString marked = new SpannableString("✓  " + row.optString("text", ""));
                    marked.setSpan(new StrikethroughSpan(), 3, marked.length(), 0);
                    marked.setSpan(new ForegroundColorSpan(doneColor), 3, marked.length(), 0);
                    v.setTextViewText(ROW_IDS[i], marked);
                } else v.setTextViewText(ROW_IDS[i], line);
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