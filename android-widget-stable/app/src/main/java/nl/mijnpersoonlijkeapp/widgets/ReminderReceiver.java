package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class ReminderReceiver extends BroadcastReceiver {
    // New channel ID on purpose: Android notification channel sound settings are immutable
    // after creation, so this guarantees the audible alarm settings are actually applied.
    static final String CHANNEL_ID = "mijn_dag_alarms_v3";
    private static final String ACTION_SNOOZE = "nl.mijnpersoonlijkeapp.widgets.REMINDER_SNOOZE";
    private static final String ACTION_STOP = "nl.mijnpersoonlijkeapp.widgets.REMINDER_STOP";
    private static final String ACTION_OPEN = "nl.mijnpersoonlijkeapp.widgets.REMINDER_OPEN";
    private static final String SNOOZE_KEY = "snooze_choice";
    private static final long DEFAULT_SNOOZE_MS = 10L * 60L * 1000L;
    private static final long STOP_SUPPRESS_MS = 15L * 60L * 1000L;
    private static final String GROUP_PREFS = "reminder_group_state";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        String id = intent == null ? null : intent.getStringExtra("id");
        String title = intent == null ? null : intent.getStringExtra("title");
        String body = intent == null ? null : intent.getStringExtra("body");
        String target = intent == null ? null : intent.getStringExtra("target");

        if (ACTION_STOP.equals(action)) {
            suppressGroup(context, groupKeyFromId(id), System.currentTimeMillis() + STOP_SUPPRESS_MS);
            stopAlarmService(context);
            cancelNotification(context, id);
            stopVibration(context);
            return;
        }

        if (ACTION_OPEN.equals(action)) {
            stopAlarmService(context);
            cancelNotification(context, id);
            stopVibration(context);
            if (target == null || target.trim().isEmpty()) target = "today";
            Intent open = new Intent(context, MainActivity.class);
            open.putExtra("target", target);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try { context.startActivity(open); } catch (Exception ignored) {}
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            // Een melding die inmiddels verplaatst/afgerond is mag ook via
            // een achtergebleven meldingsactie geen nieuwe snooze aanmaken.
            if (NativeAlarmScheduler.wasExplicitlyCancelled(context, id)) {
                dismissCancelledAlarm(context, id);
                return;
            }
            stopAlarmService(context);
            cancelNotification(context, id);
            stopVibration(context);
            if (title == null || title.trim().isEmpty()) title = "Mijn dag";
            if (body == null || body.trim().isEmpty()) body = "Je hebt iets gepland.";
            if (target == null || target.trim().isEmpty()) target = "today";

            long snoozeMs = DEFAULT_SNOOZE_MS;
            try {
                Bundle results = RemoteInput.getResultsFromIntent(intent);
                CharSequence choice = results == null ? null : results.getCharSequence(SNOOZE_KEY);
                snoozeMs = snoozeDelay(choice == null ? "" : choice.toString());
            } catch (Exception ignored) {}

            String group = groupKeyFromId(id);
            suppressGroup(context, group, System.currentTimeMillis() + snoozeMs - 1000L);
            String snoozeId = (id == null ? "reminder" : stripSnoozeSuffix(id)) + "-snooze-" + System.currentTimeMillis();
            NativeAlarmScheduler.schedule(
                    context,
                    snoozeId,
                    title,
                    body,
                    System.currentTimeMillis() + snoozeMs,
                    target
            );
            return;
        }

        if (title == null || title.trim().isEmpty()) title = "Mijn dag";
        if (body == null || body.trim().isEmpty()) body = "Je hebt iets gepland.";
        if (target == null || target.trim().isEmpty()) target = "today";

        // Een al geannuleerd Android-alarm kan nog net in de broadcast-queue zitten.
        // Controleer daarom de bewaarde schedulerstatus voor de melding wordt getoond.
        if (!NativeAlarmScheduler.isScheduled(context, id)) return;
        String group = groupKeyFromId(id);
        boolean snoozedOccurrence = id != null && id.contains("-snooze-");
        if (!snoozedOccurrence && isGroupSuppressed(context, group)) {
            NativeAlarmScheduler.handleFired(context, id);
            return;
        }
        if (snoozedOccurrence) clearGroupSuppression(context, group);

        ensureChannel(context);

        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            NativeAlarmScheduler.handleFired(context, id);
            return;
        }

        Intent sound = new Intent(context, AlarmSoundService.class);
        sound.putExtra("id", id);
        sound.putExtra("title", title);
        sound.putExtra("body", body);
        sound.putExtra("target", target);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(sound);
            else context.startService(sound);
        } catch (Exception ignored) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(notificationId(id), buildAlarmNotification(context, id, title, body, target));
            vibrate(context);
        }

        NativeAlarmScheduler.handleFired(context, id);
    }

    private static String stripSnoozeSuffix(String id) {
        if (id == null) return "";
        int i = id.indexOf("-snooze-");
        return i >= 0 ? id.substring(0, i) : id;
    }

    private static String groupKeyFromId(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) return "";
        String id = stripSnoozeSuffix(rawId);

        if (id.startsWith("timed-task-")) return "task|" + id.substring("timed-task-".length());
        if (id.startsWith("timed-habit-")) return "habit|" + id.substring("timed-habit-".length());
        if (id.startsWith("timed-household-")) return "household|" + id.substring("timed-household-".length());

        int rem = id.indexOf("-rem-");
        if (rem > 0) {
            String base = id.substring(0, rem);
            if (base.startsWith("task-")) return "task|" + base.substring("task-".length());
            if (base.startsWith("habit-")) return "habit|" + base.substring("habit-".length());
            if (base.startsWith("household-")) return "household|" + base.substring("household-".length());
        }

        return "alarm|" + id;
    }

    private static SharedPreferences groupPrefs(Context context) {
        return context.getSharedPreferences(GROUP_PREFS, Context.MODE_PRIVATE);
    }

    private static void suppressGroup(Context context, String group, long until) {
        if (group == null || group.isEmpty()) return;
        groupPrefs(context).edit().putLong(group, until).apply();
    }

    private static boolean isGroupSuppressed(Context context, String group) {
        if (group == null || group.isEmpty()) return false;
        long until = groupPrefs(context).getLong(group, 0L);
        if (until <= System.currentTimeMillis()) {
            if (until > 0L) groupPrefs(context).edit().remove(group).apply();
            return false;
        }
        return true;
    }

    private static void clearGroupSuppression(Context context, String group) {
        if (group == null || group.isEmpty()) return;
        groupPrefs(context).edit().remove(group).apply();
    }

    static void clearSuppressionForItem(Context context, String type, String itemId) {
        if (type == null || itemId == null) return;
        clearGroupSuppression(context, type + "|" + itemId);
    }

    static void clearSuppressionForAlarm(Context context, String id) {
        clearGroupSuppression(context, groupKeyFromId(id));
    }

    static void dismissCancelledAlarm(Context context, String id) {
        AlarmSoundService.stopIfActive(context, id);
        cancelNotification(context, id);
    }

    static Notification buildAlarmNotification(Context context, String id, String title, String body, String target) {
        PendingIntent content = actionIntent(context, ACTION_OPEN, id, title, body, target, 0);
        PendingIntent snooze = snoozeIntent(context, id, title, body, target);
        PendingIntent stop = actionIntent(context, ACTION_STOP, id, title, body, target, 2);

        RemoteInput snoozeChoices = new RemoteInput.Builder(SNOOZE_KEY)
                .setLabel("Snooze")
                .setChoices(new CharSequence[]{"5 min", "10 min", "30 min", "1 uur"})
                .build();
        Notification.Action snoozeAction = new Notification.Action.Builder(
                android.R.drawable.ic_lock_idle_alarm, "Snooze", snooze)
                .addRemoteInput(snoozeChoices)
                .build();

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(content)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .addAction(snoozeAction)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build());
        return b.build();
    }

    static int notificationIdForService(String id) {
        return notificationId(id);
    }

    private static void stopAlarmService(Context context) {
        try { context.stopService(new Intent(context, AlarmSoundService.class)); } catch (Exception ignored) {}
    }

    private static PendingIntent snoozeIntent(Context context, String id, String title, String body, String target) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.setAction(ACTION_SNOOZE);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        i.putExtra("target", target);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(context, requestCode(id, 1), i, flags);
    }

    private static long snoozeDelay(String choice) {
        if ("5 min".equals(choice)) return 5L * 60L * 1000L;
        if ("30 min".equals(choice)) return 30L * 60L * 1000L;
        if ("1 uur".equals(choice)) return 60L * 60L * 1000L;
        return 10L * 60L * 1000L;
    }

    private static PendingIntent actionIntent(Context context, String action, String id, String title,
                                              String body, String target, int salt) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.setAction(action);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        i.putExtra("target", target);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode(id, salt), i, flags);
    }

    private static int requestCode(String id, int salt) {
        int base = id == null ? 71 : (id.hashCode() & 0x7fffffff);
        return (base ^ (salt * 0x45d9f3b)) & 0x7fffffff;
    }

    private static int notificationId(String id) {
        return id == null ? 71 : (id.hashCode() & 0x7fffffff);
    }

    private static void cancelNotification(Context context, String id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(notificationId(id));
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Alarmen en herinneringen", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Duidelijke alarmen voor taken, routines, huishouden en focus");
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableLights(true);
        nm.createNotificationChannel(channel);
    }

    private static void vibrate(Context context) {
        try {
            Vibrator vibrator = vibrator(context);
            if (vibrator == null) return;
            long[] pattern = new long[]{0, 500, 180, 500, 180, 800};
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            else vibrator.vibrate(pattern, -1);
        } catch (Exception ignored) {}
    }

    private static void stopVibration(Context context) {
        try {
            Vibrator vibrator = vibrator(context);
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {}
    }

    private static Vibrator vibrator(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
