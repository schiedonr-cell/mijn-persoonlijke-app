package nl.mijnpersoonlijkeapp.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private int selectedBackground;
    private int selectedTransparency;
    private String selectedTextMode;
    private int selectedAccent;
    private int selectedIconSize;
    private TextView summary;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Intent intent = getIntent();
        if (intent != null) appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        selectedBackground = WidgetStyle.background(this);
        selectedTransparency = WidgetStyle.transparency(this);
        selectedTextMode = WidgetStyle.textMode(this);
        selectedAccent = WidgetStyle.accent(this);
        selectedIconSize = WidgetStyle.iconSize(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(26));
        root.setBackgroundColor(Color.rgb(245,245,239));
        scroll.addView(root);

        root.addView(heading("Widgetstijl", 27));

        TextView info = text("Kies kleur, transparantie, tekst, accent en pictogramgrootte. Deze stijl geldt voor je Mijn dag-widgets.", 14, Color.rgb(92,100,92));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(info, infoLp);

        summary = text("", 13, Color.rgb(69,98,77));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.setMargins(0, 0, 0, dp(14));
        root.addView(summary, sumLp);
        updateSummary();

        section(root, "Achtergrond");
        root.addView(option("Antraciet", () -> selectBackground(WidgetStyle.BG_ANTHRACITE)));
        root.addView(option("Zwart", () -> selectBackground(WidgetStyle.BG_BLACK)));
        root.addView(option("Donkergroen", () -> selectBackground(WidgetStyle.BG_GREEN)));
        root.addView(option("Blauwgrijs", () -> selectBackground(WidgetStyle.BG_BLUE_GREY)));
        root.addView(option("Licht", () -> selectBackground(WidgetStyle.BG_LIGHT)));

        section(root, "Transparantie");
        LinearLayout transparencyRow1 = row();
        transparencyRow1.addView(compact("25%", () -> selectTransparency(25)));
        transparencyRow1.addView(compact("50%", () -> selectTransparency(50)));
        transparencyRow1.addView(compact("75%", () -> selectTransparency(75)));
        root.addView(transparencyRow1);
        LinearLayout transparencyRow2 = row();
        transparencyRow2.addView(compact("90%", () -> selectTransparency(90)));
        transparencyRow2.addView(compact("100%", () -> selectTransparency(100)));
        root.addView(transparencyRow2);

        TextView transHint = text("100% = volledig doorzichtig. 25% = achtergrond nog duidelijk zichtbaar.", 12, Color.rgb(92,100,92));
        LinearLayout.LayoutParams thp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thp.setMargins(0, dp(6), 0, 0);
        root.addView(transHint, thp);

        section(root, "Tekstkleur");
        LinearLayout textRow = row();
        textRow.addView(compact("Automatisch", () -> selectText(WidgetStyle.TEXT_AUTO)));
        textRow.addView(compact("Licht", () -> selectText(WidgetStyle.TEXT_LIGHT)));
        textRow.addView(compact("Donker", () -> selectText(WidgetStyle.TEXT_DARK)));
        root.addView(textRow);

        section(root, "Accentkleur");
        root.addView(option("Groen", () -> selectAccent(WidgetStyle.ACCENT_GREEN)));
        root.addView(option("Blauw", () -> selectAccent(WidgetStyle.ACCENT_BLUE)));
        root.addView(option("Paars", () -> selectAccent(WidgetStyle.ACCENT_PURPLE)));
        root.addView(option("Zand", () -> selectAccent(WidgetStyle.ACCENT_SAND)));

        section(root, "Pictogramgrootte");
        LinearLayout iconRow = row();
        iconRow.addView(compact("Klein", () -> selectIconSize(WidgetStyle.ICON_SMALL)));
        iconRow.addView(compact("Middel", () -> selectIconSize(WidgetStyle.ICON_MEDIUM)));
        iconRow.addView(compact("Groot", () -> selectIconSize(WidgetStyle.ICON_LARGE)));
        iconRow.addView(compact("XL", () -> selectIconSize(WidgetStyle.ICON_XLARGE)));
        root.addView(iconRow);

        Button save = new Button(this);
        save.setText("Opslaan");
        save.setAllCaps(false);
        save.setTextSize(18);
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(Color.rgb(69,98,77));
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        saveLp.setMargins(0, dp(22), 0, 0);
        root.addView(save, saveLp);

        setContentView(scroll);
    }

    private void section(LinearLayout root, String label) {
        TextView h = heading(label, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(18), 0, dp(7));
        root.addView(h, lp);
    }

    private Button option(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private Button compact(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView heading(String value, int size) { return text(value, size, Color.rgb(32,37,31)); }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void selectBackground(int value) { selectedBackground = value; updateSummary(); }
    private void selectTransparency(int value) { selectedTransparency = value; updateSummary(); }
    private void selectText(String value) { selectedTextMode = value; updateSummary(); }
    private void selectAccent(int value) { selectedAccent = value; updateSummary(); }
    private void selectIconSize(int value) { selectedIconSize = value; updateSummary(); }

    private void updateSummary() {
        if (summary == null) return;
        String textName = WidgetStyle.TEXT_AUTO.equals(selectedTextMode) ? "automatisch" : (WidgetStyle.TEXT_LIGHT.equals(selectedTextMode) ? "licht" : "donker");
        summary.setText("Gekozen: " + backgroundName(selectedBackground) + " · " + selectedTransparency + "% transparant · tekst " + textName + " · " + accentName(selectedAccent) + " · pictogram " + iconName(selectedIconSize));
    }

    private String backgroundName(int color) {
        if (color == WidgetStyle.BG_BLACK) return "zwart";
        if (color == WidgetStyle.BG_GREEN) return "donkergroen";
        if (color == WidgetStyle.BG_BLUE_GREY) return "blauwgrijs";
        if (color == WidgetStyle.BG_LIGHT) return "licht";
        return "antraciet";
    }

    private String accentName(int color) {
        if (color == WidgetStyle.ACCENT_BLUE) return "blauw";
        if (color == WidgetStyle.ACCENT_PURPLE) return "paars";
        if (color == WidgetStyle.ACCENT_SAND) return "zand";
        return "groen";
    }

    private String iconName(int value) {
        if (value <= WidgetStyle.ICON_SMALL) return "klein";
        if (value <= WidgetStyle.ICON_MEDIUM) return "middel";
        if (value <= WidgetStyle.ICON_LARGE) return "groot";
        return "XL";
    }

    private void save() {
        WidgetStyle.setOptions(this, selectedBackground, selectedTransparency, selectedTextMode, selectedAccent, selectedIconSize);
        Intent result = new Intent();
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
