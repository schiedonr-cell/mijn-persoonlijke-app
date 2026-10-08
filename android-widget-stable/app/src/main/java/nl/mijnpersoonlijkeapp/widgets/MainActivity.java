package nl.mijnpersoonlijkeapp.widgets;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    private static final String TRANSFER = BASE + "transfer.html";
    private static final String PREFS = "native_app_state";
    private static final String IMPORT_DONE = "import_done";
    private static final String IMPORT_FILE = "pending-import.json";
    private static final String WEB_CACHE_RESET_V45 = "web_cache_reset_v45";
    private static final int PICK_FILE = 77;
    private static final int CALENDAR_PERMISSION = 78;
    private static final int NOTIFICATION_PERMISSION = 79;
    private static final int SPEECH_REQUEST = 80;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private String target = "today";
    private boolean importing = false;

    private final Runnable syncLoop = new Runnable() {
        @Override public void run() {
            syncWidget();
            handler.postDelayed(this, 1200);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReminderReceiver.ensureChannel(this);
        NativeAlarmScheduler.rescheduleAll(this);
        readTarget(getIntent());
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
            return;
        }
        if (requestCode == SPEECH_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> list = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                String text = list != null && !list.isEmpty() ? list.get(0) : "";
                deliverSpeechResult(text);
            } else {
                deliverSpeechResult("");
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
            if (entries == null || !entries.has("mijnPersoonlijkeAppV1")) return false;
            try (FileOutputStream out = new FileOutputStream(importFile())) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private void openApp() {
        handler.removeCallbacks(syncLoop);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.clearCache(true);
        webView.addJavascriptInterface(new Bridge(), "AndroidWidgetBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("https".equalsIgnoreCase(u.getScheme()) && "schiedonr-cell.github.io".equalsIgnoreCase(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (importFile().exists() && !importing) importIntoWebView();
                else if (imported()) {
                    if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(WEB_CACHE_RESET_V45, false)) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(WEB_CACHE_RESET_V45, true).apply();
                        String reloadUrl = BASE + "?open=" + Uri.encode(target) + "&nativev=45&fresh=1";
                        String cleanJs =
                            "(function(){" +
                            "try{if('serviceWorker' in navigator){navigator.serviceWorker.getRegistrations().then(function(rs){return Promise.all(rs.map(function(r){return r.unregister();}));}).catch(function(){});}}catch(e){}" +
                            "try{if(window.caches){caches.keys().then(function(keys){return Promise.all(keys.map(function(k){return caches.delete(k);}));}).catch(function(){});}}catch(e){}" +
                            "setTimeout(function(){location.replace(" + JSONObject.quote(reloadUrl) + ");},700);" +
                            "})();";
                        try { webView.evaluateJavascript(cleanJs, null); } catch (Exception ignored) {}
                        return;
                    }
                    injectCalendarCache();
                    installNativeHooks();
                    ensureCurrentNotesModule();
                    syncWidget();
                    startSync();
                    requestCalendarIfNeeded();
                }
            }
        });
        setContentView(webView);
        webView.loadUrl(BASE + "?open=" + Uri.encode(target) + "&nativev=45");
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
            Iterator<String> keys = entries.keys();
            while (keys.hasNext()) { String k = keys.next(); pairs.add(new String[]{k, entries.optString(k,"")}); }
            injectNext(pairs, 0);
        } catch (Exception e) {
            importing = false;
            showTransferChoice(false);
        }
    }

    private void injectNext(List<String[]> pairs, int i) {
        if (i >= pairs.size()) {
            importFile().delete();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(IMPORT_DONE, true).apply();
            importing = false;
            Toast.makeText(this, "Je gegevens staan erin.", Toast.LENGTH_SHORT).show();
            webView.reload();
            return;
        }
        String[] p = pairs.get(i);
        webView.evaluateJavascript("localStorage.setItem(" + JSONObject.quote(p[0]) + "," + JSONObject.quote(p[1]) + ");", v -> injectNext(pairs, i+1));
    }

    private void ensureCurrentNotesModule() {
        if (webView == null) return;
        String js =
            "(function(){" +
            "if(window.__mijnDagNotesV2Loaded){try{window.MijnDagNotes&&window.MijnDagNotes.refresh&&window.MijnDagNotes.refresh();}catch(_){ }return;}" +
            "var old=document.getElementById('mijnDagNotesV2Script');if(old)old.remove();" +
            "var sc=document.createElement('script');sc.id='mijnDagNotesV2Script';" +
            "sc.src='https://schiedonr-cell.github.io/mijn-persoonlijke-app/notes-v2.js?v=45';" +
            "sc.async=false;document.head.appendChild(sc);" +
            "})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void installNativeHooks() {
        if (webView == null) return;
        String js =
            "(function(){" +
            "if(window.__mijnDagNativeHooks)return;window.__mijnDagNativeHooks=true;" +
            "function nativeReminderPatch(){" +
            "var box=document.getElementById('reminderSupportStatus');" +
            "if(box)box.innerHTML='<strong>Android-meldingen beschikbaar</strong><span>Deze herinneringen lopen via de geïnstalleerde app en werken ook als je scherm uit staat.</span>';" +
            "var t=document.getElementById('reminderEnabledToggle'),test=document.getElementById('testReminderButton');if(t)t.disabled=false;if(test)test.disabled=false;" +
            "var note=document.querySelector('#reminderModal .reminder-note');if(note)note.textContent='Deze tijden worden als echte Android-herinneringen ingesteld.';" +
            "}" +
            "function parseClock(text){var m=String(text||'').trim().match(/^(\\d+):(\\d{2})$/);if(!m)return 0;return Number(m[1])*60+Number(m[2]);}" +
            "document.addEventListener('click',function(e){" +
            "var open=e.target.closest&&e.target.closest('#openReminderButton');if(open){setTimeout(nativeReminderPatch,0);return;}" +
            "var save=e.target.closest&&e.target.closest('#saveReminderButton');if(save){e.preventDefault();e.stopImmediatePropagation();" +
            "var on=!!document.getElementById('reminderEnabledToggle')?.checked;" +
            "var move=document.getElementById('moveReminderTime')?.value||'16:30',relax=document.getElementById('relaxReminderTime')?.value||'21:00',close=document.getElementById('closeReminderTime')?.value||'22:30';" +
            "try{localStorage.setItem('mijnPersoonlijkeAppRemindersV1',JSON.stringify({enabled:on,moveTime:move,relaxTime:relax,closeTime:close,subscribed:false}));}catch(_){}" +
            "AndroidWidgetBridge.setCoreReminders(on,move,relax,close);nativeReminderPatch();return;}" +
            "var test=e.target.closest&&e.target.closest('#testReminderButton');if(test){e.preventDefault();e.stopImmediatePropagation();AndroidWidgetBridge.testNativeNotification();return;}" +
            "var mic=e.target.closest&&e.target.closest('#dumpMicButton');if(mic){e.preventDefault();e.stopImmediatePropagation();AndroidWidgetBridge.startSpeech();return;}" +
            "var fs=e.target.closest&&e.target.closest('#focusStartButton');if(fs){var txt=(fs.textContent||'').toLowerCase();if(txt.indexOf('start')>=0){var sec=parseClock(document.getElementById('focusClockText')?.textContent);if(sec>0)AndroidWidgetBridge.scheduleFocus(sec);}else{AndroidWidgetBridge.cancelFocus();}return;}" +
            "if(e.target.closest&&e.target.closest('[data-focus-duration],#customFocusButton,[data-focus-done],#closeDayButton'))AndroidWidgetBridge.cancelFocus();" +
            "},true);" +
            "nativeReminderPatch();" +
            "var hint=document.getElementById('speechHint');if(hint)hint.textContent='Tik om te spreken, of typ hieronder.';" +
            "})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void startSpeechRecognition() {
        try {
            Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "nl-NL");
            i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Zeg je gedachte");
            startActivityForResult(i, SPEECH_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "Spraakherkenning is niet beschikbaar op deze telefoon.", Toast.LENGTH_LONG).show();
        }
    }

    private void deliverSpeechResult(String text) {
        if (webView == null) return;
        String safe = JSONObject.quote(text == null ? "" : text);
        String js = "(function(){var input=document.getElementById('dumpTextInput');if(input)input.value=" + safe + ";" +
                "if(" + safe + "){document.getElementById('addDumpButton')?.click();}" +
                "var hint=document.getElementById('speechHint');if(hint)hint.textContent='Tik om te spreken, of typ hieronder.';})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        }
    }

    private int[] parseHourMinute(String value, int defHour, int defMinute) {
        try {
            String[] p = value.split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) return new int[]{h,m};
        } catch (Exception ignored) {}
        return new int[]{defHour,defMinute};
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
            String js = "(function(){try{" +
                    "var liveHouse='';" +
                    "var box=document.getElementById('householdList');" +
                    "if(box){" +
                    "var items=[];" +
                    "var nodes=box.querySelectorAll('.household-name');" +
                    "for(var i=0;i<nodes.length;i++){var name=(nodes[i].textContent||'').trim();if(name)items.push({name:name});}" +
                    "liveHouse=JSON.stringify(items);" +
                    "}else if(typeof householdVisibleToday==='function'){" +
                    "var list=householdVisibleToday();" +
                    "if(Array.isArray(list)){" +
                    "if(typeof householdDoneToday==='function')list=list.filter(function(x){return !householdDoneToday(x);});" +
                    "liveHouse=JSON.stringify(list.map(function(x){return {name:(x&&x.name)||'Huishouden'};}));" +
                    "}}" +
                    "AndroidWidgetBridge.update(localStorage.getItem('mijnPersoonlijkeAppV1')||'',localStorage.getItem('mijnPersoonlijkeAppCalendarEventsV1')||'',liveHouse);" +
                    "}catch(e){try{AndroidWidgetBridge.update(localStorage.getItem('mijnPersoonlijkeAppV1')||'',localStorage.getItem('mijnPersoonlijkeAppCalendarEventsV1')||'','');}catch(_){} }})();";
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_PERMISSION) { injectCalendarCache(); syncWidget(); }
        if (requestCode == NOTIFICATION_PERMISSION && grantResults.length > 0) {
            Toast.makeText(this,
                    grantResults[0] == PackageManager.PERMISSION_GRANTED ? "Meldingen toegestaan." : "Meldingen niet toegestaan.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onResume() { super.onResume(); if (webView != null && imported()) startSync(); }
    @Override protected void onPause() { syncWidget(); handler.removeCallbacks(syncLoop); super.onPause(); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private final class Bridge {
        @JavascriptInterface public void update(String stateJson, String calendarJson, String liveHouseholdJson) {
            SnapshotStore.updateFromWebState(getApplicationContext(), stateJson, calendarJson, liveHouseholdJson);
        }

        @JavascriptInterface public String getGoogleCalendars() {
            try { return CalendarData.calendars(getApplicationContext()).toString(); }
            catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface public String getSelectedGoogleCalendarIds() {
            try { return CalendarData.selectedIdsJson(getApplicationContext()).toString(); }
            catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface public String getTodayGoogleCalendarEvents() {
            try { return CalendarData.todayEvents(getApplicationContext()).toString(); }
            catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface public void setSelectedGoogleCalendarIds(String json) {
            CalendarData.setSelectedIds(getApplicationContext(), json);
            runOnUiThread(() -> {
                injectCalendarCache();
                syncWidget();
                Toast.makeText(MainActivity.this, "Agenda-keuze opgeslagen.", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface public void setCoreReminders(boolean enabled, String move, String relax, String close) {
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                if (!enabled) {
                    NativeAlarmScheduler.cancel(getApplicationContext(), "core-move");
                    NativeAlarmScheduler.cancel(getApplicationContext(), "core-relax");
                    NativeAlarmScheduler.cancel(getApplicationContext(), "core-close");
                    Toast.makeText(MainActivity.this, "Herinneringen staan uit.", Toast.LENGTH_SHORT).show();
                    return;
                }
                int[] a = parseHourMinute(move,16,30), b = parseHourMinute(relax,21,0), d = parseHourMinute(close,22,30);
                NativeAlarmScheduler.scheduleDaily(getApplicationContext(),"core-move","Bewegen","Tijd voor je beweegmoment.",a[0],a[1],"today");
                NativeAlarmScheduler.scheduleDaily(getApplicationContext(),"core-relax","Bewust ontspannen","Tijd voor een rustig moment.",b[0],b[1],"today");
                NativeAlarmScheduler.scheduleDaily(getApplicationContext(),"core-close","Dag afsluiten","Tijd om je dag rustig af te ronden.",d[0],d[1],"today");
                Toast.makeText(MainActivity.this, "Android-herinneringen opgeslagen.", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface public void testNativeNotification() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermissionIfNeeded();
                    Toast.makeText(MainActivity.this, "Sta meldingen toe en tik daarna nog één keer op Test melding.", Toast.LENGTH_LONG).show();
                    return;
                }
                NativeAlarmScheduler.schedule(getApplicationContext(),"test-" + System.currentTimeMillis(),"Testmelding","Dit is een test van Mijn dag.",System.currentTimeMillis()+1800L,"today");
                Toast.makeText(MainActivity.this, "Testmelding komt zo.", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface public void scheduleFocus(int seconds) {
            int safe = Math.max(1, Math.min(6 * 60 * 60, seconds));
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                NativeAlarmScheduler.schedule(getApplicationContext(),"focus-active","Focus klaar","Je focusblok is afgelopen.",System.currentTimeMillis()+safe*1000L,"focus");
            });
        }

        @JavascriptInterface public void cancelFocus() {
            NativeAlarmScheduler.cancel(getApplicationContext(),"focus-active");
        }

        @JavascriptInterface public void startSpeech() {
            runOnUiThread(MainActivity.this::startSpeechRecognition);
        }

        @JavascriptInterface public void scheduleReminder(String id, String title, String body, long triggerAt, String target) {
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                NativeAlarmScheduler.schedule(getApplicationContext(), id, title, body, triggerAt, target);
            });
        }

        @JavascriptInterface public void scheduleDailyItemReminder(String id, String title, String body, String time, String target, boolean doneToday) {
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                if (doneToday) {
                    NativeAlarmScheduler.cancel(getApplicationContext(), id);
                    return;
                }
                int[] hm = parseHourMinute(time, 9, 0);
                NativeAlarmScheduler.scheduleDaily(getApplicationContext(), id, title, body, hm[0], hm[1], target);
            });
        }

        @JavascriptInterface public void scheduleEveryDaysReminder(String id, String title, String body, long firstAt, int everyDays, String target) {
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                NativeAlarmScheduler.scheduleEveryDays(getApplicationContext(), id, title, body, firstAt, everyDays, target);
            });
        }

        @JavascriptInterface public void cancelReminder(String id) {
            NativeAlarmScheduler.cancel(getApplicationContext(), id);
        }

        @JavascriptInterface public boolean nativeRemindersAvailable() {
            return true;
        }
    }
}
