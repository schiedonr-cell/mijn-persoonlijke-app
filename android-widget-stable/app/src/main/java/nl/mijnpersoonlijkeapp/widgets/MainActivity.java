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
import android.webkit.ValueCallback;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.core.content.FileProvider;
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
import java.io.OutputStream;
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
    private static final int PICK_FILE = 77;
    private static final int CALENDAR_PERMISSION = 78;
    private static final int NOTIFICATION_PERMISSION = 79;
    private static final int SPEECH_REQUEST = 80;
    private static final int BACKUP_CREATE_DOCUMENT = 81;
    private static final int NOTE_FILE_CHOOSER = 82;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private String target = "today";
    private boolean importing = false;
    private String pendingBackupJson = null;
    private ValueCallback<Uri[]> pendingNoteFile;
    private Uri cameraPhotoUri;
    private List<String[]> importBlobsPairs;
    private int importBlobsIndex=-1;

    private final Runnable syncLoop = new Runnable() {
        @Override public void run() {
            applyPendingTimelineChanges();
            applyPendingQuickChanges();
            syncWidget();
            handler.postDelayed(this, 1200);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReminderReceiver.ensureChannel(this);
        NativeAlarmScheduler.clearLegacySnoozesOnce(this);
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

    private void createBackup(String backupJson) {
        try {
            JSONObject backup = new JSONObject(backupJson);
            JSONObject entries = backup.optJSONObject("entries");
            if (entries == null || entries.optString("mijnPersoonlijkeAppV1", "").isEmpty()) {
                Toast.makeText(this, "Geen appgegevens voor een back-up gevonden.", Toast.LENGTH_LONG).show();
                return;
            }
            pendingBackupJson = backupJson;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,
                    "mijn-dag-backup-" + java.time.LocalDate.now().toString() + ".json");
            startActivityForResult(intent, BACKUP_CREATE_DOCUMENT);
        } catch (Exception ex) {
            pendingBackupJson = null;
            Toast.makeText(this, "Back-up kon niet worden gestart. Verwijder nog geen apps.", Toast.LENGTH_LONG).show();
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        startActivityForResult(i, PICK_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NOTE_FILE_CHOOSER) {
            Uri[] chosen = null;
            if (resultCode == RESULT_OK) {
                if (data != null) chosen = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                if ((chosen == null || chosen.length == 0) && cameraPhotoUri != null)
                    chosen = new Uri[]{cameraPhotoUri};
            }
            if (pendingNoteFile != null) pendingNoteFile.onReceiveValue(chosen);
            pendingNoteFile = null;
            cameraPhotoUri = null;
            return;
        }
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
        if (requestCode == BACKUP_CREATE_DOCUMENT) {
            String backup = pendingBackupJson;
            pendingBackupJson = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null || backup == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                if (out == null) throw new java.io.IOException("Bestand kan niet worden geopend");
                out.write(backup.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, "Back-up opgeslagen. Bewaar dit bestand goed.", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Back-up opslaan mislukt. Probeer opnieuw; verwijder nog geen apps.", Toast.LENGTH_LONG).show();
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
            while ((n = r.read(b)) > 0) {
                // Een back-up kan foto's en kleine PDF's bevatten.
                // Nooit een afgekapt JSON-bestand proberen terug te zetten.
                if (out.length() + n > 16_000_000) return "";
                out.append(b, 0, n);
            }
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
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingNoteFile != null) pendingNoteFile.onReceiveValue(null);
                pendingNoteFile = callback;
                cameraPhotoUri = null;
                try {
                    if (params.isCaptureEnabled()) {
                        File dir = new File(getCacheDir(), "note-files");
                        if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Kan fotomap niet maken");
                        File photo = File.createTempFile("camera-", ".jpg", dir);
                        cameraPhotoUri = FileProvider.getUriForFile(MainActivity.this,
                                getPackageName() + ".notesfiles", photo);
                        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
                        camera.setClipData(ClipData.newRawUri("foto", cameraPhotoUri));
                        camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        startActivityForResult(camera, NOTE_FILE_CHOOSER);
                    } else {
                        // Kies alleen typen die door de aangeklikte webknop worden ondersteund.
                        StringBuilder accepts=new StringBuilder();
                        if(params.getAcceptTypes()!=null)for(String type:params.getAcceptTypes()){
                            if(type!=null)accepts.append(",").append(type.toLowerCase(java.util.Locale.ROOT));
                        }
                        String requested=accepts.toString();
                        ArrayList<String> types=new ArrayList<>();
                        if(requested.contains("image/"))types.add("image/*");
                        if(requested.contains("pdf"))types.add("application/pdf");
                        if(requested.contains("wordprocessingml")||requested.contains(".docx"))
                            types.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                        if(requested.contains("msword")||requested.contains(".doc"))
                            types.add("application/msword");
                        if(types.isEmpty())types.add("application/pdf");
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        // Google Drive kan een echte JPG/PNG als application/octet-stream
                        // of met een leeg MIME-type aanbieden. Met image/* verdwijnt die
                        // foto dan uit de kiezer. Alleen bij FOTO kiezen alle bestanden
                        // laten zien; de webapp controleert de daadwerkelijke afbeeldingsbytes.
                        boolean onlyPhoto=types.size()==1 && "image/*".equals(types.get(0));
                        pick.setType(onlyPhoto?"*/*":(types.size()==1?types.get(0):"*/*"));
                        if(types.size()>1)pick.putExtra(Intent.EXTRA_MIME_TYPES,types.toArray(new String[0]));
                        startActivityForResult(pick, NOTE_FILE_CHOOSER);
                    }
                } catch (Exception ex) {
                    if (pendingNoteFile != null) pendingNoteFile.onReceiveValue(null);
                    pendingNoteFile = null;
                    cameraPhotoUri = null;
                    Toast.makeText(MainActivity.this, "Bestand kiezen lukte niet.", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
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
                    String cleanJs =
                        "(function(){" +
                        "try{if('serviceWorker' in navigator){navigator.serviceWorker.getRegistrations().then(function(rs){return Promise.all(rs.map(function(r){return r.unregister();}));}).catch(function(){});}}catch(e){}" +
                        "try{if(window.caches){caches.keys().then(function(keys){return Promise.all(keys.map(function(k){return caches.delete(k);}));}).catch(function(){});}}catch(e){}" +
                        "})();";
                    try { webView.evaluateJavascript(cleanJs, null); } catch (Exception ignored) {}
                    injectCalendarCache();
                    installNativeHooks();
                    ensureCurrentNotesModule();
                    syncWidget();
                    applyPendingTimelineChanges();
                    applyPendingQuickChanges();
                    startSync();
                    requestCalendarIfNeeded();
                }
            }
        });
        setContentView(webView);
        webView.loadUrl(BASE + "?open=" + Uri.encode(target) + "&nativev=47");
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

    private void failImport() {
        // Het originele bestand niet wissen als slechts een deel is teruggezet.
        importing = false;
        importBlobsPairs = null;
        importBlobsIndex = -1;
        Toast.makeText(this,
                "Herstellen is niet gelukt. Je back-upbestand is bewaard; verwijder geen gegevens.",
                Toast.LENGTH_LONG).show();
        showTransferChoice(false);
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
        // Android WebView kan grote JavaScript-opdrachten afbreken.
        // Verstuur back-ups daarom in kleine, gecontroleerde stukken.
        injectTransferChunk(pairs, i, 0);
    }

    private void injectTransferChunk(List<String[]> pairs, int i, int offset) {
        if (!importing || webView == null) return;
        String[] pair = pairs.get(i);
        String key = pair[0], value = pair[1];
        if (offset >= value.length()) {
            if ("mijnPersoonlijkeAppNoteBlobsV1".equals(key)) {
                // Binaire bijlagen horen in IndexedDB, nooit in localStorage.
                importBlobsPairs=pairs;
                importBlobsIndex=i;
                String js =
                        "(function(){try{var v=window.__mijnDagImportBuffer||'';" +
                        "delete window.__mijnDagImportBuffer;" +
                        "if(!window.MijnDagNoteBlobStorage)return false;" +
                        "window.MijnDagNoteBlobStorage.importPayload(v)" +
                        ".then(function(){AndroidWidgetBridge.noteBlobsImported(true);}," +
                        "function(){AndroidWidgetBridge.noteBlobsImported(false);});" +
                        "return true;}" +
                        "catch(e){delete window.__mijnDagImportBuffer;return false;}})();";
                webView.evaluateJavascript(js, result -> {
                    if (!"true".equals(result)) failImport();
                });
            } else {
                String js =
                        "(function(){try{var v=window.__mijnDagImportBuffer||'';" +
                        "localStorage.setItem(" + JSONObject.quote(key) + ",v);" +
                        "var ok=localStorage.getItem(" + JSONObject.quote(key) + ")===v;" +
                        "delete window.__mijnDagImportBuffer;return ok;}" +
                        "catch(e){delete window.__mijnDagImportBuffer;return false;}})();";
                webView.evaluateJavascript(js, result -> {
                    if ("true".equals(result)) injectNext(pairs, i + 1);
                    else failImport();
                });
            }
            return;
        }
        final int end = Math.min(offset + 32768, value.length());
        final String part = JSONObject.quote(value.substring(offset, end));
        String js =
                "(function(){try{" +
                (offset == 0 ? "window.__mijnDagImportBuffer='';" : "") +
                "window.__mijnDagImportBuffer+=" + part + ";" +
                "return window.__mijnDagImportBuffer.length===" + end + ";" +
                "}catch(e){return false;}})();";
        webView.evaluateJavascript(js, result -> {
            if ("true".equals(result)) injectTransferChunk(pairs, i, end);
            else failImport();
        });
    }

    private void ensureCurrentNotesModule() {
        if (webView == null) return;
        String js =
            "(function(){" +
            "if(window.__mijnDagNotesV2Loaded){try{window.MijnDagNotes&&window.MijnDagNotes.refresh&&window.MijnDagNotes.refresh();}catch(_){ }return;}" +
            "var old=document.getElementById('mijnDagNotesV2Script');if(old)old.remove();" +
            "var sc=document.createElement('script');sc.id='mijnDagNotesV2Script';" +
            "sc.src='https://schiedonr-cell.github.io/mijn-persoonlijke-app/notes-v2.js?v=59';" +
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
            "var fs=e.target.closest&&e.target.closest('#focusStartButton');if(fs){var txt=(fs.textContent||'').toLowerCase();if(txt.indexOf('pauze')>=0){var sec=parseClock(document.getElementById('focusClockText')?.textContent);var name=document.querySelector('.focus-task-name')?.textContent||'Focus';if(sec>0)AndroidWidgetBridge.pauseFocus(sec,name);else AndroidWidgetBridge.cancelFocus();}else{var sec=parseClock(document.getElementById('focusClockText')?.textContent);var name=document.querySelector('.focus-task-name')?.textContent||'Focus';if(sec>0)AndroidWidgetBridge.scheduleFocusNamed(sec,name);}return;}" +
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

    private void applyPendingTimelineChanges() {
        if(webView==null||!imported())return;
        String pending=SnapshotStore.pending(this);
        if("[]".equals(pending))return;
        String js="if(window.applyNativeDayTimelineChanges)window.applyNativeDayTimelineChanges("+JSONObject.quote(pending)+");";
        try{webView.evaluateJavascript(js,null);}catch(Exception ignored){}
    }

    private void applyPendingQuickChanges(){
        if(webView==null||!imported())return;
        String pending=SnapshotStore.quickPending(this);
        if("[]".equals(pending))return;
        String js="if(window.applyNativeQuickChanges)window.applyNativeQuickChanges("+JSONObject.quote(pending)+");";
        try{webView.evaluateJavascript(js,null);}catch(Exception ignored){}
    }

    private void syncWidget() {
        if (webView == null || !imported()) return;
        try {
            String js = "(function(){try{" +
                    "var liveHouse='';" +
                    "if(typeof householdVisibleToday==='function'){" +
                    "var list=householdVisibleToday();" +
                    "if(Array.isArray(list)){" +
                    "if(typeof householdDoneToday==='function')list=list.filter(function(x){return !householdDoneToday(x);});" +
                    "liveHouse=JSON.stringify(list.map(function(x){return {name:(x&&x.name)||'Huishouden',time:(x&&x.time)||''};}));" +
                    "}" +
                    "}else{" +
                    "var box=document.getElementById('householdList');" +
                    "if(box){" +
                    "var items=[];" +
                    "var nodes=box.querySelectorAll('.household-name');" +
                    "for(var i=0;i<nodes.length;i++){var name=(nodes[i].textContent||'').trim();if(name)items.push({name:name,time:''});}" +
                    "liveHouse=JSON.stringify(items);" +
                    "}" +
                    "}" +
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
        @JavascriptInterface public void noteBlobsImported(boolean ok) {
            runOnUiThread(() -> {
                if(!importing || importBlobsPairs==null || importBlobsIndex<0)return;
                List<String[]> saved=importBlobsPairs;
                int next=importBlobsIndex+1;
                importBlobsPairs=null;importBlobsIndex=-1;
                if(ok)injectNext(saved,next);
                else failImport();
            });
        }

        private static final String DOCX_MIME=
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        private static final String DOC_MIME="application/msword";
        private StringBuilder noteDocumentParts;
        private int expectedNoteDocumentLength;
        @JavascriptInterface public synchronized boolean beginNoteDocument(int length) {
            if(length<1||length>11200000){noteDocumentParts=null;return false;}
            expectedNoteDocumentLength=length;
            noteDocumentParts=new StringBuilder(length);
            return true;
        }
        @JavascriptInterface public synchronized boolean addNoteDocumentPart(String part) {
            if(noteDocumentParts==null||part==null||part.length()>32768||
               noteDocumentParts.length()+part.length()>expectedNoteDocumentLength){
                noteDocumentParts=null;return false;
            }
            noteDocumentParts.append(part);
            return true;
        }
        @JavascriptInterface public boolean finishNoteDocument(String name,String mime) {
            if(!DOCX_MIME.equals(mime)&&!DOC_MIME.equals(mime))return false;
            final String value;
            synchronized(this){
                if(noteDocumentParts==null||noteDocumentParts.length()!=expectedNoteDocumentLength){
                    noteDocumentParts=null;return false;
                }
                value=noteDocumentParts.toString();
                noteDocumentParts=null;
            }
            runOnUiThread(()->showWordDocument(value,mime));
            return true;
        }

        private StringBuilder noteImageParts;
        private int expectedNoteImageLength;
        @JavascriptInterface public synchronized boolean beginNoteImage(int length){
            if(length<1||length>2500000){noteImageParts=null;return false;}
            expectedNoteImageLength=length;
            noteImageParts=new StringBuilder(length);
            return true;
        }
        @JavascriptInterface public synchronized boolean addNoteImagePart(String part){
            if(noteImageParts==null||part==null||part.length()>32768||
               noteImageParts.length()+part.length()>expectedNoteImageLength){
                noteImageParts=null;return false;
            }
            noteImageParts.append(part);
            return true;
        }
        @JavascriptInterface public boolean finishNoteImage(String mime){
            if(!"image/jpeg".equals(mime)&&!"image/png".equals(mime)&&!"image/webp".equals(mime))return false;
            final String value;
            synchronized(this){
                if(noteImageParts==null||noteImageParts.length()!=expectedNoteImageLength){
                    noteImageParts=null;return false;
                }
                value=noteImageParts.toString();
                noteImageParts=null;
            }
            runOnUiThread(()->showNoteImage(value,mime));
            return true;
        }

        private StringBuilder notePdfParts;
        private int expectedNotePdfLength;
        @JavascriptInterface public synchronized boolean beginNotePdf(int length) {
            if(length<1 || length>11200000){notePdfParts=null;return false;}
            expectedNotePdfLength=length;
            notePdfParts=new StringBuilder(length);
            return true;
        }
        @JavascriptInterface public synchronized boolean addNotePdfPart(String part){
            if(notePdfParts==null||part==null||part.length()>32768||
                notePdfParts.length()+part.length()>expectedNotePdfLength){notePdfParts=null;return false;}
            notePdfParts.append(part);
            return true;
        }
        @JavascriptInterface public boolean finishNotePdf(String name){
            final String value;
            synchronized(this){
                if(notePdfParts==null || notePdfParts.length()!=expectedNotePdfLength){
                    notePdfParts=null;return false;
                }
                value=notePdfParts.toString();
                notePdfParts=null;
            }
            runOnUiThread(()->showNotePdf(value,name));
            return true;
        }

        private StringBuilder backupParts;
        private int expectedBackupLength;

        @JavascriptInterface public synchronized boolean beginBackup(int length) {
            if (length <= 0 || length > 16_000_000) {
                backupParts = null;
                return false;
            }
            expectedBackupLength = length;
            backupParts = new StringBuilder(length);
            return true;
        }

        @JavascriptInterface public synchronized boolean addBackupPart(String part) {
            if (backupParts == null || part == null || part.length() > 32768 ||
                    backupParts.length() + part.length() > expectedBackupLength) {
                backupParts = null;
                return false;
            }
            backupParts.append(part);
            return true;
        }

        @JavascriptInterface public boolean finishBackup() {
            final String complete;
            synchronized (this) {
                if (backupParts == null || backupParts.length() != expectedBackupLength) {
                    backupParts = null;
                    return false;
                }
                complete = backupParts.toString();
                backupParts = null;
            }
            runOnUiThread(() -> createBackup(complete));
            return true;
        }

        private void showNotePdf(String base64, String name) {
                try {
                    if (base64 == null || base64.length() > 11200000) throw new java.io.IOException("PDF te groot");
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' ||
                            bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')
                        throw new java.io.IOException("Geen geldige PDF");
                    File dir = new File(getCacheDir(), "note-files");
                    if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("Kan PDF-map niet maken");
                    File pdf = File.createTempFile("notitie-", ".pdf", dir);
                    try (FileOutputStream out = new FileOutputStream(pdf)) { out.write(bytes); }
                    // Rechtstreeks ingebouwde PDF-lezer, geen herhaalde Adobe-keuze.
                    Intent open = new Intent(MainActivity.this, NotePdfViewerActivity.class);
                    open.putExtra("path",pdf.getAbsolutePath());
                    open.putExtra("name",name==null?"Document.pdf":name);
                    startActivity(open);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "PDF openen lukt niet. Controleer of je een PDF-lezer hebt.", Toast.LENGTH_LONG).show();
                }
        }
        private void showNoteImage(String base64,String mime){
            try{
                if(base64==null||base64.length()>2500000)throw new java.io.IOException("Foto te groot");
                byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
                if(bytes.length<6||bytes.length>1900000)throw new java.io.IOException("Foto te groot");
                boolean jpeg=(bytes[0]&255)==255&&(bytes[1]&255)==216;
                boolean png=(bytes[0]&255)==137&&(bytes[1]&255)==80&&(bytes[2]&255)==78&&(bytes[3]&255)==71;
                boolean webp=bytes.length>12&&bytes[0]=='R'&&bytes[1]=='I'&&
                    bytes[2]=='F'&&bytes[3]=='F'&&bytes[8]=='W'&&bytes[9]=='E';
                if(!(("image/jpeg".equals(mime)&&jpeg)||("image/png".equals(mime)&&png)||
                     ("image/webp".equals(mime)&&webp)))
                    throw new java.io.IOException("Ongeldige foto");
                File dir=new File(getCacheDir(),"note-files");
                if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Kan fotomap niet maken");
                String suffix="image/png".equals(mime)?".png":("image/webp".equals(mime)?".webp":".jpg");
                File photo=File.createTempFile("notitiefoto-",suffix,dir);
                try(FileOutputStream out=new FileOutputStream(photo)){out.write(bytes);}
                Uri uri=FileProvider.getUriForFile(MainActivity.this,
                    getPackageName()+".notesfiles",photo);
                Intent view=new Intent(Intent.ACTION_VIEW);
                view.setDataAndType(uri,mime);
                view.setClipData(ClipData.newRawUri("Foto",uri));
                view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                // Samsung: eerst rechtstreeks de Galerij; indien niet beschikbaar
                // de standaard fotoviewer gebruiken, ZONDER verplichte chooser.
                try{
                    Intent samsung=new Intent(view);
                    samsung.setPackage("com.sec.android.gallery3d");
                    startActivity(samsung);
                }catch(RuntimeException notFound){
                    // Sommige Samsung-versies laten externe content-URI's niet
                    // rechtstreeks in Gallery toe. Dan standaard foto-app.
                    startActivity(view);
                }
            }catch(Exception error){
                Toast.makeText(MainActivity.this,
                    "Foto openen in Galerij lukte niet. Probeer een fotoviewer.",Toast.LENGTH_LONG).show();
            }
        }

        private void showWordDocument(String base64,String mime){
            try{
                if(base64==null||base64.length()>11200000)throw new java.io.IOException("Document te groot");
                byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
                if(bytes.length<8||bytes.length>8*1024*1024)throw new java.io.IOException("Document te groot");
                boolean docx=DOCX_MIME.equals(mime);
                boolean isZip=(bytes[0]&255)==0x50&&(bytes[1]&255)==0x4b;
                boolean isDoc=(bytes[0]&255)==0xd0&&(bytes[1]&255)==0xcf&&
                   (bytes[2]&255)==0x11&&(bytes[3]&255)==0xe0;
                if(!(docx?isZip:isDoc))throw new java.io.IOException("Geen geldig Word-document");
                File dir=new File(getCacheDir(),"note-files");
                if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Kan opslag niet openen");
                File output=File.createTempFile("notitie-",docx?".docx":".doc",dir);
                try(FileOutputStream out=new FileOutputStream(output)){out.write(bytes);}
                Uri uri=FileProvider.getUriForFile(MainActivity.this,
                    getPackageName()+".notesfiles",output);
                Intent open=new Intent(Intent.ACTION_VIEW);
                open.setDataAndType(uri,mime);
                open.setClipData(ClipData.newRawUri("Word-document",uri));
                open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(open,"Word-document openen"));
            }catch(Exception ex){
                Toast.makeText(MainActivity.this,
                    "Document openen lukt niet. Controleer of een Word-lezer beschikbaar is.",
                    Toast.LENGTH_LONG).show();
            }
        }
        @JavascriptInterface public void openNotePdf(String base64, String name) {
            if(base64==null || base64.length()>2100000)return;
            runOnUiThread(()->showNotePdf(base64,name));
        }
        @JavascriptInterface public void saveBackup(String backupJson) {
            runOnUiThread(() -> createBackup(backupJson));
        }
        @JavascriptInterface public void ackDayTimelineUpdates(String idsJson) {
            SnapshotStore.acknowledge(getApplicationContext(),idsJson);
        }
        @JavascriptInterface public void ackQuickUpdates(String idsJson){
            SnapshotStore.acknowledgeQuick(getApplicationContext(),idsJson);
        }

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
                FocusTimerNotification.show(getApplicationContext(),safe,"Focus");
            });
        }

        @JavascriptInterface public void scheduleFocusNamed(int seconds, String name) {
            int safe = Math.max(1, Math.min(6 * 60 * 60, seconds));
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                NativeAlarmScheduler.schedule(getApplicationContext(),"focus-active","Focus klaar","Je focusblok is afgelopen.",System.currentTimeMillis()+safe*1000L,"focus");
                FocusTimerNotification.show(getApplicationContext(),safe,name);
            });
        }

        @JavascriptInterface public void pauseFocus(int seconds, String name) {
            int remaining=Math.max(0,Math.min(6*60*60,seconds));
            runOnUiThread(() -> {
                NativeAlarmScheduler.cancel(getApplicationContext(),"focus-active");
                if(remaining>0)FocusTimerNotification.showPaused(getApplicationContext(),remaining,name);
                else FocusTimerNotification.cancel(getApplicationContext());
            });
        }

        @JavascriptInterface public void cancelFocus() {
            NativeAlarmScheduler.cancel(getApplicationContext(),"focus-active");
            FocusTimerNotification.cancel(getApplicationContext());
        }

        @JavascriptInterface public void scheduleDayPlanFocus(int seconds, String name, long endsAt) {
            int safe=Math.max(1,Math.min(6*60*60,seconds));
            runOnUiThread(() -> {
                requestNotificationPermissionIfNeeded();
                NativeAlarmScheduler.cancel(getApplicationContext(),"dayplan-focus-active");
                long end=endsAt>System.currentTimeMillis()+1000L
                        ?endsAt:System.currentTimeMillis()+safe*1000L;
                NativeAlarmScheduler.schedule(getApplicationContext(),"dayplan-focus-active",
                        "Focus klaar","Je geplande tijd is voorbij.",end,"focus");
                FocusTimerNotification.showDayPlan(getApplicationContext(),
                        Math.max(1,(int)Math.ceil((end-System.currentTimeMillis())/1000.0)),name);
            });
        }

        @JavascriptInterface public void cancelDayPlanFocus() {
            NativeAlarmScheduler.cancel(getApplicationContext(),"dayplan-focus-active");
            FocusTimerNotification.cancelDayPlan(getApplicationContext());
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

        @JavascriptInterface public void cancelTimelineSnoozes(String idsJson) {
            runOnUiThread(() -> {
                try {
                    JSONArray originals = new JSONArray(idsJson);
                    for (int i = 0; i < originals.length(); i++) {
                        String id = originals.optString(i, "");
                        if (!id.isEmpty() && (id.startsWith("timed-") || id.startsWith("core-"))) {
                            // Alleen gesnoozede kopieën annuleren. Een apart ingestelde
                            // dagelijkse herinnering blijft gewoon behouden.
                            NativeAlarmScheduler.cancelSnoozes(getApplicationContext(), id);
                        }
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void clearReminderSuppression(String type, String itemId) {
            ReminderReceiver.clearSuppressionForItem(getApplicationContext(), type, itemId);
        }

        @JavascriptInterface public boolean nativeRemindersAvailable() {
            return true;
        }
    }
}
