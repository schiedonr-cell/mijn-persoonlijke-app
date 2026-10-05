package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class CalendarData {
    private CalendarData() {}

    static JSONArray todayEvents(Context context) {
        JSONArray out = new JSONArray();
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return out;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = LocalDate.now(zone);
        long begin = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        String[] projection = new String[]{
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY
        };

        ContentResolver resolver = context.getContentResolver();
        android.net.Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, begin);
        ContentUris.appendId(builder, end);

        try (Cursor cursor = resolver.query(
                builder.build(),
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
        )) {
            if (cursor == null) return out;
            while (cursor.moveToNext()) {
                String title = cursor.getString(0);
                long startMs = cursor.getLong(1);
                long endMs = cursor.getLong(2);
                String location = cursor.getString(3);
                boolean allDay = cursor.getInt(4) != 0;

                JSONObject event = new JSONObject();
                event.put("summary", title == null || title.trim().isEmpty() ? "Afspraak" : title.trim());
                if (location != null && !location.trim().isEmpty()) event.put("location", location.trim());

                JSONObject start = new JSONObject();
                JSONObject finish = new JSONObject();
                if (allDay) {
                    start.put("date", day.toString());
                    finish.put("date", day.plusDays(1).toString());
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
        } catch (Exception ignored) {}
        return out;
    }
}
