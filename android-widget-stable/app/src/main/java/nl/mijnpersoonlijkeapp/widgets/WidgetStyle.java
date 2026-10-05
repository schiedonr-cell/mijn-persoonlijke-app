package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

final class WidgetStyle {
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String TRANSPARENT = "transparent";

    private static final String PREFS = "widget_style_prefs";
    private static final String KEY_STYLE = "style";

    private WidgetStyle() {}

    static String get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STYLE, LIGHT);
    }

    static void set(Context context, String style) {
        if (!DARK.equals(style) && !TRANSPARENT.equals(style)) style = LIGHT;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STYLE, style).apply();
        refreshAll(context);
    }

    static void applyQuick(RemoteViews v, Context context) {
        String style = get(context);
        int rootBg = R.drawable.widget_bg;
        int buttonBg = R.drawable.button_secondary_bg;
        int text = Color.rgb(32, 37, 31);

        if (DARK.equals(style)) {
            rootBg = R.drawable.widget_bg_dark;
            buttonBg = R.drawable.button_secondary_bg_dark;
            text = Color.rgb(245, 247, 244);
        } else if (TRANSPARENT.equals(style)) {
            rootBg = R.drawable.widget_bg_transparent;
            buttonBg = R.drawable.button_secondary_bg_transparent;
            text = Color.rgb(32, 37, 31);
        }

        setBackground(v, R.id.widget_root, rootBg);
        setText(v, R.id.widget_title, text);

        int[] neutral = new int[]{R.id.btn_today, R.id.btn_food, R.id.btn_projects, R.id.btn_notes};
        for (int id : neutral) {
            setBackground(v, id, buttonBg);
            setText(v, id, text);
        }
    }

    static void applyToday(RemoteViews v, Context context) {
        String style = get(context);
        int rootBg = R.drawable.widget_bg;
        int pillBg = R.drawable.widget_pill;
        int text = Color.rgb(32, 37, 31);
        int muted = Color.rgb(107, 113, 104);
        int divider = Color.rgb(215, 221, 214);

        if (DARK.equals(style)) {
            rootBg = R.drawable.widget_bg_dark;
            pillBg = R.drawable.widget_pill_dark;
            text = Color.rgb(245, 247, 244);
            muted = Color.rgb(198, 203, 197);
            divider = Color.rgb(84, 92, 85);
        } else if (TRANSPARENT.equals(style)) {
            rootBg = R.drawable.widget_bg_transparent;
            pillBg = R.drawable.widget_pill_transparent;
            text = Color.rgb(32, 37, 31);
            muted = Color.rgb(84, 91, 83);
            divider = Color.argb(110, 100, 110, 101);
        }

        setBackground(v, R.id.today_root, rootBg);
        setText(v, R.id.today_title, text);
        setText(v, R.id.today_subtitle, muted);
        setBackground(v, R.id.today_energy, pillBg);
        setText(v, R.id.today_energy, DARK.equals(style) ? Color.rgb(222, 235, 225) : Color.rgb(69, 98, 77));
        setBackgroundColor(v, R.id.today_divider, divider);

        int[] rows = new int[]{
                R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4,
                R.id.today_row_5, R.id.today_row_6, R.id.today_row_7
        };
        for (int id : rows) setText(v, id, text);
        setText(v, R.id.today_empty, muted);
        setText(v, R.id.today_more, DARK.equals(style) ? Color.rgb(177, 212, 187) : Color.rgb(69, 98, 77));
    }

    static void refreshAll(Context context) {
        refresh(context, SmallWidgetProvider.class);
        refresh(context, MediumWidgetProvider.class);
        refresh(context, LargeWidgetProvider.class);
        refresh(context, TodayWidgetProvider.class);
    }

    private static void refresh(Context context, Class<?> provider) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, provider);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids == null || ids.length == 0) return;
            Intent intent = new Intent(context, provider);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private static void setBackground(RemoteViews v, int id, int drawable) {
        try { v.setInt(id, "setBackgroundResource", drawable); } catch (Exception ignored) {}
    }

    private static void setBackgroundColor(RemoteViews v, int id, int color) {
        try { v.setInt(id, "setBackgroundColor", color); } catch (Exception ignored) {}
    }

    private static void setText(RemoteViews v, int id, int color) {
        try { v.setTextColor(id, color); } catch (Exception ignored) {}
    }
}
