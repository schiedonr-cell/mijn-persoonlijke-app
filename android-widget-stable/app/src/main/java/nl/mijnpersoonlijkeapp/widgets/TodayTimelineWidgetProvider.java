package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.PendingIntent;
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
        R.id.timeline_row_box_9,R.id.timeline_row_box_10,R.id.timeline_row_box_11,R.id.timeline_row_box_12,
        R.id.timeline_row_box_13,R.id.timeline_row_box_14,R.id.timeline_row_box_15,R.id.timeline_row_box_16,
        R.id.timeline_row_box_17,R.id.timeline_row_box_18,R.id.timeline_row_box_19,R.id.timeline_row_box_20,
        R.id.timeline_row_box_21,R.id.timeline_row_box_22,R.id.timeline_row_box_23,R.id.timeline_row_box_24
    };
    private static final int[] CHECK_IDS = new int[]{
        R.id.timeline_check_1,
        R.id.timeline_check_2,
        R.id.timeline_check_3,
        R.id.timeline_check_4,
        R.id.timeline_check_5,
        R.id.timeline_check_6,
        R.id.timeline_check_7,
        R.id.timeline_check_8,
        R.id.timeline_check_9,
        R.id.timeline_check_10,
        R.id.timeline_check_11,
        R.id.timeline_check_12,
        R.id.timeline_check_13,
        R.id.timeline_check_14,
        R.id.timeline_check_15,
        R.id.timeline_check_16,
        R.id.timeline_check_17,
        R.id.timeline_check_18,
        R.id.timeline_check_19,
        R.id.timeline_check_20,
        R.id.timeline_check_21,
        R.id.timeline_check_22,
        R.id.timeline_check_23,
        R.id.timeline_check_24
    };
    private static final int[] ICON_IDS = new int[]{
        R.id.timeline_icon_1,R.id.timeline_icon_2,R.id.timeline_icon_3,R.id.timeline_icon_4,
        R.id.timeline_icon_5,R.id.timeline_icon_6,R.id.timeline_icon_7,R.id.timeline_icon_8,
        R.id.timeline_icon_9,R.id.timeline_icon_10,R.id.timeline_icon_11,R.id.timeline_icon_12,
        R.id.timeline_icon_13,R.id.timeline_icon_14,R.id.timeline_icon_15,R.id.timeline_icon_16,
        R.id.timeline_icon_17,R.id.timeline_icon_18,R.id.timeline_icon_19,R.id.timeline_icon_20,
        R.id.timeline_icon_21,R.id.timeline_icon_22,R.id.timeline_icon_23,R.id.timeline_icon_24
    };
    private static final int[] TIME_IDS = new int[]{
        R.id.timeline_time_1,R.id.timeline_time_2,R.id.timeline_time_3,R.id.timeline_time_4,
        R.id.timeline_time_5,R.id.timeline_time_6,R.id.timeline_time_7,R.id.timeline_time_8,
        R.id.timeline_time_9,R.id.timeline_time_10,R.id.timeline_time_11,R.id.timeline_time_12,
        R.id.timeline_time_13,R.id.timeline_time_14,R.id.timeline_time_15,R.id.timeline_time_16,
        R.id.timeline_time_17,R.id.timeline_time_18,R.id.timeline_time_19,R.id.timeline_time_20,
        R.id.timeline_time_21,R.id.timeline_time_22,R.id.timeline_time_23,R.id.timeline_time_24
    };
    private static final int[] TEXT_IDS = new int[]{
        R.id.timeline_text_1,R.id.timeline_text_2,R.id.timeline_text_3,R.id.timeline_text_4,
        R.id.timeline_text_5,R.id.timeline_text_6,R.id.timeline_text_7,R.id.timeline_text_8,
        R.id.timeline_text_9,R.id.timeline_text_10,R.id.timeline_text_11,R.id.timeline_text_12,
        R.id.timeline_text_13,R.id.timeline_text_14,R.id.timeline_text_15,R.id.timeline_text_16,
        R.id.timeline_text_17,R.id.timeline_text_18,R.id.timeline_text_19,R.id.timeline_text_20,
        R.id.timeline_text_21,R.id.timeline_text_22,R.id.timeline_text_23,R.id.timeline_text_24
    };
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final String ACTION_TOGGLE = "nl.mijnpersoonlijkeapp.widgets.TIMELINE_TOGGLE";
    private static final String ACTION_NEXT_PAGE = "nl.mijnpersoonlijkeapp.widgets.TIMELINE_NEXT_PAGE";
    private static final String PAGE_PREFS = "timeline_widget_pages";

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            String date=intent.getStringExtra("date"),blockId=intent.getStringExtra("blockId");
            if(date!=null&&blockId!=null){
                JSONObject snap=SnapshotStore.read(context);
                JSONArray rows=snap.optJSONArray("timelineRows");
                if(rows!=null)for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.optJSONObject(i);
                    if(row!=null&&blockId.equals(row.optString("blockId"))){
                        SnapshotStore.toggleBlock(context,date,blockId,!row.optBoolean("done",false));
                        break;
                    }
                }
            }
            return;
        }
        if (intent != null && ACTION_NEXT_PAGE.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                SharedPreferences p = context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE);
                p.edit().putInt("page-" + id, p.getInt("page-" + id, 0) + 1).apply();
                updateOne(context, AppWidgetManager.getInstance(context), id);
            }
        }
    }


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
        JSONArray plannedRows = snapshot.optJSONArray("timelineRows");
        boolean usingDayPlan = snapshot.optBoolean("hasDayTimeline", false) && plannedRows != null;
        JSONArray rows = usingDayPlan ? plannedRows : snapshot.optJSONArray("rows");
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
            if (!usingDayPlan && "Agenda".equals(row.optString("kind","")) && !agendaIsToday(row)) continue;
            items.add(row);
        }
        if (!usingDayPlan) Collections.sort(items, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                boolean ta=hasClock(a), tb=hasClock(b);
                if (ta && !tb) return -1;
                if (!ta && tb) return 1;
                if (ta) {
                    int ka=sortKey(a), kb=sortKey(b);
                    if (ka!=kb) return Integer.compare(ka,kb);
                    return categoryRank(a.optString("kind","")) - categoryRank(b.optString("kind",""));
                }
                int ca=categoryRank(a.optString("kind","")), cb=categoryRank(b.optString("kind",""));
                if (ca!=cb) return Integer.compare(ca,cb);
                return a.optString("text","").compareToIgnoreCase(b.optString("text",""));
            }
        });

        int maxRows = Math.min(rowsForHeight(manager, id), ROW_BOX_IDS.length);
        int pages = Math.max(1, (items.size() + maxRows - 1) / maxRows);
        SharedPreferences pref = context.getSharedPreferences(PAGE_PREFS, Context.MODE_PRIVATE);
        String token = snapshot.optString("date", "") + "|" + usingDayPlan + "|" + items.size();
        int requestedPage = token.equals(pref.getString("token-" + id, "")) ? pref.getInt("page-" + id, 0) : 0;
        int page = Math.floorMod(requestedPage, pages);
        pref.edit().putString("token-" + id, token).putInt("page-" + id, page).apply();
        int start = page * maxRows;
        int visible = Math.min(maxRows, items.size() - start);
        for(int i=0;i<visible;i++) {
            JSONObject row=items.get(start+i);
            String kind=row.optString("kind","");
            String rawTime=row.optString("time","").trim();
            String clock=displayTime(rawTime);
            v.setImageViewResource(ICON_IDS[i], iconFor(kind));
            v.setTextViewText(TIME_IDS[i], clock.isEmpty() ? "—" : clock);
            boolean completed=row.optBoolean("done",false);
            v.setTextViewText(TEXT_IDS[i], row.optString("text",""));
            if(usingDayPlan && !"agenda".equals(row.optString("linkType")) && !row.optString("blockId").isEmpty()){
                v.setViewVisibility(CHECK_IDS[i], View.VISIBLE);
                v.setTextViewText(CHECK_IDS[i], completed ? "☑" : "□");
                Intent toggle=new Intent(context,TodayTimelineWidgetProvider.class);
                toggle.setAction(ACTION_TOGGLE);
                toggle.setData(android.net.Uri.parse("mijndag://check/"+android.net.Uri.encode(row.optString("blockId"))));
                toggle.putExtra("date",snapshot.optString("date"));
                toggle.putExtra("blockId",row.optString("blockId"));
                PendingIntent pi=PendingIntent.getBroadcast(context, 3000+i+id*100, toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                v.setOnClickPendingIntent(CHECK_IDS[i],pi);
            }else v.setViewVisibility(CHECK_IDS[i],usingDayPlan ? View.INVISIBLE : View.GONE);
            v.setViewVisibility(ROW_BOX_IDS[i], View.VISIBLE);
            v.setOnClickPendingIntent(ROW_BOX_IDS[i], WidgetLinks.open(context, targetFor(kind), 540+i));
        }

        if (items.isEmpty()) {
            v.setViewVisibility(R.id.timeline_empty, View.VISIBLE);
            v.setTextViewText(R.id.timeline_empty, usingDayPlan ? "Geen blokken in de dagplanning." : "Niets meer gepland voor vandaag.");
        } else {
            v.setViewVisibility(R.id.timeline_empty, View.GONE);
        }

        if (pages > 1) {
            v.setTextViewText(R.id.timeline_more, "Pagina " + (page + 1) + "/" + pages + " · tik voor volgende ›");
            v.setViewVisibility(R.id.timeline_more, View.VISIBLE);
            Intent next = new Intent(context, TodayTimelineWidgetProvider.class);
            next.setAction(ACTION_NEXT_PAGE);
            next.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            PendingIntent pending = PendingIntent.getBroadcast(context, id + 2400, next,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.timeline_more, pending);
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
            return Math.max(3, Math.min(24, (h-76)/28));
        } catch(Exception ignored) { return 8; }
    }

    private static boolean hasClock(JSONObject row) {
        String time=row.optString("time","").trim();
        if (time.toLowerCase(Locale.ROOT).contains("hele dag")) return true;
        return CLOCK.matcher(time).find();
    }

    private static int categoryRank(String kind) {
        if ("Routine".equals(kind)) return 0;
        if ("Taak".equals(kind)) return 1;
        if ("Huis".equals(kind)) return 2;
        if ("Agenda".equals(kind)) return 3;
        return 4;
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
        String v=value.trim();
        if(v.toLowerCase(Locale.ROOT).contains("hele dag")) return "Hele dag";
        if(v.matches("^\\d{1,2}:\\d{2}[–—-]\\d{1,2}:\\d{2}$")) return v;
        Matcher m=CLOCK.matcher(v);
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
        if("Plan".equals(kind)) return "today";
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
