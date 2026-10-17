package nl.mijnpersoonlijkeapp.widgets;

import static org.junit.Assert.*;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class MijnDagMigrationInstrumentedTest {
    private static WebView findWebView(View v) {
        if(v instanceof WebView)return (WebView)v;
        if(v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++){
                WebView web=findWebView(group.getChildAt(i));
                if(web!=null)return web;
            }
        }
        return null;
    }
    private static String js(ActivityScenario<MainActivity> scenario,String code) throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<String> output=new AtomicReference<>("");
        scenario.onActivity(a -> {
            WebView web=findWebView(a.getWindow().getDecorView());
            if(web==null){output.set("NO_WEBVIEW");latch.countDown();return;}
            web.evaluateJavascript(code,result->{output.set(result);latch.countDown();});
        });
        assertTrue("WebView reageert niet",latch.await(10,TimeUnit.SECONDS));
        return output.get();
    }
    @Test public void bundledAppHasNewHomeAndExistingFeatures() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] data;
        try(InputStream in=ctx.getAssets().open("app/index.html");
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
            data=out.toByteArray();
        }
        String html=new String(data,java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("Nieuw beginscherm ontbreekt",html.contains("myday-part-menu"));
        assertTrue("Focus ontbreekt",html.contains("dayPlanFocusReminder"));
        assertTrue("Weekmenu ontbreekt",html.contains("renderFoodAll"));
        assertTrue("Boodschappen ontbreken",html.contains("shopping"));
        assertTrue("Notities ontbreken",html.contains("notes"));
    }
    @Test public void existingStorageKeyAndWidgetsRemain() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("nl.mijnpersoonlijkeapp",ctx.getPackageName());
        assertNotNull(new android.content.ComponentName(ctx,TodayWidgetProvider.class));
        assertNotNull(new android.content.ComponentName(ctx,MediumWidgetProvider.class));
        assertNotNull(new android.content.ComponentName(ctx,LargeWidgetProvider.class));
    }
    @Test public void mainActivityOpensBundledScreenAndKeepsStorage() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.getSharedPreferences("native_app_state",Context.MODE_PRIVATE).edit()
            .putBoolean("import_done",true).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            String dom="";long deadline=System.currentTimeMillis()+35000;
            do {
                dom=js(scenario,"(function(){return !!(document.getElementById('dayPlanList')&&document.querySelector('.myday-part-menu'));})()");
                if("true".equals(dom))break;
                Thread.sleep(700);
            }while(System.currentTimeMillis()<deadline);
            assertEquals("Het nieuwe dagoverzicht moet werkelijk starten", "true",dom);
            String original=js(scenario,"(function(){return localStorage.getItem('mijndag_runtime_probe')})()");
            assertEquals("null",original);
            assertEquals("true",js(scenario,"(function(){localStorage.setItem('mijndag_runtime_probe','bewaard');return true})()"));
            assertEquals("\"bewaard\"",js(scenario,"localStorage.getItem('mijndag_runtime_probe')"));
            js(scenario,"localStorage.removeItem('mijndag_runtime_probe')");
        }
    }
    @Test public void nativeFocusAlarmCanScheduleAndCancel() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        MijnDagAlarmReceiver.focus(ctx,60,"Focus controle");
        assertTrue(MijnDagAlarmReceiver.focusScheduled(ctx));
        MijnDagAlarmReceiver.cancelFocus(ctx);
        assertFalse(MijnDagAlarmReceiver.focusScheduled(ctx));
    }
}
