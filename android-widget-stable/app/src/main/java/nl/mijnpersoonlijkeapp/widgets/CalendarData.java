package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

final class CalendarData {
    private static final String PREFS = "native_calendar_settings";
    private static final String KEY_SELECTED = "selected_google_calendar_ids";
    private static final String KEY_INITIALIZED = "calendar_selection_initialized";

    private CalendarData() {}

    static JSONArray calendars(Context context) {
        JSONArray out = new JSONArray();
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return out;

        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.VISIBLE
        };

        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars.ACCOUNT_NAME + " ASC, " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"
        )) {
            if (cursor == null) return out;
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String account = cursor.getString(2);
                String type = cursor.getString(3);
                boolean visible = cursor.getInt(4) != 0;
                if (!isGoogle(type) || isTaskCalendar(name)) continue;

                JSONObject item = new JSONObject();
                item.put("id", id);
                item.put("name", name == null || name.trim().isEmpty() ? "Google Agenda" : name.trim());
                item.put("account", account == null ? "" : account.trim());
                item.put("visible", visible);
                out.put(item);
            }
        } catch (Exception ignored) {}
        ensureDefaultSelection(context, out);
        return out;
    }

    static JSONArray selectedIdsJson(Context context) {
        JSONArray out = new JSONArray();
        for (Long id : selectedIds(context)) out.put(id);
        return out;
    }

    static void setSelectedIds(Context context, String json) {
        try {
            JSONArray input = new JSONArray(json == null ? "[]" : json);
            JSONArray clean = new JSONArray();
            for (int i = 0; i < input.length(); i++) {
                long id = input.optLong(i, -1);
                if (id >= 0) clean.put(id);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_SELECTED, clean.toString())
                    .putBoolean(KEY_INITIALIZED, true)
                    .apply();
        } catch (Exception ignored) {}
    }

    static JSONArray todayEvents(Context context) {
        JSONArray out = new JSONArray();
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return out;

        // Ensures Google calendars are discovered and first-use defaults are created.
        calendars(context);
        Set<Long> selected = selectedIds(context);
        if (selected.isEmpty()) return out;

        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = LocalDate.now(zone);
        long begin = day.atStartOfDay(zone).toInstant().toEpochMilli();

        String[] projection = new String[]{
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID
        };

        ContentResolver resolver = context.getContentResolver();

        // Zoek steeds verder vooruit totdat er 3 afspraken zijn.
        // Zo blijft een rustige agenda toch gevuld zonder meteen een enorme periode op te vragen.
        LocalDate[] horizons = new LocalDate[]{
                day.plusDays(31),
                day.plusMonths(6),
                day.plusYears(2),
                day.plusYears(10)
        };
        long rangeBegin = begin;

        for (LocalDate horizon : horizons) {
            if (out.length() >= 3) break;
            long rangeEnd = horizon.atStartOfDay(zone).toInstant().toEpochMilli();

            android.net.Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, rangeBegin);
            ContentUris.appendId(builder, rangeEnd);

            try (Cursor cursor = resolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC"
            )) {
                if (cursor != null) {
                    while (cursor.moveToNext() && out.length() < 3) {
                        long calendarId = cursor.getLong(5);
                        if (!selected.contains(calendarId)) continue;

                        String title = cursor.getString(0);
                        long startMs = cursor.getLong(1);
                        long endMs = cursor.getLong(2);
                        String location = cursor.getString(3);
                        boolean allDay = cursor.getInt(4) != 0;

                        JSONObject event = new JSONObject();
                        event.put("summary", title == null || title.trim().isEmpty() ? "Afspraak" : title.trim());
                        event.put("calendarId", calendarId);
                        if (location != null && !location.trim().isEmpty()) event.put("location", location.trim());

                        JSONObject start = new JSONObject();
                        JSONObject finish = new JSONObject();
                        if (allDay) {
                            LocalDate eventDay = Instant.ofEpochMilli(startMs).atZone(java.time.ZoneOffset.UTC).toLocalDate();
                            LocalDate eventEndDay = Instant.ofEpochMilli(endMs).atZone(java.time.ZoneOffset.UTC).toLocalDate();
                            start.put("date", eventDay.toString());
                            finish.put("date", eventEndDay.toString());
                        } else {
                            ZonedDateTime s = Instant.ofEpochMilli(startMs).atZone(zone);
                            ZonedDateTime e = Instant.ofEpochMilli(endMs).atZone(zone);
                            start.put("dateTime", s.toOffsetDateTime().toString());
                            finish.put("dateTime", e.toOffsetDateTime().toString());
                        }
                        event.put("start", start);
                        event.put("end", finish);
                        out.put(event);
                    }
                }
            } catch (Exception ignored) {}

            rangeBegin = rangeEnd + 1L;
        }
        return out;
    }

    private static boolean isGoogle(String type) {
        if (type == null) return false;
        String value = type.toLowerCase();
        return value.contains("google");
    }

    private static boolean isTaskCalendar(String name) {
        if (name == null) return false;
        String value = name.trim().toLowerCase();
        return value.equals("tasks") || value.equals("taken") || value.contains("google tasks") || value.contains("google taken");
    }

    private static void ensureDefaultSelection(Context context, JSONArray available) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return;

        JSONArray defaults = new JSONArray();
        for (int i = 0; i < available.length(); i++) {
            JSONObject item = available.optJSONObject(i);
            if (item != null && item.optBoolean("visible", true)) defaults.put(item.optLong("id"));
        }
        prefs.edit()
                .putString(KEY_SELECTED, defaults.toString())
                .putBoolean(KEY_INITIALIZED, true)
                .apply();
    }

    private static Set<Long> selectedIds(Context context) {
        Set<Long> ids = new HashSet<>();
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED, "[]");
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) {
                long id = arr.optLong(i, -1);
                if (id >= 0) ids.add(id);
            }
        } catch (Exception ignored) {}
        return ids;
    }
}
