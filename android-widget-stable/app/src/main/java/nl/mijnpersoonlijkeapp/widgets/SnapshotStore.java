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
import java.util.Set;

final class SnapshotStore {
    private static final String PREFS = "widget_snapshot_store";
    private static final String KEY = "today_snapshot";

    private SnapshotStore() {}

    static void updateFromWebState(Context context, String stateJson, String calendarJson) {
        if (stateJson == null || stateJson.trim().isEmpty()) return;
        try {
            JSONObject state = new JSONObject(stateJson);
            JSONObject snapshot = build(context, state, calendarJson);
            String text = snapshot.toString();
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String old = prefs.getString(KEY, "");
            if (!text.equals(old)) {
                prefs.edit().putString(KEY, text).apply();
                TodayWidgetProvider.refreshAll(context);
                TodayGridWidgetProvider.refreshAll(context);
            }
        } catch (Exception ignored) {}
    }

    static JSONObject read(Context context) {
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
            return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static String todayKey() {
        return LocalDate.now().toString();
    }

    private static JSONObject build(Context context, JSONObject state, String calendarJson) throws Exception {
        String today = todayKey();
        int energy = clamp(state.optInt("energy", 2));
        JSONObject energyHistory = state.optJSONObject("energyHistory");
        if (energyHistory != null) energy = clamp(energyHistory.optInt(today, energy));

        JSONArray rows = new JSONArray();
        addRoutines(rows, state, today);
        addHousehold(rows, state, today, energy);
        addTasks(rows, state, today, energy);
        addAgenda(rows, context, calendarJson, today);

        JSONObject snapshot = new JSONObject();
        snapshot.put("date", today);
        snapshot.put("energy", energy);
        snapshot.put("updatedAt", System.currentTimeMillis());
        snapshot.put("rows", rows);
        return snapshot;
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
            addRow(rows, "Routine", h.optString("name", "Gewoonte"));
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

        for (JSONObject item : fixed) addRow(rows, "Huis", item.optString("name", "Huishouden"));
        int openSlots = Math.max(0, energy - doneRegular);
        for (int i = 0; i < Math.min(openSlots, due.size()); i++) {
            addRow(rows, "Huis", due.get(i).optString("name", "Huishouden"));
        }
    }

    private static void addTasks(JSONArray rows, JSONObject state, String today, int energy) {
        JSONArray tasks = state.optJSONArray("tasks");
        if (tasks == null) return;
        Map<String, JSONObject> byId = new HashMap<>();
        List<JSONObject> open = new ArrayList<>();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.optJSONObject(i);
            if (t == null) continue;
            String id = t.optString("id", "");
            if (!id.isEmpty()) byId.put(id, t);
            if (taskOpen(t, today)) open.add(t);
        }

        List<JSONObject> chosen = new ArrayList<>();
        Set<String> used = new HashSet<>();
        JSONObject plan = state.optJSONObject("todayPlan");
        if (plan != null && today.equals(plan.optString("date", ""))) {
            JSONArray ids = plan.optJSONArray("taskIds");
            if (ids != null) {
                for (int i = 0; i < ids.length() && chosen.size() < energy; i++) {
                    String id = ids.optString(i, "");
                    JSONObject t = byId.get(id);
                    if (t != null && taskOpen(t, today)) {
                        chosen.add(t);
                        used.add(id);
                    }
                }
            }
        }

        if (chosen.size() < energy) {
            Collections.sort(open, new Comparator<JSONObject>() {
                @Override public int compare(JSONObject a, JSONObject b) {
                    String da = a.optString("deadline", "");
                    String db = b.optString("deadline", "");
                    if (da.isEmpty() && !db.isEmpty()) return 1;
                    if (!da.isEmpty() && db.isEmpty()) return -1;
                    if (!da.equals(db)) return da.compareTo(db);
                    return Long.compare(a.optLong("createdAt", 0), b.optLong("createdAt", 0));
                }
            });
            for (JSONObject t : open) {
                if (chosen.size() >= energy) break;
                String id = t.optString("id", "");
                if (used.contains(id)) continue;
                chosen.add(t);
                used.add(id);
            }
        }

        for (JSONObject t : chosen) addRow(rows, "Taak", t.optString("name", "Taak"));
    }

    private static void addAgenda(JSONArray rows, Context context, String calendarJson, String today) {
        JSONArray events = CalendarData.todayEvents(context);
        if (events.length() == 0) events = cachedEvents(calendarJson, today);
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || "cancelled".equals(e.optString("status", ""))) continue;
            String title = e.optString("summary", "Afspraak");
            addRow(rows, "Agenda", title, eventTime(e));
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
            if (!start.optString("date", "").isEmpty() && start.optString("dateTime", "").isEmpty()) return "Hele dag";
            String startText = start.optString("dateTime", "");
            if (startText.isEmpty()) return "";
            ZoneId zone = ZoneId.systemDefault();
            String a = OffsetDateTime.parse(startText).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"));
            String endText = end == null ? "" : end.optString("dateTime", "");
            if (endText.isEmpty()) return a;
            String b = OffsetDateTime.parse(endText).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"));
            return a + "–" + b;
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
