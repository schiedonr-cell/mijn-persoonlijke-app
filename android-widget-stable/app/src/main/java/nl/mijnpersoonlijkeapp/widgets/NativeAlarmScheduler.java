package nl.mijnpersoonlijkeapp.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Iterator;
import java.util.ArrayList;

final class NativeAlarmScheduler {
    private static final String PREFS = "native_alarm_store";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_CANCELLED = "cancelled_bases";
    private static final String KEY_CLEANUP_V63 = "snooze_cleanup_v63";

    private NativeAlarmScheduler() {}

    static void schedule(Context context, String id, String title, String body, long triggerAt, String target) {
        scheduleInternal(context, id, title, body, Math.max(System.currentTimeMillis() + 1000L, triggerAt), target, false, -1, -1, 0);
    }

    static void scheduleDaily(Context context, String id, String title, String body, int hour, int minute, String target) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        c.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis() + 1000L) c.add(Calendar.DAY_OF_YEAR, 1);
        scheduleInternal(context, id, title, body, c.getTimeInMillis(), target, true, hour, minute, 1);
    }

    static void scheduleEveryDays(Context context, String id, String title, String body, long firstAt, int everyDays, String target) {
        int days = Math.max(1, Math.min(365, everyDays));
        long at = firstAt;
        long now = System.currentTimeMillis();
        long step = days * 86400000L;
        // Never turn a missed repeating household time into an immediate alarm.
        // Move it to the next valid repeat occurrence instead.
        while (at <= now + 1000L) at += step;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        scheduleInternal(context, id, title, body, at, target, false,
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), days);
    }

    private static void scheduleInternal(Context context, String id, String title, String body, long at, String target,
                                         boolean repeatDaily, int hour, int minute, int repeatDays) {
        if (id == null || id.trim().isEmpty()) return;
        // Een opnieuw ingestelde begintijd maakt eerdere snoozes van dat alarm ongeldig.
        if (!id.contains("-snooze-")) {
            JSONObject previous = load(context).optJSONObject(id);
            if (previous != null && (previous.optLong("at", 0) != at ||
                    !previous.optString("title", "").equals(title))) {
                cancelSnoozes(context, id);
            }
            clearCancellationMark(context, id);
        }
        save(context, id, title, body, at, target, repeatDaily, hour, minute, repeatDays);

        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;

        PendingIntent pi = pendingIntent(context, id, title, body, target);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException ex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
    }

    static synchronized void cancel(Context context, String id) {
        if (id == null || id.trim().isEmpty()) return;
        // A snoozed occurrence has its own ID; canceling the original must also
        // cancel every child alarm, including snoozes from earlier re-snoozes.
        if (!id.contains("-snooze-")) {
            markCancelled(context, id);
            cancelSnoozes(context, id);
        }
        cancelOne(context, id);
    }

    static synchronized void cancelSnoozes(Context context, String originalId) {
        if (originalId == null || originalId.trim().isEmpty()) return;
        String base = baseId(originalId);
        String prefix = base + "-snooze-";
        ArrayList<String> children = new ArrayList<>();
        Iterator<String> keys = load(context).keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(prefix)) children.add(key);
        }
        for (String id : children) cancelOne(context, id);
        ReminderReceiver.clearSuppressionForAlarm(context, base);
    }

    // Eenmalig bij installatie van deze versie: de oude snooze-kopieën
    // opruimen zonder de gewone, zelfstandig ingestelde reminders te verwijderen.
    static synchronized void clearLegacySnoozesOnce(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean(KEY_CLEANUP_V63, false)) return;
        ArrayList<String> old = new ArrayList<>();
        Iterator<String> keys = load(context).keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.contains("-snooze-")) old.add(key);
        }
        for (String id : old) {
            cancelOne(context, id);
            ReminderReceiver.clearSuppressionForAlarm(context, baseId(id));
        }
        preferences.edit().putBoolean(KEY_CLEANUP_V63, true).apply();
    }

    static synchronized boolean isScheduled(Context context, String id) {
        return id != null && load(context).has(id);
    }

    static synchronized boolean wasExplicitlyCancelled(Context context, String id) {
        if (id == null) return false;
        try {
            long when = new JSONObject(prefs(context).getString(KEY_CANCELLED, "{}"))
                    .optLong(baseId(id), 0L);
            return when > System.currentTimeMillis() - 86400000L;
        } catch (Exception ignored) { return false; }
    }

    private static String baseId(String id) {
        int i = id.indexOf("-snooze-");
        return i >= 0 ? id.substring(0, i) : id;
    }

    private static void cancelOne(Context context, String id) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            try { alarm.cancel(pendingIntent(context, id, "", "", "today")); } catch (Exception ignored) {}
        }
        remove(context, id);
        ReminderReceiver.dismissCancelledAlarm(context, id);
    }

    private static void markCancelled(Context context, String base) {
        try {
            JSONObject recent = new JSONObject(prefs(context).getString(KEY_CANCELLED, "{}"));
            ArrayList<String> old = new ArrayList<>();
            Iterator<String> keys = recent.keys();
            long cutoff = System.currentTimeMillis() - 86400000L;
            while (keys.hasNext()) {
                String id = keys.next();
                if (recent.optLong(id, 0L) < cutoff) old.add(id);
            }
            for (String id : old) recent.remove(id);
            recent.put(base, System.currentTimeMillis());
            prefs(context).edit().putString(KEY_CANCELLED, recent.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void clearCancellationMark(Context context, String base) {
        try {
            JSONObject recent = new JSONObject(prefs(context).getString(KEY_CANCELLED, "{}"));
            if (recent.has(base)) {
                recent.remove(base);
                prefs(context).edit().putString(KEY_CANCELLED, recent.toString()).apply();
            }
        } catch (Exception ignored) {}
    }

    static void rescheduleAll(Context context) {
        JSONObject items = load(context);
        long now = System.currentTimeMillis();
        Iterator<String> keys = items.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONObject item = items.optJSONObject(id);
            if (item == null) continue;
            boolean daily = item.optBoolean("repeatDaily", false);
            int repeatDays = item.optInt("repeatDays", 0);
            if (daily) {
                scheduleDaily(context, id, item.optString("title", "Herinnering"),
                        item.optString("body", ""), item.optInt("hour", 9), item.optInt("minute", 0),
                        item.optString("target", "today"));
            } else if (repeatDays > 0) {
                long at = item.optLong("at", 0L);
                while (at <= now + 1000L) at += repeatDays * 86400000L;
                scheduleEveryDays(context, id, item.optString("title", "Herinnering"),
                        item.optString("body", ""), at, repeatDays, item.optString("target", "today"));
            } else {
                long at = item.optLong("at", 0L);
                if (at > now) schedule(context, id, item.optString("title", "Herinnering"),
                        item.optString("body", ""), at, item.optString("target", "today"));
                else remove(context, id);
            }
        }
    }

    static void handleFired(Context context, String id) {
        if (id == null) return;
        JSONObject item = load(context).optJSONObject(id);
        if (item != null && item.optBoolean("repeatDaily", false)) {
            scheduleDaily(context, id, item.optString("title", "Herinnering"),
                    item.optString("body", ""), item.optInt("hour", 9), item.optInt("minute", 0),
                    item.optString("target", "today"));
        } else if (item != null && item.optInt("repeatDays", 0) > 0) {
            int days = item.optInt("repeatDays", 1);
            long next = item.optLong("at", System.currentTimeMillis()) + days * 86400000L;
            while (next <= System.currentTimeMillis() + 1000L) next += days * 86400000L;
            scheduleEveryDays(context, id, item.optString("title", "Herinnering"),
                    item.optString("body", ""), next, days, item.optString("target", "today"));
        } else {
            remove(context, id);
        }
    }

    private static PendingIntent pendingIntent(Context context, String id, String title, String body, String target) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("nl.mijnpersoonlijkeapp.widgets.ALARM." + id);
        intent.putExtra("id", id);
        intent.putExtra("title", title == null || title.trim().isEmpty() ? "Mijn dag" : title);
        intent.putExtra("body", body == null ? "" : body);
        intent.putExtra("target", target == null || target.trim().isEmpty() ? "today" : target);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode(id), intent, flags);
    }

    private static int requestCode(String id) {
        return id.hashCode() & 0x7fffffff;
    }

    private static void save(Context context, String id, String title, String body, long at, String target,
                             boolean repeatDaily, int hour, int minute, int repeatDays) {
        try {
            JSONObject items = load(context);
            JSONObject item = new JSONObject();
            item.put("title", title == null ? "Herinnering" : title);
            item.put("body", body == null ? "" : body);
            item.put("at", at);
            item.put("target", target == null ? "today" : target);
            item.put("repeatDaily", repeatDaily);
            item.put("repeatDays", Math.max(0, repeatDays));
            if (repeatDaily || repeatDays > 0) {
                item.put("hour", hour);
                item.put("minute", minute);
            }
            items.put(id, item);
            prefs(context).edit().putString(KEY_ITEMS, items.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void remove(Context context, String id) {
        try {
            JSONObject items = load(context);
            items.remove(id);
            prefs(context).edit().putString(KEY_ITEMS, items.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static JSONObject load(Context context) {
        try { return new JSONObject(prefs(context).getString(KEY_ITEMS, "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
