package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class ReminderReceiver extends BroadcastReceiver {
    // New channel ID on purpose: Android notification channel sound settings are immutable
    // after creation, so this guarantees the audible alarm settings are actually applied.
    static final String CHANNEL_ID = "mijn_dag_alarms_v2";
    private static final String ACTION_SNOOZE = "nl.mijnpersoonlijkeapp.widgets.REMINDER_SNOOZE";
    private static final String ACTION_STOP = "nl.mijnpersoonlijkeapp.widgets.REMINDER_STOP";
    private static final long SNOOZE_MS = 10L * 60L * 1000L;

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        String id = intent == null ? null : intent.getStringExtra("id");
        String title = intent == null ? null : intent.getStringExtra("title");
        String body = intent == null ? null : intent.getStringExtra("body");
        String target = intent == null ? null : intent.getStringExtra("target");

        if (ACTION_STOP.equals(action)) {
            cancelNotification(context, id);
            stopVibration(context);
            return;
        }

        if (ACTION_SNOOZE.equals(action)) {
            cancelNotification(context, id);
            stopVibration(context);
            if (title == null || title.trim().isEmpty()) title = "Mijn dag";
            if (body == null || body.trim().isEmpty()) body = "Je hebt iets gepland.";
            if (target == null || target.trim().isEmpty()) target = "today";
            String snoozeId = "snooze-" + (id == null ? System.currentTimeMillis() : id) + "-" + System.currentTimeMillis();
            NativeAlarmScheduler.schedule(
                    context,
                    snoozeId,
                    title,
                    body,
                    System.currentTimeMillis() + SNOOZE_MS,
                    target
            );
            return;
        }

        if (title == null || title.trim().isEmpty()) title = "Mijn dag";
        if (body == null || body.trim().isEmpty()) body = "Je hebt iets gepland.";
        if (target == null || target.trim().isEmpty()) target = "today";

        ensureChannel(context);

        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            NativeAlarmScheduler.handleFired(context, id);
            return;
        }

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("target", target);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int immutableFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) immutableFlags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent content = PendingIntent.getActivity(
                context,
                requestCode(id, 0),
                open,
                immutableFlags
        );

        PendingIntent snooze = actionIntent(context, ACTION_SNOOZE, id, title, body, target, 1);
        PendingIntent stop = actionIntent(context, ACTION_STOP, id, title, body, target, 2);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setDefaults(Notification.DEFAULT_ALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(false)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_lock_idle_alarm, "Snooze 10 min", snooze).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build());

        Notification notification = b.build();
        // Repeat the alarm sound until the user opens, stops or snoozes the reminder.
        notification.flags |= Notification.FLAG_INSISTENT;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notificationId(id), notification);

        vibrate(context);
        NativeAlarmScheduler.handleFired(context, id);
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
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 180, 500, 180, 800});
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(sound, attrs);
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
