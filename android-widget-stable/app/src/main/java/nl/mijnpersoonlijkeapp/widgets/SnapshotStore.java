package nl.mijnpersoonlijkeapp.widgets;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

final class SnapshotStore {
    private static final String PREFS = "widget_snapshot_store";
    private static final String KEY = "today_snapshot";
    private static final String PENDING = "day_timeline_pending";
    private static final String CACHED_STATE = "last_web_state";
    private static final String CACHED_CALENDAR = "last_calendar_state";
    private static final String CACHED_HOUSEHOLD = "last_household_state";

    private SnapshotStore() {}

    static synchronized void updateFromWebState(Context context, String stateJson, String calendarJson, String liveHouseholdJson) {
        if (stateJson == null || stateJson.trim().isEmpty()) return;
        try {
            JSONObject state = new JSONObject(stateJson);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            // Bewaar een bronkopie: hiermee kan afvinken op het homescreen ook zonder open WebView
            // meteen de grote en alle kleine widgets vanuit dezelfde data bijwerken.
            prefs.edit()
                    .putString(CACHED_STATE, stateJson)
                    .putString(CACHED_CALENDAR, calendarJson == null ? "" : calendarJson)
                    .putString(CACHED_HOUSEHOLD, liveHouseholdJson == null ? "" : liveHouseholdJson)
                    .commit();
            applyPendingToSnapshotState(context, state);
            JSONObject snapshot = build(context, state, calendarJson, liveHouseholdJson);
            persistAndRefresh(context, snapshot);
        } catch (Exception ignored) {}
    }

    private static void persistAndRefresh(Context context, JSONObject snapshot) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String text = snapshot.toString();
        String old = prefs.getString(KEY, "");
        if (!text.equals(old)) {
            prefs.edit().putString(KEY, text).commit();
            TodayWidgetProvider.refreshAll(context);
            TodayGridWidgetProvider.refreshAll(context);
            TodayTimelineWidgetProvider.refreshAll(context);
            refreshCategoryWidgets(context);
        }
    }

    private static void refreshCategoryWidgets(Context context) {
        android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(context);
        Class<?>[] providers = new Class<?>[]{AgendaWidgetProvider.class, TodayTasksWidgetProvider.class, RoutinesWidgetProvider.class, HouseholdWidgetProvider.class};
        for (Class<?> provider : providers) {
            android.content.ComponentName component = new android.content.ComponentName(context, provider);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids == null || ids.length == 0) continue;
            android.content.Intent intent = new android.content.Intent(context, provider);
            intent.setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(intent);
        }
    }

    static JSONObject read(Context context) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
            return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static synchronized String pending(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PENDING, "[]");
    }

    static synchronized void acknowledge(Context context, String idsJson) {
        try {
            JSONArray ids = new JSONArray(idsJson), ops = new JSONArray(pending(context)), remain = new JSONArray();
            for (int i=0;i<ops.length();i++) {
                JSONObject op=ops.optJSONObject(i);
                if(op==null)continue;
                boolean found=false;
                for(int j=0;j<ids.length();j++)if(op.optString("id").equals(ids.optString(j)))found=true;
                if(!found)remain.put(op);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PENDING,remain.toString()).commit();
        }catch(Exception ignored){}
    }

    static synchronized void toggleBlock(Context context, String date, String blockId, boolean done) {
        if(!todayKey().equals(date)||blockId==null||blockId.isEmpty())return;
        try {
            JSONObject snapshot=read(context);
            JSONArray rows=snapshot.optJSONArray("timelineRows");
            if(rows==null||!date.equals(snapshot.optString("date")))return;
            boolean found=false;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row!=null && blockId.equals(row.optString("blockId"))
                        && !"agenda".equals(row.optString("linkType"))){found=true;break;}
            }
            if(!found)return;
            JSONArray old=new JSONArray(pending(context)), next=new JSONArray();
            for(int i=0;i<old.length();i++){
                JSONObject op=old.optJSONObject(i);
                if(op!=null && !(date.equals(op.optString("date"))&&blockId.equals(op.optString("blockId"))))next.put(op);
            }
            JSONObject op=new JSONObject();
            op.put("id",java.util.UUID.randomUUID().toString());op.put("date",date);op.put("blockId",blockId);op.put("done",done);
            next.put(op);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PENDING,next.toString()).commit();
            SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String saved=prefs.getString(CACHED_STATE,"");
            if(!saved.isEmpty()){
                JSONObject state=new JSONObject(saved);
                applyPendingToSnapshotState(context,state);
                JSONObject rebuilt=build(context,state,prefs.getString(CACHED_CALENDAR,""),
                        prefs.getString(CACHED_HOUSEHOLD,""));
                // Eén gekoppelde taak kan op meerdere plekken in de planning staan.
                // Alle bijbehorende herinneringen moeten dan ook stoppen.
                JSONArray planned=rebuilt.optJSONArray("timelineRows");
                if(planned!=null)for(int i=0;i<planned.length();i++){
                    JSONObject row=planned.optJSONObject(i);
                    if(row!=null && row.optBoolean("done",false))
                        NativeAlarmScheduler.cancel(context,"dayplan-"+date+"-"+row.optString("blockId"));
                }
                persistAndRefresh(context,rebuilt);
            }else{
                // Alleen bij een nog niet gesynchroniseerde installatie.
                // De volgende WebView-sync bouwt alle categorieën opnieuw op.
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.optJSONObject(i);
                    if(row!=null && blockId.equals(row.optString("blockId")))row.put("done",done);
                }
                if(done)NativeAlarmScheduler.cancel(context,"dayplan-"+date+"-"+blockId);
                persistAndRefresh(context,snapshot);
            }
        }catch(Exception ignored){}
    }

    private static void applyPendingToSnapshotState(Context context, JSONObject state) {
        try{
            JSONObject plan=state.optJSONObject("dayTimeline");
            if(plan==null)return;
            String date=plan.optString("date"),today=todayKey();
            if(!today.equals(date))return;
            JSONArray blocks=plan.optJSONArray("blocks"),ops=new JSONArray(pending(context));
            if(blocks==null)return;
            for(int i=0;i<ops.length();i++){
                JSONObject op=ops.optJSONObject(i);
                if(op==null||!date.equals(op.optString("date")))continue;
                for(int j=0;j<blocks.length();j++){
                    JSONObject b=blocks.optJSONObject(j);
                    if(b==null||!op.optString("blockId").equals(b.optString("id")))continue;
                    boolean done=op.optBoolean("done");
                    String type=b.optString("linkType"),id=b.optString("linkId");
                    if("task".equals(type)){
                        JSONObject t=findById(state.optJSONArray("tasks"),id);
                        if(t!=null){
                            // Elk tijdblok blijft afzonderlijk afvinkbaar.
                            // De oorspronkelijke taak is pas klaar als alle gekoppelde
                            // tijdblokken zijn afgerond.
                            if(t.optBoolean("done",false)){
                                for(int k=0;k<blocks.length();k++){
                                    JSONObject sibling=blocks.optJSONObject(k);
                                    if(sibling!=null && "task".equals(sibling.optString("linkType"))
                                            && id.equals(sibling.optString("linkId")))sibling.put("done",true);
                                }
                            }
                            b.put("done",done);
                            boolean allDone=true;
                            for(int k=0;k<blocks.length();k++){
                                JSONObject sibling=blocks.optJSONObject(k);
                                if(sibling!=null && "task".equals(sibling.optString("linkType"))
                                        && id.equals(sibling.optString("linkId"))
                                        && !sibling.optBoolean("done",false)){allDone=false;break;}
                            }
                            t.put("done",allDone);
                            t.put("completedAt",allDone?System.currentTimeMillis():JSONObject.NULL);
                        }else b.put("done",done);
                    }else if("habit".equals(type) && isMinuteRoutine(state.optJSONArray("habits"),id)){
                        // Bij ontspannen en stretchen is een tijdblok afzonderlijk
                        // afvinkbaar. De WebView telt de minuten later bij het dagdoel.
                        b.put("done",done);
                    }else if("habit".equals(type)||"household".equals(type)){
                        JSONObject t=findById(state.optJSONArray("habit".equals(type)?"habits":"householdTasks"),id);
                        if(t!=null){
                            JSONObject history=t.optJSONObject("history");
                            if(history==null){history=new JSONObject();t.put("history",history);}
                            if(done)history.put(today,true);else history.remove(today);
                        }
                    }else if(!"agenda".equals(type)){b.put("done",done);}
                }
            }
        }catch(Exception ignored){}
    }

    static String todayKey() {
        return LocalDate.now().toString();
    }

    private static JSONObject build(Context context, JSONObject state, String calendarJson, String liveHouseholdJson) throws Exception {
        String today = todayKey();
        int energy = clamp(state.optInt("energy", 2));
        JSONObject energyHistory = state.optJSONObject("energyHistory");
        if (energyHistory != null) energy = clamp(energyHistory.optInt(today, energy));

        JSONArray rows = new JSONArray();
        addRoutines(rows, state, today);
        if (!addLiveHousehold(rows, liveHouseholdJson, state, today)) addHousehold(rows, state, today, energy);
        addTasks(rows, state, today, energy);
        addAgenda(rows, context, calendarJson, today);

        JSONObject snapshot = new JSONObject();
        snapshot.put("date", today);
        snapshot.put("energy", energy);
        snapshot.put("updatedAt", System.currentTimeMillis());
        snapshot.put("rows", rows);

        JSONObject plan = state.optJSONObject("dayTimeline");
        JSONArray blocks = plan == null ? null : plan.optJSONArray("blocks");
        if (plan != null && today.equals(plan.optString("date", "")) && blocks != null && blocks.length() > 0) {
            snapshot.put("hasDayTimeline", true);
            snapshot.put("timelineTotal", blocks.length());
            snapshot.put("timelineRows", buildDayTimelineRows(state, today));
        }

        return snapshot;
    }

    private static JSONArray buildDayTimelineRows(JSONObject state, String today) {
        JSONArray out = new JSONArray();
        JSONObject plan = state.optJSONObject("dayTimeline");
        if (plan == null || !today.equals(plan.optString("date", ""))) return out;
        JSONArray blocks = plan.optJSONArray("blocks");
        if (blocks == null) return out;

        JSONArray tasks = state.optJSONArray("tasks");
        JSONArray habits = state.optJSONArray("habits");
        JSONArray household = state.optJSONArray("householdTasks");

        for (int i = 0; i < blocks.length(); i++) {
            JSONObject b = blocks.optJSONObject(i);
            if (b == null) continue;
            String text = b.optString("text", "").trim();
            String start = b.optString("start", "").trim();
            String end = b.optString("end", "").trim();
            String linkType = b.optString("linkType", "");
            String linkId = b.optString("linkId", "");
            if (text.isEmpty() || start.isEmpty()) continue;

            boolean done = b.optBoolean("done", false);
            String kind = "Plan";
            if ("task".equals(linkType)) {
                kind = "Taak";
                JSONObject item = findById(tasks, linkId);
                if (item != null) done = done || item.optBoolean("done", false);
            } else if ("habit".equals(linkType)) {
                kind = "Routine";
                JSONObject item = findById(habits, linkId);
                if (!isMinuteRoutine(habits, linkId)) {
                    JSONObject history = item == null ? null : item.optJSONObject("history");
                    if (history != null) done = truthy(history, today);
                }
            } else if ("household".equals(linkType)) {
                kind = "Huis";
                JSONObject item = findById(household, linkId);
                JSONObject history = item == null ? null : item.optJSONObject("history");
                if (history != null) done = truthy(history, today);
            } else if ("agenda".equals(linkType)) {
                kind = "Agenda";
            }

            // Afgeronde onderdelen blijven in de app bewaard, maar niet op het homescreen.
            // Agenda-afspraken zijn informatief en kunnen niet afgevinkt worden.
            if (done) continue;
            JSONObject row = new JSONObject();
            try {
                row.put("kind", kind);
                row.put("text", text);
                row.put("time", end.isEmpty() ? start : start + "–" + end);
                row.put("done", done);
                row.put("blockId", b.optString("id", ""));
                row.put("linkType", linkType);
                out.put(row);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static boolean isMinuteRoutine(JSONArray habits, String id) {
        if ("basis-relax".equals(id) || "basis-stretch".equals(id)) return true;
        JSONObject h = findById(habits, id);
        if (h == null) return false;
        String name = h.optString("name", "").trim().toLowerCase(Locale.forLanguageTag("nl-NL"));
        return "stretchen".equals(name) || "stretchen / mobiliteit".equals(name);
    }

    private static JSONObject findById(JSONArray array, String id) {
        if (array == null || id == null || id.isEmpty()) return null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && id.equals(item.optString("id", ""))) return item;
        }
        return null;
    }

    private static void addRow(JSONArray rows, String kind, String text) {
        addRow(rows, kind, text, "");
    }

    private static void addRow(JSONArray rows, String kind, String text, String time) {
        if (text == null || text.trim().isEmpty()) return;
        JSONObject row = new JSONObject();
        try {
            row.put("kind", kind);
            row.put("text", text.trim());
            row.put("time", time == null ? "" : time.trim());
            rows.put(row);
        } catch (Exception ignored) {}
    }

    private static boolean addLiveHousehold(JSONArray rows, String liveHouseholdJson, JSONObject state, String today) {
        if (liveHouseholdJson == null || liveHouseholdJson.trim().isEmpty()) return false;
        try {
            JSONArray items = new JSONArray(liveHouseholdJson);
            JSONArray allHousehold=state.optJSONArray("householdTasks");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String name=item.optString("name","Huishouden");
                boolean completed=false;
                if(allHousehold!=null)for(int j=0;j<allHousehold.length();j++){
                    JSONObject house=allHousehold.optJSONObject(j);
                    if(house!=null && name.equalsIgnoreCase(house.optString("name",""))
                            && doneToday(house,today)){completed=true;break;}
                }
                if(!completed)addRow(rows, "Huis", name, item.optString("time", ""));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void addRoutines(JSONArray rows, JSONObject state, String today) {
        JSONArray habits = state.optJSONArray("habits");
        if (habits == null) return;
        for (int i = 0; i < habits.length(); i++) {
            JSONObject h = habits.optJSONObject(i);
            if (h == null) continue;
            String startedOn = h.optString("startedOn", "");
            if (!startedOn.isEmpty() && startedOn.compareTo(today) > 0) continue;
            JSONObject history = h.optJSONObject("history");
            if (history != null && truthy(history, today)) continue;
            addRow(rows, "Routine", h.optString("name", "Gewoonte"), h.optString("time", ""));
        }
    }

    private static void addHousehold(JSONArray rows, JSONObject state, String today, int energy) {
        JSONArray source = state.optJSONArray("householdTasks");
        if (source == null) return;
        List<JSONObject> fixed = new ArrayList<>();
        List<JSONObject> due = new ArrayList<>();
        int doneRegular = 0;

        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            boolean done = doneToday(item, today);
            boolean isFixed = item.optBoolean("fixedReminder", false);
            if (!isFixed && done) doneRegular++;
            if (done) continue;
            if (!isDue(item, today)) continue;
            if (isFixed) fixed.add(item);
            else if (clampEffort(item.optInt("effort", 1)) <= energy) due.add(item);
        }

        Collections.sort(due, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int pa = priority(a, today), pb = priority(b, today);
                if (pa != pb) return Integer.compare(pb, pa);
                return Integer.compare(clampEffort(a.optInt("effort", 1)), clampEffort(b.optInt("effort", 1)));
            }
        });

        for (JSONObject item : fixed) addRow(rows, "Huis", item.optString("name", "Huishouden"), item.optString("time", ""));
        int openSlots = Math.max(0, energy - doneRegular);
        for (int i = 0; i < Math.min(openSlots, due.size()); i++) {
            addRow(rows, "Huis", due.get(i).optString("name", "Huishouden"), due.get(i).optString("time", ""));
        }
    }

    private static void addTasks(JSONArray rows, JSONObject state, String today, int energy) {
        JSONArray tasks = state.optJSONArray("tasks");
        if (tasks == null) return;
        Map<String, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;
            String id = task.optString("id", "");
            if (!id.isEmpty()) byId.put(id, task);
        }

        // Zoals de kleine Agenda-widget: alleen wat bewust voor VANDAAG
        // is geselecteerd of ingepland. Niet vanuit de algemene backlog aanvullen.
        List<JSONObject> chosen = new ArrayList<>();
        Set<String> used = new HashSet<>();
        JSONObject todayPlan = state.optJSONObject("todayPlan");
        if (todayPlan != null && today.equals(todayPlan.optString("date", ""))) {
            JSONArray ids = todayPlan.optJSONArray("taskIds");
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i, "");
                    JSONObject task = byId.get(id);
                    if (task == null || !taskOpen(task, today) || task.optBoolean("paused", false)) continue;
                    if (used.add(id)) chosen.add(task);
                }
            }
        }

        // Een extra taak die expliciet in de dagtijdlijn staat, hoort ook bij vandaag
        // zelfs als hij niet in de energie-afhankelijke 1/2/3-takenlijst staat.
        JSONObject dayPlan = state.optJSONObject("dayTimeline");
        if (dayPlan != null && today.equals(dayPlan.optString("date", ""))) {
            JSONArray blocks = dayPlan.optJSONArray("blocks");
            if (blocks != null) {
                for (int i = 0; i < blocks.length(); i++) {
                    JSONObject block = blocks.optJSONObject(i);
                    if (block == null || !"task".equals(block.optString("linkType", ""))
                            || !block.optString("postponedTo", "").isEmpty()
                            || block.optBoolean("done", false)) continue;
                    String id = block.optString("linkId", "");
                    JSONObject task = byId.get(id);
                    if (task == null || !taskOpen(task, today) || task.optBoolean("paused", false)) continue;
                    if (used.add(id)) chosen.add(task);
                }
            }
        }

        for (JSONObject task : chosen) {
            addRow(rows, "Taak", task.optString("name", "Taak"), task.optString("time", ""));
        }
    }

    private static void addAgenda(JSONArray rows, Context context, String calendarJson, String today) {
        JSONArray events = CalendarData.todayEvents(context);
        if (events.length() == 0) events = cachedEvents(calendarJson, today);
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || "cancelled".equals(e.optString("status", ""))) continue;
            // De kleine widgets zijn een overzicht voor VANDAAG.
            // De app zelf blijft komende afspraken via CalendarData.todayEvents() tonen.
            if (!isEventOnDate(e, today)) continue;
            String title = e.optString("summary", "Afspraak");
            addRow(rows, "Agenda", title, eventTime(e));
        }
    }

    private static boolean isEventOnDate(JSONObject event, String date) {
        try {
            JSONObject start = event.optJSONObject("start");
            if (start == null) return false;
            String day = start.optString("date", "").trim();
            if (!day.isEmpty()) return date.equals(day);

            String value = start.optString("dateTime", "").trim();
            if (value.isEmpty()) return false;
            LocalDate local = OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            return date.equals(local.toString());
        } catch (Exception ignored) {
            // Onbekende of ongeldige datum nooit als een afspraak van vandaag tonen.
            return false;
        }
    }

    private static JSONArray cachedEvents(String calendarJson, String today) {
        try {
            JSONObject cache = new JSONObject(calendarJson == null ? "{}" : calendarJson);
            if (!today.equals(cache.optString("date", ""))) return new JSONArray();
            JSONArray items = cache.optJSONArray("items");
            return items == null ? new JSONArray() : items;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static String eventTime(JSONObject event) {
        try {
            JSONObject start = event.optJSONObject("start");
            JSONObject end = event.optJSONObject("end");
            if (start == null) return "";

            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            LocalDate eventDay;
            String time = "";

            String dateOnly = start.optString("date", "");
            String startText = start.optString("dateTime", "");
            if (!dateOnly.isEmpty() && startText.isEmpty()) {
                eventDay = LocalDate.parse(dateOnly);
                time = "Hele dag";
            } else {
                if (startText.isEmpty()) return "";
                OffsetDateTime parsedStart = OffsetDateTime.parse(startText);
                eventDay = parsedStart.atZoneSameInstant(zone).toLocalDate();
                String a = parsedStart.atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"));
                String endText = end == null ? "" : end.optString("dateTime", "");
                if (endText.isEmpty()) {
                    time = a;
                } else {
                    String b = OffsetDateTime.parse(endText).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"));
                    time = a + "–" + b;
                }
            }

            long days = ChronoUnit.DAYS.between(today, eventDay);
            String dayLabel;
            if (days == 0) dayLabel = "Vandaag";
            else if (days == 1) dayLabel = "Morgen";
            else if (days == 2) dayLabel = "Overmorgen";
            else dayLabel = eventDay.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("nl-NL")));

            return time.isEmpty() ? dayLabel : dayLabel + " · " + time;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean taskOpen(JSONObject t, String today) {
        if (t.optBoolean("done", false) || t.optBoolean("paused", false)) return false;
        String defer = t.optString("deferUntil", "");
        return defer.isEmpty() || defer.compareTo(today) <= 0;
    }

    private static boolean doneToday(JSONObject item, String today) {
        JSONObject history = item.optJSONObject("history");
        return history != null && truthy(history, today);
    }

    private static boolean isDue(JSONObject item, String today) {
        if (item.optBoolean("paused", false) || doneToday(item, today)) return false;
        String defer = item.optString("deferUntil", "");
        if (!defer.isEmpty() && defer.compareTo(today) > 0) return false;
        String last = lastDone(item);
        if (last.isEmpty()) return true;
        int frequency = Math.max(1, item.optInt("frequency", 1));
        return dayDiff(last, today) >= frequency;
    }

    private static int priority(JSONObject item, String today) {
        String last = lastDone(item);
        if (last.isEmpty()) return 999;
        return (int) dayDiff(last, today) - Math.max(1, item.optInt("frequency", 1));
    }

    private static String lastDone(JSONObject item) {
        JSONObject history = item.optJSONObject("history");
        if (history == null) return "";
        String best = "";
        Iterator<String> keys = history.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!truthy(history, key)) continue;
            if (best.isEmpty() || key.compareTo(best) > 0) best = key;
        }
        return best;
    }

    private static boolean truthy(JSONObject object, String key) {
        try {
            if (object == null || !object.has(key) || object.isNull(key)) return false;
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) return false;
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof Number) return ((Number) value).doubleValue() != 0d;
            if (value instanceof String) return !((String) value).isEmpty();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static long dayDiff(String from, String to) {
        try {
            return ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to));
        } catch (Exception e) {
            return 0;
        }
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(3, value));
    }

    private static int clampEffort(int value) {
        return Math.max(1, Math.min(3, value));
    }
}
