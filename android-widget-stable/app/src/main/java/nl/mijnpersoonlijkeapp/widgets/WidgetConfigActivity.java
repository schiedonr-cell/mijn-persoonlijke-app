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
    private int selectedBorder;
    private int selectedIconSize;
    private TextView summary;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Intent intent = getIntent();
        if (intent != null) appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        selectedBackground = WidgetStyle.background(this, appWidgetId);
        selectedTransparency = WidgetStyle.transparency(this, appWidgetId);
        selectedTextMode = WidgetStyle.textMode(this, appWidgetId);
        selectedAccent = WidgetStyle.accent(this, appWidgetId);
        selectedBorder = WidgetStyle.border(this, appWidgetId);
        selectedIconSize = WidgetStyle.iconSize(this, appWidgetId);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(26));
        root.setBackgroundColor(Color.rgb(245,245,239));
        scroll.addView(root);

        root.addView(heading("Deze widget aanpassen", 27));
        TextView info = text("Deze instellingen gelden alleen voor deze widget. Andere widgets blijven zoals ze zijn.", 14, Color.rgb(92,100,92));
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
        root.addView(option("Petrol", () -> selectBackground(WidgetStyle.BG_PETROL)));
        root.addView(option("Nachtblauw", () -> selectBackground(WidgetStyle.BG_NAVY)));
        root.addView(option("Warmbruin", () -> selectBackground(WidgetStyle.BG_WARM_BROWN)));
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

        section(root, "Tekstkleur");
        LinearLayout textRow = row();
        textRow.addView(compact("Automatisch", () -> selectText(WidgetStyle.TEXT_AUTO)));
        textRow.addView(compact("Licht", () -> selectText(WidgetStyle.TEXT_LIGHT)));
        textRow.addView(compact("Donker", () -> selectText(WidgetStyle.TEXT_DARK)));
        root.addView(textRow);

        section(root, "Pictogram-/accentkleur");
        addColorOptions(root, false);

        section(root, "Randkleur");
        root.addView(option("Zelfde als pictogramkleur", () -> selectBorder(WidgetStyle.BORDER_MATCH_ACCENT)));
        addColorOptions(root, true);

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

    private void addColorOptions(LinearLayout root, boolean border) {
        addColor(root, border, "Groen", WidgetStyle.ACCENT_GREEN);
        addColor(root, border, "Blauw", WidgetStyle.ACCENT_BLUE);
        addColor(root, border, "Lichtblauw", WidgetStyle.ACCENT_LIGHT_BLUE);
        addColor(root, border, "Petrol / teal", WidgetStyle.ACCENT_TEAL);
        addColor(root, border, "Mint", WidgetStyle.ACCENT_MINT);
        addColor(root, border, "Saliegroen", WidgetStyle.ACCENT_SAGE);
        addColor(root, border, "Paars", WidgetStyle.ACCENT_PURPLE);
        addColor(root, border, "Mauve", WidgetStyle.ACCENT_MAUVE);
        addColor(root, border, "Roze", WidgetStyle.ACCENT_PINK);
        addColor(root, border, "Zand", WidgetStyle.ACCENT_SAND);
        addColor(root, border, "Terracotta", WidgetStyle.ACCENT_TERRACOTTA);
        addColor(root, border, "Crème", WidgetStyle.ACCENT_CREAM);
        addColor(root, border, "Wit", WidgetStyle.ACCENT_WHITE);
        addColor(root, border, "Lichtgrijs", WidgetStyle.ACCENT_LIGHT_GREY);
        addColor(root, border, "Donkergrijs", WidgetStyle.ACCENT_DARK_GREY);
        addColor(root, border, "Zwart", WidgetStyle.ACCENT_BLACK);
        addColor(root, border, "Goud (zacht)", WidgetStyle.ACCENT_GOLD);
        addColor(root, border, "Champagnegoud", WidgetStyle.ACCENT_CHAMPAGNE_GOLD);
        addColor(root, border, "Klassiek goud", WidgetStyle.ACCENT_CLASSIC_GOLD);
        addColor(root, border, "Oud goud", WidgetStyle.ACCENT_OLD_GOLD);
        addColor(root, border, "Roségoud", WidgetStyle.ACCENT_ROSE_GOLD);
        addColor(root, border, "Rood", WidgetStyle.ACCENT_RED);
        addColor(root, border, "Dieprood", WidgetStyle.ACCENT_DEEP_RED);
        addColor(root, border, "Bordeaux", WidgetStyle.ACCENT_BORDEAUX);
        addColor(root, border, "Koraalrood", WidgetStyle.ACCENT_CORAL);
    }

    private void addColor(LinearLayout root, boolean border, String name, int value) {
        root.addView(option(name, () -> { if (border) selectBorder(value); else selectAccent(value); }));
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
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }

    private void selectBackground(int value) { selectedBackground = value; updateSummary(); }
    private void selectTransparency(int value) { selectedTransparency = value; updateSummary(); }
    private void selectText(String value) { selectedTextMode = value; updateSummary(); }
    private void selectAccent(int value) { selectedAccent = value; updateSummary(); }
    private void selectBorder(int value) { selectedBorder = value; updateSummary(); }
    private void selectIconSize(int value) { selectedIconSize = value; updateSummary(); }

    private void updateSummary() {
        if (summary == null) return;
        String textName = WidgetStyle.TEXT_AUTO.equals(selectedTextMode) ? "automatisch" : (WidgetStyle.TEXT_LIGHT.equals(selectedTextMode) ? "licht" : "donker");
        String borderName = selectedBorder == WidgetStyle.BORDER_MATCH_ACCENT ? "zelfde als pictogram" : accentName(selectedBorder);
        summary.setText("Gekozen: " + backgroundName(selectedBackground) + " · " + selectedTransparency + "% transparant · tekst " + textName + " · pictogram " + accentName(selectedAccent) + " · rand " + borderName + " · grootte " + iconName(selectedIconSize));
    }

    private String backgroundName(int color) {
        if (color == WidgetStyle.BG_BLACK) return "zwart";
        if (color == WidgetStyle.BG_GREEN) return "donkergroen";
        if (color == WidgetStyle.BG_BLUE_GREY) return "blauwgrijs";
        if (color == WidgetStyle.BG_PETROL) return "petrol";
        if (color == WidgetStyle.BG_NAVY) return "nachtblauw";
        if (color == WidgetStyle.BG_WARM_BROWN) return "warmbruin";
        if (color == WidgetStyle.BG_LIGHT) return "licht";
        return "antraciet";
    }

    private String accentName(int color) {
        if (color == WidgetStyle.ACCENT_BLUE) return "blauw";
        if (color == WidgetStyle.ACCENT_LIGHT_BLUE) return "lichtblauw";
        if (color == WidgetStyle.ACCENT_TEAL) return "petrol/teal";
        if (color == WidgetStyle.ACCENT_MINT) return "mint";
        if (color == WidgetStyle.ACCENT_SAGE) return "saliegroen";
        if (color == WidgetStyle.ACCENT_PURPLE) return "paars";
        if (color == WidgetStyle.ACCENT_MAUVE) return "mauve";
        if (color == WidgetStyle.ACCENT_SAND) return "zand";
        if (color == WidgetStyle.ACCENT_GOLD) return "goud (zacht)";
        if (color == WidgetStyle.ACCENT_CHAMPAGNE_GOLD) return "champagnegoud";
        if (color == WidgetStyle.ACCENT_CLASSIC_GOLD) return "klassiek goud";
        if (color == WidgetStyle.ACCENT_OLD_GOLD) return "oud goud";
        if (color == WidgetStyle.ACCENT_ROSE_GOLD) return "roségoud";
        if (color == WidgetStyle.ACCENT_TERRACOTTA) return "terracotta";
        if (color == WidgetStyle.ACCENT_PINK) return "roze";
        if (color == WidgetStyle.ACCENT_WHITE) return "wit";
        if (color == WidgetStyle.ACCENT_CREAM) return "crème";
        if (color == WidgetStyle.ACCENT_LIGHT_GREY) return "lichtgrijs";
        if (color == WidgetStyle.ACCENT_DARK_GREY) return "donkergrijs";
        if (color == WidgetStyle.ACCENT_BLACK) return "zwart";
        if (color == WidgetStyle.ACCENT_RED) return "rood";
        if (color == WidgetStyle.ACCENT_DEEP_RED) return "dieprood";
        if (color == WidgetStyle.ACCENT_BORDEAUX) return "bordeaux";
        if (color == WidgetStyle.ACCENT_CORAL) return "koraalrood";
        return "groen";
    }

    private String iconName(int value) {
        if (value <= WidgetStyle.ICON_SMALL) return "klein";
        if (value <= WidgetStyle.ICON_MEDIUM) return "middel";
        if (value <= WidgetStyle.ICON_LARGE) return "groot";
        return "XL";
    }

    private void save() {
        WidgetStyle.setOptions(this, appWidgetId, selectedBackground, selectedTransparency, selectedTextMode, selectedAccent, selectedBorder, selectedIconSize);
        Intent result = new Intent();
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
