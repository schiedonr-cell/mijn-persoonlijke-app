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
import org.json.JSONObject;
import java.util.Calendar;

public final class PreviewAlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "mijndag_test_reminders";
    private static final String PREF = "preview_alarm_store";
    private static final String FOCUS = "test-focus-end";

    static void schedule(Context context, String id, String title, String description, long at, int repeatDays) {
        if (id == null || id.isEmpty() || at < System.currentTimeMillis()+400) return;
        try {
            JSONObject spec = new JSONObject();
            spec.put("id",id); spec.put("title",title); spec.put("body",description);
            spec.put("at",at); spec.put("days",repeatDays);
            SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            prefs.edit().putString(id,spec.toString()).apply();
            postAlarm(context,spec);
        } catch(Exception ex) {android.util.Log.w("MijnDagTest","Alarm maken mislukt",ex);}
    }

    static void cancel(Context context,String id) {
        if (id == null || id.isEmpty()) return;
        context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(id).apply();
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if(manager!=null) manager.cancel(pending(context,id,PendingIntent.FLAG_UPDATE_CURRENT));
    }

    static void focus(Context context,int seconds,String name) {
        schedule(context,FOCUS,"Focus afgerond",name==null||name.isEmpty()?"Je Focus is klaar.":name,
            System.currentTimeMillis()+Math.max(1,seconds)*1000L,0);
    }
    static void cancelFocus(Context context) {cancel(context,FOCUS);}

    private static PendingIntent pending(Context ctx,String id,int flags) {
        Intent intent=new Intent(ctx,PreviewAlarmReceiver.class);
        intent.setAction("nl.mijnpersoonlijkeapp.test.ALARM");
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
        if(Build.VERSION.SDK_INT>=31 && !manager.canScheduleExactAlarms())
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
                if(at>System.currentTimeMillis())postAlarm(ctx,spec);
                else prefs.edit().remove(key).apply();
            }catch(Exception ex){android.util.Log.w("MijnDagTest","Herstel melding mislukt",ex);}
        }
    }
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){restore(context);return;}
        String id=intent.getStringExtra("alarm_id");
        if(id==null)return;
        SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        try {
            JSONObject spec=new JSONObject(prefs.getString(id,"{}"));
            if(!id.equals(spec.optString("id")))return;
            show(context,id,spec.optString("title","Herinnering"),spec.optString("body",""));
            int days=spec.optInt("days");
            if(days>0){
                Calendar cal=Calendar.getInstance();cal.setTimeInMillis(spec.optLong("at"));
                do{cal.add(Calendar.DATE,days);}while(cal.getTimeInMillis()<=System.currentTimeMillis());
                spec.put("at",cal.getTimeInMillis());
                prefs.edit().putString(id,spec.toString()).apply();postAlarm(context,spec);
            }else prefs.edit().remove(id).apply();
        }catch(Exception ex){android.util.Log.w("MijnDagTest","Melding mislukt",ex);}
    }
    private static void show(Context ctx,String id,String title,String body){
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(
            new NotificationChannel(CHANNEL,"Mijn Dag TEST meldingen",NotificationManager.IMPORTANCE_HIGH));
        if(Build.VERSION.SDK_INT>=33 &&
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(ctx,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(ctx,id.hashCode(),open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(ctx,CHANNEL):new Notification.Builder(ctx);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
          .setContentTitle(title).setContentText(body).setContentIntent(content)
          .setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
          .setDefaults(Notification.DEFAULT_ALL);
        nm.notify(id.hashCode(),builder.build());
    }
}
