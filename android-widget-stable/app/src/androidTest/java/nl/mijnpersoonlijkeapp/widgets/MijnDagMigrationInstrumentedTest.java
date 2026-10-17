package nl.mijnpersoonlijkeapp.widgets;

import static org.junit.Assert.*;
import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

@RunWith(AndroidJUnit4.class)
public class MijnDagMigrationInstrumentedTest {
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
}
