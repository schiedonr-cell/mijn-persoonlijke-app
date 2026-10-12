package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

final class FocusTimerNotification {
    private static final String CHANNEL = "mijn_dag_focus_countdown_v1";
    private static final int ID = 783241;

    static void show(Context context, int seconds, String task) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26 && nm.getNotificationChannel(CHANNEL)==null){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Lopende focustimer",NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("De resterende focustijd, ook op het vergrendelscherm");
            channel.setSound(null,null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
        Intent open=new Intent(context,MainActivity.class);
        open.putExtra("target","focus");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,20486,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
                ?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Focus · "+(task==null||task.trim().isEmpty()?"Bezig":task))
                .setContentText("Resterende tijd")
                .setWhen(System.currentTimeMillis()+Math.max(1,seconds)*1000L)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(content);
        nm.notify(ID,b.build());
    }

    static void showPaused(Context context, int seconds, String task) {
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26 && nm.getNotificationChannel(CHANNEL)==null){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Lopende focustimer",NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null,null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
        Intent open=new Intent(context,MainActivity.class);
        open.putExtra("target","focus");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,20486,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        int s=Math.max(0,seconds);
        String time=s>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",
                s/3600,(s%3600)/60,s%60)
                :String.format(java.util.Locale.ROOT,"%02d:%02d",s/60,s%60);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
                ?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_media_pause)
                .setContentTitle("Focus gepauzeerd")
                .setContentText(time+" resterend · "+(task==null||task.trim().isEmpty()?"Focus":task))
                .setShowWhen(false)
                .setUsesChronometer(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentIntent(content);
        nm.notify(ID,b.build());
    }

    static void cancel(Context context){
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)nm.cancel(ID);
    }
}
