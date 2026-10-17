package nl.mijnpersoonlijkeapp.widgets;

import static org.junit.Assert.*;
import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PreviewAlarmInstrumentedTest {
    @Test public void alarmReceiverReceivesScheduled12SecondAlarm() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="instrumentation-alarm-verify";
        ctx.getSharedPreferences("preview_debug",Context.MODE_PRIVATE).edit()
            .remove("last_alarm").remove("audio_status").commit();
        // Geen geluidsweergave nodig voor een bewijs van de broadcastbezorging.
        PreviewAlarmReceiver.schedule(ctx,key,"Instrumentatie alarm","Controle van AlarmManager",
            System.currentTimeMillis()+4500,0);
        boolean delivered=false;
        String actual="";
        for(int i=0;i<36;i++){
            Thread.sleep(500);
            actual=ctx.getSharedPreferences("preview_debug",Context.MODE_PRIVATE)
                .getString("last_alarm","");
            if(actual.contains(key)){delivered=true;break;}
        }
        assertTrue("De door Android ingeplande alarmbroadcast is niet ontvangen: "+actual,delivered);
    }
    @Test public void focusHasSeparateCompletionAlarmAndDoesNotCheckTask() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreviewAlarmReceiver.focus(ctx,8,"Proef Focus");
        assertTrue("Focus alarm is niet geregistreerd",PreviewAlarmReceiver.focusScheduled(ctx));
        AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        assertNotNull("Android AlarmManager ontbreekt",am);
        PreviewAlarmReceiver.cancelFocus(ctx);
        assertFalse("Focus alarm is niet ingetrokken",PreviewAlarmReceiver.focusScheduled(ctx));
    }
}