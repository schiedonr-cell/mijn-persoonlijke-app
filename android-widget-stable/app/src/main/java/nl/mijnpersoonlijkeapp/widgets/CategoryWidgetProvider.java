package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

abstract class CategoryWidgetProvider extends AppWidgetProvider {
    private static final int[] ROW_IDS = {
            R.id.category_row_1, R.id.category_row_2, R.id.category_row_3,
            R.id.category_row_4, R.id.category_row_5, R.id.category_row_6
    };

    abstract String kind();
    abstract String title();
    abstract String emptyText();
    abstract String target();
    abstract int iconRes();
    abstract int requestCode();
    boolean showTime() { return false; }

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
        WidgetStyle.applyCategory(v, context);
        v.setOnClickPendingIntent(R.id.category_root, WidgetLinks.open(context, target(), requestCode()));

        JSONObject snapshot = SnapshotStore.read(context);
        JSONArray rows = snapshot.optJSONArray("rows");
        if (!SnapshotStore.todayKey().equals(snapshot.optString("date", "")) || rows == null) {
            fill(v, new ArrayList<>(), visibleRowsForHeight(manager, id), "Open Mijn dag");
            manager.updateAppWidget(id, v);
            return;
        }

        List<JSONObject> items = collect(rows, kind());
        fill(v, items, visibleRowsForHeight(manager, id), emptyText());
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
                String text = row.optString("text", "");
                String time = row.optString("time", "").trim();
                String line = showTime() && !time.isEmpty() ? time + "  " + text : "○  " + text;
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

    private int visibleRowsForHeight(AppWidgetManager manager, int id) {
        try {
            int height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            if (height < 80) return 0;
            if (height < 115) return 1;
            if (height < 150) return 2;
            if (height < 185) return 3;
            if (height < 220) return 4;
            if (height < 255) return 5;
        } catch (Exception ignored) {}
        return 6;
    }
}