package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TodayGridWidgetProvider extends AppWidgetProvider {
    private static final int[] AGENDA_IDS = {R.id.grid_agenda_1, R.id.grid_agenda_2};
    private static final int[] TASK_IDS = {R.id.grid_tasks_1, R.id.grid_tasks_2, R.id.grid_tasks_3};
    private static final int[] ROUTINE_IDS = {R.id.grid_routines_1, R.id.grid_routines_2, R.id.grid_routines_3};
    private static final int[] HOUSE_IDS = {R.id.grid_household_1, R.id.grid_household_2, R.id.grid_household_3};

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, TodayGridWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today_grid);
        applyStyle(v, context, id);
        v.setTextViewText(R.id.grid_date, friendlyDate());

        v.setOnClickPendingIntent(R.id.grid_header, WidgetLinks.open(context, "today", 410));
        v.setOnClickPendingIntent(R.id.grid_agenda, WidgetLinks.open(context, "today", 411));
        v.setOnClickPendingIntent(R.id.grid_tasks, WidgetLinks.open(context, "today", 412));
        v.setOnClickPendingIntent(R.id.grid_routines, WidgetLinks.open(context, "today", 413));
        v.setOnClickPendingIntent(R.id.grid_household, WidgetLinks.open(context, "today", 414));

        JSONObject snapshot = SnapshotStore.read(context);
        JSONArray rows = snapshot.optJSONArray("rows");
        if (!SnapshotStore.todayKey().equals(snapshot.optString("date", "")) || rows == null) {
            fillEmpty(v, AGENDA_IDS, "Open Mijn dag");
            fillEmpty(v, TASK_IDS, "Open Mijn dag");
            fillEmpty(v, ROUTINE_IDS, "Open Mijn dag");
            fillEmpty(v, HOUSE_IDS, "Open Mijn dag");
            hideMore(v);
            manager.updateAppWidget(id, v);
            return;
        }

        List<JSONObject> agenda = collect(rows, "Agenda");
        List<JSONObject> tasks = collect(rows, "Taak");
        List<JSONObject> routines = collect(rows, "Routine");
        List<JSONObject> house = collect(rows, "Huis");

        fill(v, AGENDA_IDS, R.id.grid_agenda_more, agenda, 2, true, "Geen afspraken");
        fill(v, TASK_IDS, R.id.grid_tasks_more, tasks, 3, false, "Geen taken");
        fill(v, ROUTINE_IDS, R.id.grid_routines_more, routines, 3, false, "Alles gedaan");
        fill(v, HOUSE_IDS, R.id.grid_household_more, house, 3, false, "Alles gedaan");
        manager.updateAppWidget(id, v);
    }

    private static List<JSONObject> collect(JSONArray rows, String kind) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && kind.equals(row.optString("kind", ""))) out.add(row);
        }
        return out;
    }

    private static void fill(RemoteViews v, int[] ids, int moreId, List<JSONObject> items, int limit, boolean showTime, String empty) {
        int visible = Math.min(limit, items.size());
        for (int i = 0; i < ids.length; i++) {
            if (i < visible) {
                JSONObject row = items.get(i);
                String text = row.optString("text", "");
                String time = row.optString("time", "").trim();
                String line = showTime && !time.isEmpty() ? time + "  " + text : "○  " + text;
                v.setTextViewText(ids[i], line);
                v.setViewVisibility(ids[i], View.VISIBLE);
            } else if (i == 0 && items.isEmpty()) {
                v.setTextViewText(ids[i], empty);
                v.setViewVisibility(ids[i], View.VISIBLE);
            } else {
                v.setViewVisibility(ids[i], View.GONE);
            }
        }
        if (items.size() > visible) {
            v.setTextViewText(moreId, "+ " + (items.size() - visible) + " meer");
            v.setViewVisibility(moreId, View.VISIBLE);
        } else {
            v.setViewVisibility(moreId, View.GONE);
        }
    }

    private static void fillEmpty(RemoteViews v, int[] ids, String text) {
        for (int i = 0; i < ids.length; i++) {
            if (i == 0) {
                v.setTextViewText(ids[i], text);
                v.setViewVisibility(ids[i], View.VISIBLE);
            } else v.setViewVisibility(ids[i], View.GONE);
        }
    }

    private static void hideMore(RemoteViews v) {
        v.setViewVisibility(R.id.grid_agenda_more, View.GONE);
        v.setViewVisibility(R.id.grid_tasks_more, View.GONE);
        v.setViewVisibility(R.id.grid_routines_more, View.GONE);
        v.setViewVisibility(R.id.grid_household_more, View.GONE);
    }

    private static void applyStyle(RemoteViews v, Context context, int appWidgetId) {
        int base = WidgetStyle.background(context, appWidgetId);
        int transparency = WidgetStyle.transparency(context, appWidgetId);
        int opacity = Math.max(0, Math.min(100, 100 - transparency));
        String mode = WidgetStyle.textMode(context, appWidgetId);
        boolean light = WidgetStyle.TEXT_LIGHT.equals(mode) || (!WidgetStyle.TEXT_DARK.equals(mode) && Color.luminance(base) < 0.43);
        int text = light ? Color.rgb(248,249,247) : Color.rgb(31,36,32);
        int muted = light ? Color.rgb(202,208,203) : Color.rgb(83,91,84);
        int accent = WidgetStyle.accent(context, appWidgetId);
        int panelColor = Color.argb(Math.round(255f * opacity / 100f), Color.red(base), Color.green(base), Color.blue(base));
        int cardOpacity = opacity == 0 ? 0 : Math.min(100, opacity + 8);
        int cardColor = Color.argb(Math.round(255f * cardOpacity / 100f), Color.red(base), Color.green(base), Color.blue(base));

        try {
            v.setInt(R.id.grid_root, "setBackgroundResource", transparency >= 75 ? R.drawable.widget_bg_outline : R.drawable.widget_bg_clear);
            if (transparency >= 75 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                v.setColorStateList(R.id.grid_root, "setBackgroundTintList", ColorStateList.valueOf(WidgetStyle.borderColor(context, appWidgetId)));
            }
        } catch (Exception ignored) {}
        tintBackground(v, R.id.grid_panel, panelColor, transparency >= 50 ? R.drawable.widget_bg_transparent : R.drawable.widget_bg);
        for (int id : new int[]{R.id.grid_agenda,R.id.grid_tasks,R.id.grid_routines,R.id.grid_household})
            tintBackground(v, id, cardColor, transparency >= 50 ? R.drawable.button_secondary_bg_transparent : R.drawable.button_secondary_bg);

        for (int id : new int[]{R.id.grid_title,R.id.grid_agenda_title,R.id.grid_tasks_title,R.id.grid_routines_title,R.id.grid_household_title,
                R.id.grid_agenda_1,R.id.grid_agenda_2,R.id.grid_tasks_1,R.id.grid_tasks_2,R.id.grid_tasks_3,
                R.id.grid_routines_1,R.id.grid_routines_2,R.id.grid_routines_3,R.id.grid_household_1,R.id.grid_household_2,R.id.grid_household_3})
            v.setTextColor(id, text);
        for (int id : new int[]{R.id.grid_date,R.id.grid_agenda_arrow,R.id.grid_tasks_arrow,R.id.grid_routines_arrow,R.id.grid_household_arrow}) v.setTextColor(id, muted);
        for (int id : new int[]{R.id.grid_agenda_more,R.id.grid_tasks_more,R.id.grid_routines_more,R.id.grid_household_more}) v.setTextColor(id, accent);
        for (int id : new int[]{R.id.grid_agenda_icon,R.id.grid_tasks_icon,R.id.grid_routines_icon,R.id.grid_household_icon}) {
            try { v.setInt(id, "setColorFilter", accent); } catch (Exception ignored) {}
        }
    }

    private static void tintBackground(RemoteViews v, int id, int color, int drawable) {
        try {
            v.setInt(id, "setBackgroundResource", drawable);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) v.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color));
        } catch (Exception ignored) {}
    }

    private static String friendlyDate() {
        try {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", new Locale("nl","NL"))).replace(".", "");
        } catch (Exception e) { return LocalDate.now().toString(); }
    }
}
