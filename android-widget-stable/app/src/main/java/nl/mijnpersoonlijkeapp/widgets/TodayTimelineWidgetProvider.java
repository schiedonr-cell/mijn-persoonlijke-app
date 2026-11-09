package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.PendingIntent;
import android.os.Bundle;
import android.graphics.Color;
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
    private static final int[] UP_IDS = {
        R.id.timeline_up_1,
        R.id.timeline_up_2,
        R.id.timeline_up_3,
        R.id.timeline_up_4,
        R.id.timeline_up_5,
        R.id.timeline_up_6,
        R.id.timeline_up_7,
        R.id.timeline_up_8,
        R.id.timeline_up_9,
        R.id.timeline_up_10,
        R.id.timeline_up_11,
        R.id.timeline_up_12,
        R.id.timeline_up_13,
        R.id.timeline_up_14,
        R.id.timeline_up_15,
        R.id.timeline_up_16,
        R.id.timeline_up_17,
        R.id.timeline_up_18,
        R.id.timeline_up_19,
        R.id.timeline_up_20,
        R.id.timeline_up_21,
        R.id.timeline_up_22,
        R.id.timeline_up_23,
        R.id.timeline_up_24
    };
    private static final int[] DOWN_IDS = {
        R.id.timeline_down_1,
        R.id.timeline_down_2,
        R.id.timeline_down_3,
        R.id.timeline_down_4,
        R.id.timeline_down_5,
        R.id.timeline_down_6,
        R.id.timeline_down_7,
        R.id.timeline_down_8,
        R.id.timeline_down_9,
        R.id.timeline_down_10,
        R.id.timeline_down_11,
        R.id.timeline_down_12,
        R.id.timeline_down_13,
        R.id.timeline_down_14,
        R.id.timeline_down_15,
        R.id.timeline_down_16,
        R.id.timeline_down_17,
        R.id.timeline_down_18,
        R.id.timeline_down_19,
        R.id.timeline_down_20,
        R.id.timeline_down_21,
        R.id.timeline_down_22,
        R.id.timeline_down_23,
        R.id.timeline_down_24
    };
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final String ACTION_TOGGLE = "nl.mijnpersoonlijkeapp.widgets.TIMELINE_TOGGLE";
    private static final String ACTION_QUICK = "nl.mijnpersoonlijkeapp.widgets.MIXED_TOGGLE";
    private static final String ACTION_AGENDA_TOGGLE = "nl.mijnpersoonlijkeapp.widgets.AGENDA_LOCAL_TOGGLE";
    private static final String AGENDA_PREFS = "timeline_agenda_local_checks";
    private static final String ACTION_NEXT_PAGE = "nl.mijnpersoonlijkeapp.widgets.TIMELINE_NEXT_PAGE";
    private static final String PAGE_PREFS = "timeline_widget_pages";
    private static final String ORDER_PREFS = "timeline_widget_order_mode";
    private static final String ACTION_ORDER_MODE = "nl.mijnpersoonlijkeapp.widgets.ORDER_MODE";
    private static final String ACTION_ORDER_SHIFT = "nl.mijnpersoonlijkeapp.widgets.ORDER_SHIFT";
    private static final String ACTION_NOOP = "nl.mijnpersoonlijkeapp.widgets.ORDER_NOOP";

    private static boolean reorderMode(Context context, int widgetId, boolean tomorrow) {
        if (tomorrow) return false;
        SharedPreferences p = context.getSharedPreferences(ORDER_PREFS,Context.MODE_PRIVATE);
        return SnapshotStore.todayKey().equals(p.getString("date-"+widgetId,""))
                && p.getBoolean("editing-"+widgetId,false);
    }

    private static PendingIntent reorderAction(Context context, int widgetId) {
        Intent intent=new Intent(context,TodayTimelineWidgetProvider.class);
        intent.setAction(ACTION_ORDER_MODE);
        intent.setData(android.net.Uri.parse("mijndag://ordermode/"+widgetId));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
        return PendingIntent.getBroadcast(context,0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent moveAction(Context context, int widgetId, boolean timed,
                                             String key, int direction) {
        Intent intent=new Intent(context,TodayTimelineWidgetProvider.class);
        intent.setAction(ACTION_ORDER_SHIFT);
        intent.setData(android.net.Uri.parse("mijndag://ordershift/"+widgetId+"/"
                +(timed?"timed":"untimed")+"/"+android.net.Uri.encode(key)+"/"+direction));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
        intent.putExtra("date",SnapshotStore.todayKey());
        intent.putExtra("timed",timed);
        intent.putExtra("key",key);
        intent.putExtra("direction",direction);
        return PendingIntent.getBroadcast(context,0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent noOp(Context context, int widgetId) {
        Intent intent=new Intent(context,TodayTimelineWidgetProvider.class);
        intent.setAction(ACTION_NOOP);
        intent.setData(android.net.Uri.parse("mijndag://ordernoop/"+widgetId));
        return PendingIntent.getBroadcast(context,0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean movable(JSONObject row, boolean timed) {
        if(row==null||"Agenda".equals(row.optString("kind","")))return false;
        if(timed)return !row.optBoolean("fixed",false)
                && !row.optString("blockId","").isEmpty()
                && !row.optBoolean("done",false);
        String type=row.optString("itemType","");
        return ("task".equals(type)||"habit".equals(type)
                ||"household".equals(type)||"lunch".equals(type))
                && !row.optString("itemId","").isEmpty();
    }

    private static String moveKey(JSONObject row, boolean timed) {
        return timed?row.optString("blockId","")
                :row.optString("itemType","")+":"+row.optString("itemId","");
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if(intent!=null&&(ACTION_ORDER_MODE.equals(intent.getAction())
                ||ACTION_ORDER_SHIFT.equals(intent.getAction()))){
            int widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            AppWidgetManager manager=AppWidgetManager.getInstance(context);
            android.appwidget.AppWidgetProviderInfo info=manager.getAppWidgetInfo(widgetId);
            if(info==null||info.provider==null
                    ||!TodayTimelineWidgetProvider.class.getName().equals(info.provider.getClassName())
                    ||DayTabsWidgetProvider.isTomorrow(context,widgetId))return;
            SharedPreferences prefs=context.getSharedPreferences(ORDER_PREFS,Context.MODE_PRIVATE);
            if(ACTION_ORDER_MODE.equals(intent.getAction())){
                boolean editing=reorderMode(context,widgetId,false);
                prefs.edit().putString("date-"+widgetId,SnapshotStore.todayKey())
                        .putBoolean("editing-"+widgetId,!editing).apply();
                updateOne(context,manager,widgetId);
            }else if(reorderMode(context,widgetId,false)){
                String date=intent.getStringExtra("date");
                String key=intent.getStringExtra("key");
                int direction=intent.getIntExtra("direction",0);
                boolean timed=intent.getBooleanExtra("timed",false);
                boolean moved=SnapshotStore.moveWidgetRow(context,date,timed,key,direction);
                if(!moved)android.widget.Toast.makeText(context,
                        "Verplaatsen lukt hier niet door vaste tijden of een lopende focus.",
                        android.widget.Toast.LENGTH_SHORT).show();
                updateOne(context,manager,widgetId);
            }
            return;
        }
        if(intent!=null&&ACTION_NOOP.equals(intent.getAction()))return;
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
        if(intent!=null&&ACTION_AGENDA_TOGGLE.equals(intent.getAction())){
            String date=intent.getStringExtra("date"),key=intent.getStringExtra("agendaKey");
            if(date!=null&&key!=null&&date.equals(SnapshotStore.todayKey())){
                JSONObject snapshot=SnapshotStore.read(context);
                JSONArray planned=snapshot.optJSONArray("timelineRows");
                JSONArray mixed=snapshot.optJSONArray("mixedRows");
                // Geen wijziging aan de Android-/Google-agenda of de dagplanning.
                if(date.equals(snapshot.optString("date")) &&
                        (agendaKeyPresent(planned,key)||agendaKeyPresent(mixed,key))){
                    SharedPreferences pref=context.getSharedPreferences(AGENDA_PREFS,Context.MODE_PRIVATE);
                    java.util.Set<String> updated=new java.util.HashSet<>(
                            date.equals(pref.getString("date",""))
                                    ?pref.getStringSet("checked",java.util.Collections.emptySet())
                                    :java.util.Collections.emptySet());
                    if(!updated.add(key))updated.remove(key);
                    pref.edit().putString("date",date).putStringSet("checked",updated).commit();
                    refreshAll(context);
                }
            }
            return;
        }
        if(intent!=null&&ACTION_QUICK.equals(intent.getAction())){
            String date=intent.getStringExtra("date"),type=intent.getStringExtra("itemType"),itemId=intent.getStringExtra("itemId");
            if(date!=null&&type!=null&&itemId!=null){
                // Meteen afvinken zonder de app te openen: SnapshotStore werkt
                // de widget bij en geeft het vinkje later door aan de WebView.
                SnapshotStore.toggleQuick(context,date,type,itemId);
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
        boolean tomorrow = DayTabsWidgetProvider.isTomorrow(context, id);
        v.setViewVisibility(R.id.timeline_reorder,tomorrow?View.GONE:View.VISIBLE);
        boolean editing=reorderMode(context,id,tomorrow);
        v.setTextViewText(R.id.timeline_reorder,editing?"✓":"↕");
        v.setOnClickPendingIntent(R.id.timeline_reorder,reorderAction(context,id));
        v.setContentDescription(R.id.timeline_reorder,
                editing?"Volgorde klaar":"Volgorde aanpassen");
        int bg = WidgetStyle.background(context, id);
        String textMode = WidgetStyle.textMode(context, id);
        boolean light = WidgetStyle.TEXT_LIGHT.equals(textMode)
                || (!WidgetStyle.TEXT_DARK.equals(textMode) && Color.luminance(bg) < 0.43);
        int active = light ? Color.rgb(248,249,247) : Color.rgb(31,36,32);
        int inactive = light ? Color.rgb(174,180,174) : Color.rgb(110,120,111);
        v.setTextColor(R.id.timeline_tab_today, tomorrow ? inactive : active);
        v.setTextColor(R.id.timeline_tab_tomorrow, tomorrow ? active : inactive);
        v.setOnClickPendingIntent(R.id.timeline_tab_today, DayTabsWidgetProvider.click(context, id, false));
        v.setOnClickPendingIntent(R.id.timeline_tab_tomorrow, DayTabsWidgetProvider.click(context, id, true));
        v.setTextViewText(R.id.timeline_date, friendlyDate(tomorrow));

        hideRows(v);
        JSONObject snapshot = SnapshotStore.read(context);
        JSONArray plannedRows = snapshot.optJSONArray(tomorrow?"tomorrowTimelineRows":"timelineRows");
        boolean usingDayPlan = snapshot.optBoolean(tomorrow?"hasTomorrowDayTimeline":"hasDayTimeline", false)
                && plannedRows != null;
        JSONArray rows = usingDayPlan ? plannedRows
                : (tomorrow ? snapshot.optJSONArray("tomorrowRows") : snapshot.optJSONArray("mixedRows"));
        if(!tomorrow && !usingDayPlan && rows==null)rows=snapshot.optJSONArray("rows");
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
            if (!tomorrow && !usingDayPlan && "Agenda".equals(row.optString("kind","")) && !agendaIsToday(row)) continue;
            items.add(row);
        }
        if (false && !usingDayPlan) Collections.sort(items, new Comparator<JSONObject>() {
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
        // Houd de huidige pagina aan wanneer een afgevinkte regel verdwijnt.
        String token = snapshot.optString("date", "") + "|" + tomorrow + "|" + usingDayPlan + "|"
                + (usingDayPlan ? snapshot.optInt(tomorrow?"tomorrowTimelineTotal":"timelineTotal", rows.length()) : items.size());
        int requestedPage = token.equals(pref.getString("token-" + id, "")) ? pref.getInt("page-" + id, 0) : 0;
        int page = Math.floorMod(requestedPage, pages);
        pref.edit().putString("token-" + id, token).putInt("page-" + id, page).apply();
        int start = page * maxRows;
        int visible = Math.min(maxRows, items.size() - start);
        List<String> moveKeys=new ArrayList<>();
        if(!tomorrow)for(JSONObject row:items)if(movable(row,usingDayPlan)){
            String key=moveKey(row,usingDayPlan);
            if(!moveKeys.contains(key))moveKeys.add(key);
        }
        SharedPreferences agendaPrefs=context.getSharedPreferences(AGENDA_PREFS,Context.MODE_PRIVATE);
        java.util.Set<String> checkedAgenda=snapshot.optString("date").equals(agendaPrefs.getString("date",""))
                ?agendaPrefs.getStringSet("checked",java.util.Collections.emptySet())
                :java.util.Collections.emptySet();
        for(int i=0;i<visible;i++) {
            JSONObject row=items.get(start+i);
            String orderKey=moveKey(row,usingDayPlan);
            int orderAt=moveKeys.indexOf(orderKey);
            if(editing&&movable(row,usingDayPlan)&&orderAt>=0){
                v.setViewVisibility(UP_IDS[i],orderAt>0?View.VISIBLE:View.INVISIBLE);
                v.setViewVisibility(DOWN_IDS[i],orderAt+1<moveKeys.size()?View.VISIBLE:View.INVISIBLE);
                // De pijlen gebruiken precies dezelfde gekozen widget-accentkleur.
                int accent=WidgetStyle.accent(context,id);
                v.setTextColor(UP_IDS[i],accent);
                v.setTextColor(DOWN_IDS[i],accent);
                v.setOnClickPendingIntent(UP_IDS[i],moveAction(context,id,usingDayPlan,orderKey,-1));
                v.setOnClickPendingIntent(DOWN_IDS[i],moveAction(context,id,usingDayPlan,orderKey,1));
            }else{
                v.setViewVisibility(UP_IDS[i],View.GONE);
                v.setViewVisibility(DOWN_IDS[i],View.GONE);
            }
            String kind=row.optString("kind","");
            String rawTime=row.optString("time","").trim();
            String clock=displayTime(rawTime);
            v.setImageViewResource(ICON_IDS[i], iconFor(kind));
            // Zonder kloktijden is de tijdkolom overbodig. Verberg hem volledig:
            // zo staat de activiteit direct naast het pictogram en verdwijnt het streepje.
            v.setViewVisibility(TIME_IDS[i], usingDayPlan ? View.VISIBLE : View.GONE);
            v.setTextViewText(TIME_IDS[i], usingDayPlan ? (clock.isEmpty() ? "—" : clock) : "");
            boolean completed=row.optBoolean("done",false);
            v.setTextViewText(TEXT_IDS[i], row.optString("text",""));
            // De hele regel is een groot aanraakvlak. De kop blijft de
            // aparte snelkoppeling om de app te openen.
            PendingIntent rowClick=WidgetLinks.open(context, targetFor(kind), 540+i);
            boolean laundry="household".equals(row.optString("itemType",""))
                    && row.optString("text","").trim().toLowerCase(Locale.ROOT).startsWith("was doen");
            if(!tomorrow && "Agenda".equals(kind)){
                String agendaKey=agendaCheckKey(row);
                v.setViewVisibility(CHECK_IDS[i],View.VISIBLE);
                v.setTextViewText(CHECK_IDS[i],checkedAgenda.contains(agendaKey)?"☑":"□");
                Intent toggle=new Intent(context,TodayTimelineWidgetProvider.class);
                toggle.setAction(ACTION_AGENDA_TOGGLE);
                toggle.setData(android.net.Uri.parse("mijndag://agenda-check/"
                        +android.net.Uri.encode(snapshot.optString("date")+"|"+agendaKey)));
                toggle.putExtra("date",snapshot.optString("date"));
                toggle.putExtra("agendaKey",agendaKey);
                PendingIntent pi=PendingIntent.getBroadcast(context,7000+i+id*100,toggle,
                        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                v.setOnClickPendingIntent(CHECK_IDS[i],pi);
                rowClick=pi;
            }else if(!tomorrow && usingDayPlan && !"agenda".equals(row.optString("linkType")) && !row.optString("blockId").isEmpty()){
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
                rowClick=pi;
            }else if(!tomorrow&&!usingDayPlan&&!row.optString("itemType","").isEmpty()&&!row.optString("itemId","").isEmpty()){
                if(laundry){
                    // Een was heeft drie eigen stappen: de widget mag niet
                    // met één tik de complete was als afgerond opslaan.
                    v.setViewVisibility(CHECK_IDS[i],View.GONE);
                    rowClick=WidgetLinks.open(context,"today-household", 540+i);
                }else{
                    v.setViewVisibility(CHECK_IDS[i],View.VISIBLE);
                    v.setTextViewText(CHECK_IDS[i],"□");
                    Intent quick=new Intent(context,TodayTimelineWidgetProvider.class);
                    quick.setAction(ACTION_QUICK);
                    quick.setData(android.net.Uri.parse("mijndag://mixed/"+android.net.Uri.encode(row.optString("itemType"))+"/"+android.net.Uri.encode(row.optString("itemId"))));
                    quick.putExtra("date",snapshot.optString("date"));
                    quick.putExtra("itemType",row.optString("itemType"));
                    quick.putExtra("itemId",row.optString("itemId"));
                    PendingIntent pi=PendingIntent.getBroadcast(context,5000+i+id*100,quick,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    v.setOnClickPendingIntent(CHECK_IDS[i],pi);
                    rowClick=pi;
                }
            }else v.setViewVisibility(CHECK_IDS[i],usingDayPlan?View.INVISIBLE:View.GONE);
            // De complete regel werkt al als afvinkknop; een apart vakje
            // neemt alleen ruimte in en maakt de tekst op smalle widgets korter.
            v.setViewVisibility(CHECK_IDS[i], View.GONE);
            v.setViewVisibility(ROW_BOX_IDS[i], View.VISIBLE);
            v.setOnClickPendingIntent(ROW_BOX_IDS[i], editing?noOp(context,id):rowClick);
        }

        if (items.isEmpty()) {
            v.setViewVisibility(R.id.timeline_empty, View.VISIBLE);
            v.setTextViewText(R.id.timeline_empty, tomorrow ? "Nog niets gepland voor morgen." :
                    (usingDayPlan ? "Alles afgerond voor vandaag." : "Niets meer gepland voor vandaag."));
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

    private static String agendaCheckKey(JSONObject row) {
        String name=row.optString("text","").trim().toLowerCase(Locale.ROOT);
        String time=row.optString("time","").trim();
        int label=time.indexOf(" · ");
        if(label>=0)time=time.substring(label+3).trim();
        return name+"|"+time;
    }

    private static boolean agendaKeyPresent(JSONArray rows,String key) {
        if(rows==null)return false;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row!=null&&"Agenda".equals(row.optString("kind",""))
                    &&key.equals(agendaCheckKey(row)))return true;
        }
        return false;
    }

    private static boolean agendaIsToday(JSONObject row) {
        String value=row.optString("time","").trim();
        return value.startsWith("Vandaag") || value.equals("Hele dag");
    }

    private static int rowsForHeight(AppWidgetManager manager, int id) {
        try {
            Bundle o=manager.getAppWidgetOptions(id);
            int h=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300);
            // Kop (42dp), Vandaag/Morgen (30dp), scheidingslijn (11dp),
            // paneelranden (28dp) en eventuele paginaknop (21dp)
            // nemen samen 132dp in. Laat het teveel aan taken op de
            // volgende pagina zien, in plaats van rijen samen te drukken
            // of de laatste regel buiten beeld te laten vallen.
            final int rowHeightDp = 38;
            final int chromeHeightDp = 132;
            return Math.max(2, Math.min(24, (h - chromeHeightDp) / rowHeightDp));
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
        for(int i=0;i<ROW_BOX_IDS.length;i++){
            v.setViewVisibility(ROW_BOX_IDS[i],View.GONE);
            v.setViewVisibility(UP_IDS[i],View.GONE);
            v.setViewVisibility(DOWN_IDS[i],View.GONE);
        }
    }

    private static String friendlyDate(boolean tomorrow) {
        try {
            return LocalDate.now().plusDays(tomorrow ? 1 : 0).format(DateTimeFormatter.ofPattern("EEE d MMM", new Locale("nl","NL"))).replace(".","");
        } catch(Exception e) { return LocalDate.now().plusDays(tomorrow ? 1 : 0).toString(); }
    }
}
