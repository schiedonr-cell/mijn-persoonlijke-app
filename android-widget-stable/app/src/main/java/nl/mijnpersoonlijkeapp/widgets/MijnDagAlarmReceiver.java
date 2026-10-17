package nl.mijnpersoonlijkeapp.widgets;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Calendar;

public final class MijnDagAlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "mijndag_reminders_v1";
    private static final String PREF = "mijndag_alarm_store";
    private static final String FOCUS = "mijndag-focus-end";
    private static final String DIAGNOSE = "mijndag-diagnose";
    private static final String DAY_FOCUS = "mijndag-day-focus-end";
    private static final String FOCUS_CHANNEL = "mijndag_focus_active_v1";
    private static final String END_CHANNEL = "mijndag_focus_end_v1";
    private static final int ACTIVE_FOCUS_ID = 711426;
    private static final int ACTIVE_DAY_FOCUS_ID = 711427;

    static void schedule(Context context, String id, String title, String description, long at, int repeatDays) {
        if (id == null || id.isEmpty() || at < System.currentTimeMillis()+400) return;
        try {
            JSONObject spec = new JSONObject();
            spec.put("id",id); spec.put("title",title); spec.put("body",description);
            spec.put("at",at); spec.put("days",repeatDays);
            SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            prefs.edit().putString(id,spec.toString()).apply();
            postAlarm(context,spec);
        } catch(Exception ex) {android.util.Log.w("MijnDagAlarm","Alarm maken mislukt",ex);}
    }

    static void cancel(Context context,String id) {
        if (id == null || id.isEmpty()) return;
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(id).apply();
        if(FOCUS.equals(id)||DAY_FOCUS.equals(id))cancelOngoing(context,id);
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(manager!=null) manager.cancel(pending(context,id,PendingIntent.FLAG_UPDATE_CURRENT));
    }

    static void focus(Context context,int seconds,String name) {
        focusWithId(context,FOCUS,seconds,name);
    }
    static void dayFocus(Context context,int seconds,String name) {
        focusWithId(context,DAY_FOCUS,seconds,name);
    }
    private static void focusWithId(Context context,String id,int seconds,String name) {
        long until=System.currentTimeMillis()+Math.max(1,seconds)*1000L;
        schedule(context,id,"Focus klaar — kies zelf of je afvinkt",
            name==null||name.isEmpty()?"Je Focus-tijd is voorbij.":name,until,0);
        showActiveFocus(context,id,name,until);
    }
    static void cancelFocus(Context context) {
        cancel(context,FOCUS);
    }
    static void cancelDayFocus(Context context) {
        cancel(context,DAY_FOCUS);
    }
    static int alarmVolume(Context context){
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        return am==null?-1:am.getStreamVolume(AudioManager.STREAM_ALARM);
    }
    static int alarmVolumeMax(Context context){
        AudioManager am=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        return am==null?-1:am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }
    static boolean focusScheduled(Context ctx){
        SharedPreferences prefs=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        return prefs.contains(FOCUS)||prefs.contains(DAY_FOCUS);
    }
    static void testNotification(Context ctx){
        show(ctx,"preview-direct","Testmelding Mijn Dag","Android-meldingen werken vanuit deze app.");
    }

    private static PendingIntent pending(Context ctx,String id,int flags) {
        Intent intent=new Intent(ctx,MijnDagAlarmReceiver.class);
        intent.setAction("nl.mijnpersoonlijkeapp.ALARM");
        intent.putExtra("alarm_id",id);
        return PendingIntent.getBroadcast(ctx,id.hashCode(),intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void postAlarm(Context ctx,JSONObject spec) {
        long at=spec.optLong("at");
        if(at<=System.currentTimeMillis())return;
        AlarmManager manager=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        if(manager==null)return;
        PendingIntent pi=pending(ctx,spec.optString("id"),PendingIntent.FLAG_UPDATE_CURRENT);
        // Bij toestemming exact; anders veilige Android fallback (kan vertraagd zijn).
        if(FOCUS.equals(spec.optString("id"))||DAY_FOCUS.equals(spec.optString("id"))||DIAGNOSE.equals(spec.optString("id"))){
            // Android's alarmklok is exact, ook in Doze, zonder extra exacte-alarmtoestemming.
            Intent showIntent=new Intent(ctx,MainActivity.class);
            PendingIntent info=PendingIntent.getActivity(ctx,39172,showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at,info),pi);
        }else if(Build.VERSION.SDK_INT>=31 && !manager.canScheduleExactAlarms())
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        else
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
    }
    static void restore(Context ctx) {
        SharedPreferences prefs=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        for(String key:prefs.getAll().keySet()){
            try {
                JSONObject spec=new JSONObject(prefs.getString(key,""));
                long at=spec.optLong("at");int days=spec.optInt("days");
                if(at<=System.currentTimeMillis()&&days>0){
                    Calendar c=Calendar.getInstance();c.setTimeInMillis(at);
                    do{c.add(Calendar.DATE,days);}while(c.getTimeInMillis()<=System.currentTimeMillis());
                    at=c.getTimeInMillis();spec.put("at",at);
                    prefs.edit().putString(key,spec.toString()).apply();
                }
                if(at>System.currentTimeMillis()){
                    postAlarm(ctx,spec);
                    if(FOCUS.equals(key)||DAY_FOCUS.equals(key))
                        showActiveFocus(ctx,key,spec.optString("body"),at);
                }else prefs.edit().remove(key).apply();
            }catch(Exception ex){android.util.Log.w("MijnDagAlarm","Herstel melding mislukt",ex);}
        }
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){restore(context);return;}
        String id=intent.getStringExtra("alarm_id");
        if(id==null)return;
        context.getSharedPreferences("mijndag_alarm_debug",Context.MODE_PRIVATE).edit()
          .putString("last_alarm",id+" ontvangen om "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date())).apply();
        if(FOCUS.equals(id)||DAY_FOCUS.equals(id))cancelOngoing(context,id);
        SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        try {
            JSONObject spec=new JSONObject(prefs.getString(id,"{}"));
            if(!id.equals(spec.optString("id")))return;
            show(context,id,spec.optString("title","Herinnering"),spec.optString("body",""));
            if(FOCUS.equals(id)||DAY_FOCUS.equals(id)||DIAGNOSE.equals(id))
                playAlarmSound(context);
            int days=spec.optInt("days");
            if(days>0){
                Calendar cal=Calendar.getInstance();cal.setTimeInMillis(spec.optLong("at"));
                do{cal.add(Calendar.DATE,days);}while(cal.getTimeInMillis()<=System.currentTimeMillis());
                spec.put("at",cal.getTimeInMillis());
                prefs.edit().putString(id,spec.toString()).apply();postAlarm(context,spec);
            }else prefs.edit().remove(id).apply();
        }catch(Exception ex){android.util.Log.w("MijnDagAlarm","Melding mislukt",ex);}
    }

    // De zichtbare Android-melding is niet voldoende: die kan stil zijn.
    // Speel daarom bij een ontvangen alarm ook geluid via de echte alarm-audiostroom.
    // goAsync houdt deze ontvanger maximaal 7 seconden actief tijdens het geluid.
    private void playAlarmSound(Context context){
        final PendingResult pending=goAsync();
        MediaPlayer player=null;
        try {
            Uri tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if(tone==null)tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(context,tone);
            player.setLooping(true);
            player.prepare();
            player.start();
            context.getSharedPreferences("mijndag_alarm_debug",Context.MODE_PRIVATE).edit()
                .putString("audio_status","alarmgeluid gestart").apply();
        }catch(Exception exc){
            android.util.Log.e("MijnDagAlarm","Alarmgeluid afspelen mislukt",exc);
            context.getSharedPreferences("mijndag_alarm_debug",Context.MODE_PRIVATE).edit()
                .putString("audio_status","geluidsfout: "+exc.getClass().getSimpleName()).apply();
        }
        try{
            Vibrator v=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
            if(v!=null&&v.hasVibrator()){
                if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(
                    new long[]{0,500,250,500,250,800},-1));
                else v.vibrate(new long[]{0,500,250,500,250,800},-1);
            }
        }catch(Exception ignored){}
        final MediaPlayer playing=player;
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            if(playing!=null){try{playing.stop();}catch(Exception ignored){}try{playing.release();}catch(Exception ignored){}}
            pending.finish();
        },6500);
    }
    private static void ensureChannels(Context ctx,NotificationManager nm){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationChannel reminders=new NotificationChannel(CHANNEL,
            "Mijn Dag herinneringen",NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(reminders);
        NotificationChannel countdown=new NotificationChannel(FOCUS_CHANNEL,
            "Lopende Focus",NotificationManager.IMPORTANCE_DEFAULT);
        countdown.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        countdown.setSound(null,null);
        nm.createNotificationChannel(countdown);
        NotificationChannel ends=new NotificationChannel(END_CHANNEL,
            "Focus klaar",NotificationManager.IMPORTANCE_HIGH);
        Uri tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ends.setSound(tone,new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ends.enableVibration(true);
        ends.setVibrationPattern(new long[]{0,600,200,600,200,900});
        ends.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ends);
    }
    private static int focusNotificationId(String id) {
        return DAY_FOCUS.equals(id)?ACTIVE_DAY_FOCUS_ID:ACTIVE_FOCUS_ID;
    }
    private static void cancelOngoing(Context ctx,String id){
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.cancel(focusNotificationId(id));
    }
    private static void showActiveFocus(Context ctx,String id,String name,long until){
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        ensureChannels(ctx,nm);
        if(Build.VERSION.SDK_INT>=33 &&
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=
            android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(ctx,MainActivity.class);
        PendingIntent tap=PendingIntent.getActivity(ctx,98124,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(ctx,FOCUS_CHANNEL):new Notification.Builder(ctx);
        notification.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus loopt")
            .setContentText((name==null||name.isEmpty()?"Je activiteit":name)+" · nog")
            .setContentIntent(tap)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setWhen(until)
            .setUsesChronometer(true);
        if(Build.VERSION.SDK_INT>=24)notification.setChronometerCountDown(true);
        nm.notify(focusNotificationId(id),notification.build());
    }
    private static void show(Context ctx,String id,String title,String body){
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        ensureChannels(ctx,nm);
        if(Build.VERSION.SDK_INT>=33 &&
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(ctx,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(ctx,id.hashCode(),open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String channel=FOCUS.equals(id)||DAY_FOCUS.equals(id)?END_CHANNEL:CHANNEL;
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(ctx,channel):new Notification.Builder(ctx);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
          .setContentTitle(title).setContentText(body).setContentIntent(content)
          .setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM)
          .setVisibility(Notification.VISIBILITY_PUBLIC)
          .setPriority(Notification.PRIORITY_HIGH)
          .setDefaults(Notification.DEFAULT_ALL);
        nm.notify(id.hashCode(),builder.build());
    }
}
