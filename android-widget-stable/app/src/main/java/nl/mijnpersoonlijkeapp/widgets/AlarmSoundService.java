package nl.mijnpersoonlijkeapp.widgets;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class AlarmSoundService extends Service {
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String id = intent == null ? null : intent.getStringExtra("id");
        String title = intent == null ? null : intent.getStringExtra("title");
        String body = intent == null ? null : intent.getStringExtra("body");
        String target = intent == null ? null : intent.getStringExtra("target");

        if (title == null || title.trim().isEmpty()) title = "Mijn dag";
        if (body == null || body.trim().isEmpty()) body = "Je hebt iets gepland.";
        if (target == null || target.trim().isEmpty()) target = "today";

        ReminderReceiver.ensureChannel(this);
        startForeground(
                ReminderReceiver.notificationIdForService(id),
                ReminderReceiver.buildAlarmNotification(this, id, title, body, target)
        );

        stopPlayback();
        startAlarmSound();
        startVibration();
        return START_NOT_STICKY;
    }

    private void startAlarmSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) return;

            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(this, uri);
            mp.setLooping(true);
            try { mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception ignored) {}
            mp.prepare();
            mp.start();
            player = mp;
        } catch (Exception ignored) {}
    }

    private void startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                vibrator = vm == null ? null : vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }
            if (vibrator == null) return;
            long[] pattern = new long[]{0, 500, 180, 500, 180, 800, 500};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopPlayback() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Exception ignored) {}
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        vibrator = null;
    }

    @Override public void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
