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
    private static final int[] TIME_IDS = new int[]{
            R.id.today_time_1, R.id.today_time_2, R.id.today_time_3, R.id.today_time_4,
            R.id.today_time_5, R.id.today_time_6, R.id.today_time_7, R.id.today_time_8,
            R.id.today_time_9, R.id.today_time_10, R.id.today_time_11, R.id.today_time_12
    };
    private static final int[] ROW_IDS = new int[]{
            R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4,
            R.id.today_row_5, R.id.today_row_6, R.id.today_row_7, R.id.today_row_8,
            R.id.today_row_9, R.id.today_row_10, R.id.today_row_11, R.id.today_row_12
    };

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
        WidgetStyle.applyToday(v, context);
        v.setOnClickPendingIntent(R.id.today_root, WidgetLinks.open(context, "today", 201));
        v.setTextViewText(R.id.today_subtitle, friendlyDate());

        JSONObject snapshot = SnapshotStore.read(context);
        String date = snapshot.optString("date", "");
        JSONArray rows = snapshot.optJSONArray("rows");

        if (!SnapshotStore.todayKey().equals(date) || rows == null) {
            v.setViewVisibility(R.id.today_empty, View.VISIBLE);
            v.setTextViewText(R.id.today_empty, "Open Mijn dag om het overzicht te vernieuwen.");
            v.setViewVisibility(R.id.today_more, View.GONE);
            hideRows(v);
            manager.updateAppWidget(id, v);
            return;
        }

        int count = rows.length();
        int limit = Math.min(ROW_IDS.length, visibleRowsForHeight(manager, id));
        int visible = Math.min(count, limit);

        for (int i = 0; i < ROW_IDS.length; i++) {
            if (i < visible) {
                JSONObject row = rows.optJSONObject(i);
                String kind = row == null ? "" : row.optString("kind", "");
                String text = row == null ? "" : row.optString("text", "");
                String time = row == null ? "" : row.optString("time", "");
                String cleanTime = time == null ? "" : time.trim();

                v.setTextViewText(TIME_IDS[i], cleanTime);
                v.setViewVisibility(TIME_IDS[i], cleanTime.isEmpty() ? View.GONE : View.VISIBLE);
                v.setTextViewText(ROW_IDS[i], pictogram(kind, text) + "  " + text);
                v.setViewVisibility(ROW_BOX_IDS[i], View.VISIBLE);
            } else {
                v.setViewVisibility(ROW_BOX_IDS[i], View.GONE);
            }
        }

        if (count == 0) {
            v.setViewVisibility(R.id.today_empty, View.VISIBLE);
            v.setTextViewText(R.id.today_empty, "Alles klaar voor vandaag.");
        } else {
            v.setViewVisibility(R.id.today_empty, View.GONE);
        }

        if (count > visible) {
            v.setViewVisibility(R.id.today_more, View.VISIBLE);
            v.setTextViewText(R.id.today_more, "+ " + (count - visible) + " meer");
        } else {
            v.setViewVisibility(R.id.today_more, View.GONE);
        }
        manager.updateAppWidget(id, v);
    }

    private static int visibleRowsForHeight(AppWidgetManager manager, int id) {
        try {
            Bundle options = manager.getAppWidgetOptions(id);
            int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 240);
            if (height < 175) return 3;
            if (height < 225) return 4;
            if (height < 275) return 5;
            if (height < 325) return 6;
            if (height < 375) return 7;
            if (height < 425) return 8;
            if (height < 475) return 9;
            if (height < 525) return 10;
            if (height < 575) return 11;
        } catch (Exception ignored) {}
        return 12;
    }

    private static String pictogram(String kind, String text) {
        String s = text == null ? "" : text.toLowerCase(new Locale("nl", "NL"));
        if (s.contains("ochtend")) return "☀️";
        if (s.contains("wandelen") || s.contains("wandeling") || s.contains("lopen")) return "🚶";
        if (s.contains("lunch") || s.contains("eten")) return "🍴";
        if (s.contains("boodschap")) return "🛒";
        if (s.contains("ontspan") || s.contains("rustmoment") || s.contains("rust")) return "🍃";
        if (s.contains("avond") || s.contains("dag afsluiten")) return "🌙";
        if (s.contains("medic")) return "💊";
        if (s.contains("mail") || s.contains("e-mail")) return "✉️";
        if (s.contains("robotstofzuiger") || s.contains("robot")) return "🤖";
        if (s.contains("stofzuig") || s.contains("vloer")) return "🧹";
        if (s.contains("was ") || s.contains("wasgoed") || s.contains("was opruimen")) return "🧺";
        if (s.contains("toilet") || s.contains("badkamer") || s.contains("schoonmaak")) return "🧽";
        if (s.contains("keuken")) return "🏠";
        if (s.contains("financi") || s.contains("rekening") || s.contains("bank")) return "💳";
        if ("Agenda".equals(kind)) return "📅";
        if ("Routine".equals(kind)) return "🔄";
        if ("Huis".equals(kind)) return "🏠";
        if ("Taak".equals(kind)) return "⭐";
        return "•";
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
