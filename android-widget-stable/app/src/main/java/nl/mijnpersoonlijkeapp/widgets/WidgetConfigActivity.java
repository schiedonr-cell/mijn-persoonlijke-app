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
import android.widget.TextView;

public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Intent intent = getIntent();
        if (intent != null) appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(245,245,239));

        TextView title = new TextView(this);
        title.setText("Widgetkleur");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(32,37,31));
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("Kies één rustige stijl. Deze geldt voor al je Mijn dag-widgets.");
        info.setTextSize(15);
        info.setTextColor(Color.rgb(100,108,99));
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, dp(10), 0, dp(24));
        root.addView(info, ip);

        root.addView(button("Licht", WidgetStyle.LIGHT));
        root.addView(button("Donker", WidgetStyle.DARK));
        root.addView(button("Half-transparant", WidgetStyle.TRANSPARENT));

        setContentView(root);
    }

    private Button button(String text, String style) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setOnClickListener(v -> choose(style));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.setMargins(0, dp(7), 0, dp(7));
        b.setLayoutParams(lp);
        return b;
    }

    private void choose(String style) {
        WidgetStyle.set(this, style);
        Intent result = new Intent();
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
