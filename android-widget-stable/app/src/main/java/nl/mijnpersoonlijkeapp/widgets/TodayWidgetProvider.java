package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TodayWidgetProvider extends AppWidgetProvider {
    private static final int[] ROW_IDS = new int[]{
            R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4,
            R.id.today_row_5, R.id.today_row_6, R.id.today_row_7
    };

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, TodayWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        v.setOnClickPendingIntent(R.id.today_root, WidgetLinks.open(context, "today", 201));

        JSONObject snapshot = SnapshotStore.read(context);
        String date = snapshot.optString("date", "");
        JSONArray rows = snapshot.optJSONArray("rows");
        int energy = snapshot.optInt("energy", 2);

        v.setTextViewText(R.id.today_energy, "Energie " + energy);
        v.setTextViewText(R.id.today_subtitle, friendlyDate());

        if (!SnapshotStore.todayKey().equals(date) || rows == null) {
            v.setViewVisibility(R.id.today_empty, View.VISIBLE);
            v.setTextViewText(R.id.today_empty, "Open Mijn dag om het overzicht te vernieuwen.");
            v.setViewVisibility(R.id.today_more, View.GONE);
            hideRows(v);
            manager.updateAppWidget(id, v);
            return;
        }

        int count = rows.length();
        int visible = Math.min(count, ROW_IDS.length);
        for (int i = 0; i < ROW_IDS.length; i++) {
            if (i < visible) {
                JSONObject row = rows.optJSONObject(i);
                String kind = row == null ? "" : row.optString("kind", "");
                String text = row == null ? "" : row.optString("text", "");
                v.setTextViewText(ROW_IDS[i], prefix(kind) + text);
                v.setViewVisibility(ROW_IDS[i], View.VISIBLE);
            } else {
                v.setViewVisibility(ROW_IDS[i], View.GONE);
            }
        }

        if (count == 0) {
            v.setViewVisibility(R.id.today_empty, View.VISIBLE);
            v.setTextViewText(R.id.today_empty, "Alles klaar voor vandaag.");
        } else {
            v.setViewVisibility(R.id.today_empty, View.GONE);
        }

        if (count > ROW_IDS.length) {
            v.setViewVisibility(R.id.today_more, View.VISIBLE);
            v.setTextViewText(R.id.today_more, "+ " + (count - ROW_IDS.length) + " meer open");
        } else {
            v.setViewVisibility(R.id.today_more, View.GONE);
        }
        manager.updateAppWidget(id, v);
    }

    private static String prefix(String kind) {
        if ("Agenda".equals(kind)) return "▣  ";
        if ("Routine".equals(kind)) return "○  ";
        if ("Huis".equals(kind)) return "⌂  ";
        if ("Taak".equals(kind)) return "☆  ";
        return "•  ";
    }

    private static String friendlyDate() {
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("nl", "NL"));
            String value = LocalDate.now().format(f);
            return value.substring(0, 1).toUpperCase(new Locale("nl", "NL")) + value.substring(1);
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private static void hideRows(RemoteViews v) {
        for (int row : ROW_IDS) v.setViewVisibility(row, View.GONE);
    }
}
