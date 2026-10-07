package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TodayWidgetProvider extends AppWidgetProvider {
    private static final int[] ROW_BOX_IDS = new int[]{
            R.id.today_row_box_1, R.id.today_row_box_2, R.id.today_row_box_3, R.id.today_row_box_4,
            R.id.today_row_box_5, R.id.today_row_box_6, R.id.today_row_box_7, R.id.today_row_box_8,
            R.id.today_row_box_9, R.id.today_row_box_10, R.id.today_row_box_11, R.id.today_row_box_12
    };
    private static final int[] LABEL_IDS = new int[]{
            R.id.today_time_1, R.id.today_time_2, R.id.today_time_3, R.id.today_time_4,
            R.id.today_time_5, R.id.today_time_6, R.id.today_time_7, R.id.today_time_8,
            R.id.today_time_9, R.id.today_time_10, R.id.today_time_11, R.id.today_time_12
    };
    private static final int[] ROW_IDS = new int[]{
            R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4,
            R.id.today_row_5, R.id.today_row_6, R.id.today_row_7, R.id.today_row_8,
            R.id.today_row_9, R.id.today_row_10, R.id.today_row_11, R.id.today_row_12
    };

    private static final String[] SUMMARY_KINDS = new String[]{"Taak", "Routine", "Huis", "Agenda"};
    private static final String[] SUMMARY_LABELS = new String[]{"Taak", "Routine", "Huis", "Agenda"};

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        updateOne(context, manager, appWidgetId);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, TodayWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateOne(context, manager, id);
        TodayGridWidgetProvider.refreshAll(context);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        WidgetStyle.applyToday(v, context, id);
        v.setOnClickPendingIntent(R.id.today_root, WidgetLinks.open(context, "today", 201));
        v.setOnClickPendingIntent(R.id.today_home, WidgetLinks.open(context, "home", 202));
        v.setTextViewText(R.id.today_subtitle, friendlyDate());
        v.setViewVisibility(R.id.today_more, View.GONE);

        JSONObject snapshot = SnapshotStore.read(context);
        String date = snapshot.optString("date", "");
        JSONArray rows = snapshot.optJSONArray("rows");

        if (!SnapshotStore.todayKey().equals(date) || rows == null) {
            hideRows(v);
            v.setViewVisibility(R.id.today_empty, View.VISIBLE);
            v.setTextViewText(R.id.today_empty, "Open Mijn dag om het overzicht te vernieuwen.");
            manager.updateAppWidget(id, v);
            return;
        }

        hideRows(v);
        int visible = 0;

        for (int k = 0; k < SUMMARY_KINDS.length && visible < 4; k++) {
            JSONObject row = firstOfKind(rows, SUMMARY_KINDS[k]);
            if (row == null) continue;

            String text = row.optString("text", "").trim();
            if (text.isEmpty()) continue;

            String line = text;
            if ("Agenda".equals(SUMMARY_KINDS[k])) {
                String time = row.optString("time", "").trim();
                if (!time.isEmpty()) line = time + "  " + text;
            }

            v.setTextViewText(LABEL_IDS[visible], SUMMARY_LABELS[k]);
            v.setViewVisibility(LABEL_IDS[visible], View.VISIBLE);
            v.setTextViewText(ROW_IDS[visible], line);
            v.setViewVisibility(ROW_BOX_IDS[visible], View.VISIBLE);
            visible++;
        }

        if (visible == 0) {
            v.setViewVisibility(R.id.today_empty, View.VISIBLE);
            v.setTextViewText(R.id.today_empty, "Alles klaar voor vandaag.");
        } else {
            v.setViewVisibility(R.id.today_empty, View.GONE);
        }

        manager.updateAppWidget(id, v);
    }

    private static JSONObject firstOfKind(JSONArray rows, String kind) {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && kind.equals(row.optString("kind", ""))) return row;
        }
        return null;
    }

    private static String friendlyDate() {
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE d MMM", new Locale("nl", "NL"));
            return LocalDate.now().format(f).replace(".", "");
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private static void hideRows(RemoteViews v) {
        for (int row : ROW_BOX_IDS) v.setViewVisibility(row, View.GONE);
    }
}
