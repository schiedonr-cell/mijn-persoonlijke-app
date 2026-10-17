package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.ServiceWorkerController;
import android.webkit.ServiceWorkerClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.ViewGroup;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {
    private static final String BASE = "https://schiedonr-cell.github.io/mijn-persoonlijke-app/";
    private static final String APP_PATH = "/mijn-persoonlijke-app/";
    private static final String TRANSFER = BASE + "transfer.html";
    private static final String PREFS = "native_app_state";
    private static final String IMPORT_DONE = "import_done";
    private static final String IMPORT_FILE = "pending-import.json";
    private static final int PICK_FILE = 77;
    private static final int CALENDAR_PERMISSION = 78;
    private static final int SAVE_BACKUP_FILE = 81;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private String target = "today";
    private boolean importing = false;
    private String pendingBackup = null;

    private final Runnable syncLoop = new Runnable() {
        @Override public void run() {
            syncWidget();
            handler.postDelayed(this, 1200);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readTarget(getIntent());
        MijnDagAlarmReceiver.restore(this);
        if (android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},79);
        if (acceptSharedTransfer(getIntent()) || imported() || importFile().exists()) openApp();
        else showTransferChoice(true);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readTarget(intent);
        if (acceptSharedTransfer(intent) || imported()) openApp();
    }

    private void readTarget(Intent intent) {
        if (intent == null) return;
        String value = intent.getStringExtra("target");
        if (value != null && !value.trim().isEmpty()) target = value.trim();
    }

    private boolean imported() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(IMPORT_DONE, false);
    }

    private File importFile() {
        return new File(getFilesDir(), IMPORT_FILE);
    }

    private void showTransferChoice(boolean openBrowser) {
        handler.removeCallbacks(syncLoop);
        if (webView != null) { webView.destroy(); webView = null; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = dp(24);
        root.setPadding(p, p * 2, p, p);
        root.setBackgroundColor(Color.rgb(245,245,239));

        TextView title = new TextView(this);
        title.setText("Eenmalig gegevens overzetten");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(32,37,31));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("Daarna gebruikt Mijn dag dezelfde gegevens als de Vandaag-widget.");
        info.setTextSize(16);
        info.setTextColor(Color.rgb(107,113,104));
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, dp(14), 0, dp(22));
        root.addView(info, ip);

        Button transfer = new Button(this);
        transfer.setText("Gegevens overzetten");
        transfer.setAllCaps(false);
        transfer.setOnClickListener(v -> openTransferPage());
        root.addView(transfer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        Button choose = new Button(this);
        choose.setText("Overdrachtsbestand kiezen");
        choose.setAllCaps(false);
        choose.setOnClickListener(v -> pickFile());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        cp.setMargins(0, dp(10), 0, 0);
        root.addView(choose, cp);

        setContentView(root);
        if (openBrowser) root.postDelayed(this::openTransferPage, 350);
    }

    private void openTransferPage() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TRANSFER))); }
        catch (Exception e) { Toast.makeText(this, "Kies hieronder het overdrachtsbestand.", Toast.LENGTH_LONG).show(); }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, PICK_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (saveTransfer(readUri(data.getData()))) openApp();
            else Toast.makeText(this, "Dit bestand bevat geen geldige appgegevens.", Toast.LENGTH_LONG).show();
        }
        if (requestCode == SAVE_BACKUP_FILE) {
            String backup = pendingBackup;
            pendingBackup = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || backup == null) return;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new java.io.IOException("Bestand kan niet worden geopend.");
                out.write(backup.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, "Back-up opgeslagen.", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Back-up opslaan mislukt. Je gegevens zijn niet veranderd.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean acceptSharedTransfer(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        String text = "";
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream != null) text = readUri(stream);
        if (text.isEmpty()) {
            CharSequence extra = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (extra != null) text = extra.toString();
        }
        ClipData clip = intent.getClipData();
        if (text.isEmpty() && clip != null && clip.getItemCount() > 0) {
            ClipData.Item item = clip.getItemAt(0);
            if (item.getUri() != null) text = readUri(item.getUri());
            else if (item.getText() != null) text = item.getText().toString();
        }
        return saveTransfer(text);
    }

    private String readUri(Uri uri) {
        StringBuilder out = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] b = new char[8192];
            int n;
            while ((n = r.read(b)) > 0 && out.length() < 2_000_000) out.append(b,0,n);
        } catch (Exception ignored) {}
        return out.toString();
    }

    private boolean saveTransfer(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(text);
            JSONObject entries = root.optJSONObject("entries");
            if (entries == null) return false;
            // Accepteer alleen een echt appbestand; een fout bestand mag nooit
            // de bestaande opgeslagen planning overschrijven.
            String appData = entries.optString("mijnPersoonlijkeAppV1", "");
            if (appData.isEmpty() || !appData.trim().startsWith("{")) return false;
            new JSONObject(appData);
            try (FileOutputStream out = new FileOutputStream(importFile())) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private WebResourceResponse bundledResource(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || !"schiedonr-cell.github.io".equalsIgnoreCase(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null || !path.startsWith(APP_PATH)) return null;
        String resource = path.substring(APP_PATH.length());
        if (resource.isEmpty()) resource = "index.html";
        if (resource.contains("..") || resource.contains("\\") || resource.startsWith("/")) return null;
        // Bestaande server-herinneringen mogen geen SW-bestanden blijven ophalen.
        if ("service-worker.js".equals(resource)) return null;
        String mime = "text/plain";
        if (resource.endsWith(".html")) mime = "text/html";
        else if (resource.endsWith(".js")) mime = "application/javascript";
        else if (resource.endsWith(".png")) mime = "image/png";
        else if (resource.endsWith(".webmanifest") || resource.endsWith(".json")) mime = "application/json";
        try {
            InputStream stream = getAssets().open("app/" + resource);
            return new WebResourceResponse(mime, "UTF-8", stream);
        } catch (Exception ignored) { return null; }
    }

    private void openApp() {
        handler.removeCallbacks(syncLoop);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "AndroidWidgetBridge");
        // Bundel het nieuwe scherm onder dezelfde HTTPS-origin: bestaande
        // localStorage en widgetgegevens blijven daardoor op hun plaats.
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return bundledResource(request.getUrl());
                }
            });
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return bundledResource(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("https".equalsIgnoreCase(u.getScheme()) && "schiedonr-cell.github.io".equalsIgnoreCase(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                // Een oudere webversie kan nog door een Service Worker worden
                // getoond. Verwijder alleen die cache, nooit de gebruikersdata.
                if (url.startsWith(BASE)) {
                    view.evaluateJavascript(
                        "(async()=>{try{if(!navigator.serviceWorker?.controller||sessionStorage.getItem('mijndag-native-cache-updated'))return;"
                        +"sessionStorage.setItem('mijndag-native-cache-updated','1');"
                        +"const regs=await navigator.serviceWorker.getRegistrations();await Promise.all(regs.map(r=>r.unregister()));"
                        +"if('caches'in window){const keys=await caches.keys();await Promise.all(keys.map(k=>caches.delete(k)));}"
                        +"location.reload();}catch(e){}})()", null);
                }
                if (importFile().exists() && !importing) importIntoWebView();
                else if (imported()) {
                    injectCalendarCache();
                    syncWidget();
                    startSync();
                    requestCalendarIfNeeded();
                }
            }
        });
        setContentView(webView);
        webView.loadUrl(BASE + "?nativev=1&open=" + Uri.encode(target));
    }

    private void importIntoWebView() {
        importing = true;
        try {
            StringBuilder text = new StringBuilder();
            try (FileInputStream in = new FileInputStream(importFile());
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] b = new char[8192]; int n; while ((n = r.read(b)) > 0) text.append(b,0,n);
            }
            JSONObject entries = new JSONObject(text.toString()).getJSONObject("entries");
            List<String[]> pairs = new ArrayList<>();
            // Importeer alleen de drie bekende gegevenssleutels. Zo kan een
            // overdrachtsbestand nooit andere instellingen in de WebView aanpassen.
            for (String key : new String[]{
                    "mijnPersoonlijkeAppV1",
                    "mijnPersoonlijkeAppRemindersV1",
                    "mijnPersoonlijkeAppCalendarEventsV1"}) {
                if (entries.has(key)) pairs.add(new String[]{key, entries.optString(key,"")});
            }
            // Eerst vaststellen of de app al gegevens heeft: bij een herhaalde
            // overdracht mogen die nooit stilzwijgend worden overschreven.
            webView.evaluateJavascript(
                "(function(){try{const s=localStorage.getItem('mijnPersoonlijkeAppV1');"
                  +"if(!s)return false;const data=JSON.parse(s);"
                  +"return !!(data&&typeof data==='object'&&Object.keys(data).length>0);"
                  +"}catch(e){return false;}})()",
                result -> {
                    if ("true".equals(result)) {
                        File held = new File(getFilesDir(),"onverwerkte-overdracht.json");
                        // Het originele bestand blijft behouden als hernoemen niet lukt.
                        importFile().renameTo(held);
                        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                            .putBoolean(IMPORT_DONE,true).apply();
                        importing = false;
                        Toast.makeText(this,
                            "Bestaande gegevens behouden. Nieuwe overdracht niet toegepast.",
                            Toast.LENGTH_LONG).show();
                        webView.reload();
                    } else injectNext(pairs,0);
                });
        } catch (Exception e) {
            importing = false;
            showTransferChoice(false);
        }
    }

    private void injectNext(List<String[]> pairs, int i) {
        if (i >= pairs.size()) {
            // Controleer eerst of appdata daadwerkelijk gelezen kan worden.
            // Verwijder het overdrachtsbestand pas na succesvolle controle.
            webView.evaluateJavascript(
                "(function(){try{const value=localStorage.getItem('mijnPersoonlijkeAppV1');"
                    +"return !!(value&&JSON.parse(value)&&value.length>10);}catch(e){return false;}})()",
                result -> {
                    if (!"true".equals(result)) {
                        importing = false;
                        Toast.makeText(this, "Overzetten niet bevestigd. Het overdrachtsbestand is bewaard.", Toast.LENGTH_LONG).show();
                        showTransferChoice(false);
                        return;
                    }
                    importFile().delete();
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(IMPORT_DONE, true).apply();
                    importing = false;
                    Toast.makeText(this, "Je gegevens staan erin.", Toast.LENGTH_SHORT).show();
                    webView.reload();
                });
            return;
        }
        String[] p = pairs.get(i);
        webView.evaluateJavascript("localStorage.setItem(" + JSONObject.quote(p[0]) + "," + JSONObject.quote(p[1]) + ");", v -> injectNext(pairs, i+1));
    }

    private void requestCalendarIfNeeded() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, CALENDAR_PERMISSION);
    }

    private void injectCalendarCache() {
        if (webView == null || checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        try {
            JSONArray events = CalendarData.todayEvents(this);
            JSONObject cache = new JSONObject();
            cache.put("date", SnapshotStore.todayKey());
            cache.put("savedAt", System.currentTimeMillis());
            cache.put("items", events);
            webView.evaluateJavascript("localStorage.setItem('mijnPersoonlijkeAppCalendarEventsV1'," + JSONObject.quote(cache.toString()) + ");", null);
        } catch (Exception ignored) {}
    }

    private void startSync() {
        handler.removeCallbacks(syncLoop);
        handler.post(syncLoop);
    }

    private void syncWidget() {
        if (webView == null || !imported()) return;
        try {
            webView.evaluateJavascript("(function(){try{AndroidWidgetBridge.update(localStorage.getItem('mijnPersoonlijkeAppV1')||'',localStorage.getItem('mijnPersoonlijkeAppCalendarEventsV1')||'');}catch(e){}})();", null);
        } catch (Exception ignored) {}
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_PERMISSION) { injectCalendarCache(); syncWidget(); }
    }

    @Override protected void onResume() { super.onResume(); if (webView != null && imported()) startSync(); }
    @Override protected void onPause() { syncWidget(); handler.removeCallbacks(syncLoop); super.onPause(); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private final class Bridge {
        @JavascriptInterface public void update(String stateJson, String calendarJson) {
            SnapshotStore.updateFromWebState(getApplicationContext(), stateJson, calendarJson);
        }
        @JavascriptInterface public void scheduleReminder(String id,String title,String body,double at,String target) {
            MijnDagAlarmReceiver.schedule(getApplicationContext(),id,title,body,(long)at,0);
        }
        @JavascriptInterface public void cancelReminder(String id) {
            MijnDagAlarmReceiver.cancel(getApplicationContext(),id);
        }
        @JavascriptInterface public void scheduleDailyItemReminder(String id,String title,String body,String time,String target,boolean done) {
            if(done){MijnDagAlarmReceiver.cancel(getApplicationContext(),id);return;}
            try {
                String[] parts=time.split(":");
                java.util.Calendar c=java.util.Calendar.getInstance();
                c.set(java.util.Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));
                c.set(java.util.Calendar.MINUTE,Integer.parseInt(parts[1]));
                c.set(java.util.Calendar.SECOND,0);
                c.set(java.util.Calendar.MILLISECOND,0);
                if(c.getTimeInMillis()<=System.currentTimeMillis())c.add(java.util.Calendar.DATE,1);
                MijnDagAlarmReceiver.schedule(getApplicationContext(),id,title,body,c.getTimeInMillis(),1);
            }catch(Exception ignored){}
        }
        @JavascriptInterface public void scheduleEveryDaysReminder(String id,String title,String body,double first,int days,String target) {
            MijnDagAlarmReceiver.schedule(getApplicationContext(),id,title,body,(long)first,Math.max(1,days));
        }
        @JavascriptInterface public void scheduleFocusNamed(int seconds,String name) {
            MijnDagAlarmReceiver.focus(getApplicationContext(),seconds,name);
        }
        @JavascriptInterface public void scheduleDayPlanFocus(int seconds,String name,double endsAt) {
            MijnDagAlarmReceiver.dayFocus(getApplicationContext(),Math.max(1,(int)Math.ceil((endsAt-System.currentTimeMillis())/1000.0)),name);
        }
        @JavascriptInterface public void cancelFocus() {
            MijnDagAlarmReceiver.cancelFocus(getApplicationContext());
        }
        @JavascriptInterface public void cancelDayPlanFocus() {
            MijnDagAlarmReceiver.cancelDayFocus(getApplicationContext());
        }
        @JavascriptInterface public void pauseFocus(int remaining,String name) {
            MijnDagAlarmReceiver.cancelFocus(getApplicationContext());
        }
        @JavascriptInterface public String getTodayGoogleCalendarEvents() {
            try {return CalendarData.todayEvents(MainActivity.this).toString();}
            catch(Exception e){return "[]";}
        }
        @JavascriptInterface public void saveBackup(String json) {
            // Alleen opslaan via de Android-bestandskiezer. Nooit appgegevens wissen.
            try {
                JSONObject payload=new JSONObject(json);
                JSONObject entries=payload.optJSONObject("entries");
                if (entries==null || !entries.has("mijnPersoonlijkeAppV1")) return;
                new JSONObject(entries.getString("mijnPersoonlijkeAppV1"));
                handler.post(() -> {
                    pendingBackup=json;
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    String date=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault())
                        .format(new java.util.Date());
                    i.putExtra(Intent.EXTRA_TITLE,"mijn-dag-backup-"+date+".json");
                    try{startActivityForResult(i,SAVE_BACKUP_FILE);}
                    catch(Exception e){
                        pendingBackup=null;
                        Toast.makeText(MainActivity.this,"Bestandskiezer niet beschikbaar.",Toast.LENGTH_LONG).show();
                    }
                });
            } catch(Exception ignored){}
        }
    }
}
