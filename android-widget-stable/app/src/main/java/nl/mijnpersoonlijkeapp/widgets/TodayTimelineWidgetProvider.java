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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TodayTimelineWidgetProvider extends AppWidgetProvider {
    private static final int[] ROW_BOX_IDS = new int[]{
            R.id.timeline_row_box_1,R.id.timeline_row_box_2,R.id.timeline_row_box_3,R.id.timeline_row_box_4,
            R.id.timeline_row_box_5,R.id.timeline_row_box_6,R.id.timeline_row_box_7,R.id.timeline_row_box_8,
            R.id.timeline_row_box_9,R.id.timeline_row_box_10,R.id.timeline_row_box_11,R.id.timeline_row_box_12
    };
    private static final int[] ICON_IDS = new int[]{
            R.id.timeline_icon_1,R.id.timeline_icon_2,R.id.timeline_icon_3,R.id.timeline_icon_4,
            R.id.timeline_icon_5,R.id.timeline_icon_6,R.id.timeline_icon_7,R.id.timeline_icon_8,
            R.id.timeline_icon_9,R.id.timeline_icon_10,R.id.timeline_icon_11,R.id.timeline_icon_12
    };
    private static final int[] TIME_IDS = new int[]{
            R.id.timeline_time_1,R.id.timeline_time_2,R.id.timeline_time_3,R.id.timeline_time_4,
            R.id.timeline_time_5,R.id.timeline_time_6,R.id.timeline_time_7,R.id.timeline_time_8,
            R.id.timeline_time_9,R.id.timeline_time_10,R.id.timeline_time_11,R.id.timeline_time_12
    };
    private static final int[] TEXT_IDS = new int[]{
            R.id.timeline_text_1,R.id.timeline_text_2,R.id.timeline_text_3,R.id.timeline_text_4,
            R.id.timeline_text_5,R.id.timeline_text_6,R.id.timeline_text_7,R.id.timeline_text_8,
            R.id.timeline_text_9,R.id.timeline_text_10,R.id.timeline_text_11,R.id.timeline_text_12
    };
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        updateOne(context, manager, appWidgetId);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, TodayTimelineWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today_timeline);
        WidgetStyle.applyTimeline(v, context, id);
        v.setOnClickPendingIntent(R.id.timeline_root, WidgetLinks.open(context, "today", 520));
        v.setOnClickPendingIntent(R.id.timeline_header, WidgetLinks.open(context, "today", 521));
        v.setOnClickPendingIntent(R.id.timeline_home, WidgetLinks.open(context, "home", 522));
        v.setTextViewText(R.id.timeline_date, friendlyDate());

        hideRows(v);
        JSONObject snapshot = SnapshotStore.read(context);
        JSONArray rows = snapshot.optJSONArray("rows");
        if (!SnapshotStore.todayKey().equals(snapshot.optString("date", "")) || rows == null) {
            v.setViewVisibility(R.id.timeline_empty, View.VISIBLE);
            v.setTextViewText(R.id.timeline_empty, "Open Mijn dag om het overzicht te vernieuwen.");
            v.setViewVisibility(R.id.timeline_more, View.GONE);
            manager.updateAppWidget(id, v);
            return;
        }

        List<JSONObject> items = new ArrayList<>();
        for (int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if (row==null) continue;
            // Deze widget is echt alleen voor vandaag. De gedeelde agenda-snapshot bevat
            // bewust ook komende afspraken voor de losse Agenda-widget.
            if ("Agenda".equals(row.optString("kind","")) && !agendaIsToday(row)) continue;
            items.add(row);
        }
        Collections.sort(items, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int ka=sortKey(a), kb=sortKey(b);
                if (ka!=kb) return Integer.compare(ka,kb);
                return a.optString("text","").compareToIgnoreCase(b.optString("text",""));
            }
        });

        int maxRows = rowsForHeight(manager, id);
        int visible=Math.min(Math.min(maxRows, ROW_BOX_IDS.length), items.size());
        for(int i=0;i<visible;i++) {
            JSONObject row=items.get(i);
            String kind=row.optString("kind","");
            String rawTime=row.optString("time","").trim();
            String clock=displayTime(rawTime);
            v.setImageViewResource(ICON_IDS[i], iconFor(kind));
            v.setTextViewText(TIME_IDS[i], clock.isEmpty() ? "—" : clock);
            v.setTextViewText(TEXT_IDS[i], row.optString("text",""));
            v.setViewVisibility(ROW_BOX_IDS[i], View.VISIBLE);
            v.setOnClickPendingIntent(ROW_BOX_IDS[i], WidgetLinks.open(context, targetFor(kind), 540+i));
        }

        if (items.isEmpty()) {
            v.setViewVisibility(R.id.timeline_empty, View.VISIBLE);
            v.setTextViewText(R.id.timeline_empty, "Niets meer gepland voor vandaag.");
        } else {
            v.setViewVisibility(R.id.timeline_empty, View.GONE);
        }

        if(items.size()>visible) {
            v.setTextViewText(R.id.timeline_more, "+ " + (items.size()-visible) + " meer");
            v.setViewVisibility(R.id.timeline_more, View.VISIBLE);
        } else v.setViewVisibility(R.id.timeline_more, View.GONE);

        manager.updateAppWidget(id, v);
    }

    private static boolean agendaIsToday(JSONObject row) {
        String value=row.optString("time","").trim();
        return value.startsWith("Vandaag") || value.equals("Hele dag");
    }

    private static int rowsForHeight(AppWidgetManager manager, int id) {
        try {
            Bundle o=manager.getAppWidgetOptions(id);
            int h=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
            return Math.max(3, Math.min(12, (h-64)/30));
        } catch(Exception ignored) { return 8; }
    }

    private static int sortKey(JSONObject row) {
        String time=row.optString("time","").trim();
        if (time.toLowerCase(Locale.ROOT).contains("hele dag")) return -1;
        Matcher m=CLOCK.matcher(time);
        if(m.find()) {
            try { return Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)); }
            catch(Exception ignored) {}
        }
        return 24*60+1;
    }

    private static String displayTime(String value) {
        if(value==null) return "";
        if(value.toLowerCase(Locale.ROOT).contains("hele dag")) return "Hele dag";
        Matcher m=CLOCK.matcher(value);
        if(m.find()) return String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(m.group(1)),m.group(2));
        return "";
    }

    private static int iconFor(String kind) {
        if("Agenda".equals(kind)) return R.drawable.ic_grid_agenda;
        if("Routine".equals(kind)) return R.drawable.ic_grid_routines;
        if("Huis".equals(kind)) return R.drawable.ic_grid_house;
        return R.drawable.ic_grid_tasks;
    }

    private static String targetFor(String kind) {
        if("Agenda".equals(kind)) return "today-agenda";
        if("Routine".equals(kind)) return "today-routines";
        if("Huis".equals(kind)) return "today-household";
        return "today-tasks";
    }

    private static void hideRows(RemoteViews v) {
        for(int id:ROW_BOX_IDS) v.setViewVisibility(id, View.GONE);
    }

    private static String friendlyDate() {
        try {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM", new Locale("nl","NL"))).replace(".","");
        } catch(Exception e) { return LocalDate.now().toString(); }
    }
}
