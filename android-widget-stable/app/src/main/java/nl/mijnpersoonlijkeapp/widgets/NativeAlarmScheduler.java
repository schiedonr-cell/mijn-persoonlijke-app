package nl.mijnpersoonlijkeapp.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.util.Iterator;

final class NativeAlarmScheduler {
    private static final String PREFS = "native_alarm_store";
    private static final String KEY_ITEMS = "items";

    private NativeAlarmScheduler() {}

    static void schedule(Context context, String id, String title, String body, long triggerAt, String target) {
        if (id == null || id.trim().isEmpty()) return;
        long at = Math.max(System.currentTimeMillis() + 1000L, triggerAt);
        save(context, id, title, body, at, target);

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

    static void cancel(Context context, String id) {
        if (id == null || id.trim().isEmpty()) return;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            try { alarm.cancel(pendingIntent(context, id, "", "", "today")); } catch (Exception ignored) {}
        }
        remove(context, id);
    }

    static void rescheduleAll(Context context) {
        JSONObject items = load(context);
        long now = System.currentTimeMillis();
        Iterator<String> keys = items.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONObject item = items.optJSONObject(id);
            if (item == null) continue;
            long at = item.optLong("at", 0L);
            if (at <= now) continue;
            schedule(context, id,
                    item.optString("title", "Herinnering"),
                    item.optString("body", ""),
                    at,
                    item.optString("target", "today"));
        }
    }

    static void markFired(Context context, String id) {
        remove(context, id);
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

    private static void save(Context context, String id, String title, String body, long at, String target) {
        try {
            JSONObject items = load(context);
            JSONObject item = new JSONObject();
            item.put("title", title == null ? "Herinnering" : title);
            item.put("body", body == null ? "" : body);
            item.put("at", at);
            item.put("target", target == null ? "today" : target);
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
