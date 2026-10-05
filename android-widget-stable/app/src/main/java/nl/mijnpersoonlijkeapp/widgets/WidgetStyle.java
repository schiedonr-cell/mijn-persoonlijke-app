package nl.mijnpersoonlijkeapp.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;

final class WidgetStyle {
    static final String TEXT_AUTO = "auto";
    static final String TEXT_LIGHT = "light";
    static final String TEXT_DARK = "dark";

    static final int BG_ANTHRACITE = Color.rgb(39, 44, 42);
    static final int BG_BLACK = Color.rgb(17, 19, 18);
    static final int BG_GREEN = Color.rgb(48, 67, 57);
    static final int BG_BLUE_GREY = Color.rgb(52, 62, 72);
    static final int BG_LIGHT = Color.rgb(242, 242, 236);

    static final int ACCENT_GREEN = Color.rgb(131, 181, 146);
    static final int ACCENT_BLUE = Color.rgb(126, 170, 207);
    static final int ACCENT_PURPLE = Color.rgb(177, 148, 201);
    static final int ACCENT_SAND = Color.rgb(210, 176, 126);

    private static final String PREFS = "widget_style_prefs";
    private static final String KEY_BG = "background_color";
    private static final String KEY_OPACITY = "background_opacity";
    private static final String KEY_TEXT = "text_mode";
    private static final String KEY_ACCENT = "accent_color";

    private WidgetStyle() {}

    static int background(Context context) {
        SharedPreferences p = prefs(context);
        if (p.contains(KEY_BG)) return p.getInt(KEY_BG, BG_ANTHRACITE);
        String old = p.getString("style", "light");
        if ("dark".equals(old)) return BG_ANTHRACITE;
        if ("transparent".equals(old)) return BG_LIGHT;
        return BG_LIGHT;
    }

    static int opacity(Context context) {
        SharedPreferences p = prefs(context);
        if (p.contains(KEY_OPACITY)) return clampOpacity(p.getInt(KEY_OPACITY, 80));
        String old = p.getString("style", "light");
        return "transparent".equals(old) ? 60 : 100;
    }

    static String textMode(Context context) {
        return prefs(context).getString(KEY_TEXT, TEXT_AUTO);
    }

    static int accent(Context context) {
        return prefs(context).getInt(KEY_ACCENT, ACCENT_GREEN);
    }

    static void setOptions(Context context, int background, int opacity, String textMode, int accent) {
        if (!TEXT_LIGHT.equals(textMode) && !TEXT_DARK.equals(textMode)) textMode = TEXT_AUTO;
        prefs(context).edit()
                .putInt(KEY_BG, background)
                .putInt(KEY_OPACITY, clampOpacity(opacity))
                .putString(KEY_TEXT, textMode)
                .putInt(KEY_ACCENT, accent)
                .apply();
        refreshAll(context);
    }

    static void applyQuick(RemoteViews v, Context context) {
        int base = background(context);
        int opacity = opacity(context);
        boolean lightText = useLightText(context, base);
        int text = lightText ? Color.rgb(248, 249, 247) : Color.rgb(31, 36, 32);
        int root = withOpacity(base, opacity);
        int buttonBase = blend(base, lightText ? Color.WHITE : Color.BLACK, lightText ? 0.12f : 0.06f);
        int button = withOpacity(buttonBase, Math.min(100, opacity + 12));

        setRoundedBackground(v, R.id.widget_root, root,
                lightText ? R.drawable.widget_bg_dark : (opacity < 90 ? R.drawable.widget_bg_transparent : R.drawable.widget_bg));
        setText(v, R.id.widget_title, text);

        int[] neutral = new int[]{R.id.btn_today, R.id.btn_food, R.id.btn_projects, R.id.btn_notes};
        for (int id : neutral) {
            setRoundedBackground(v, id, button,
                    lightText ? R.drawable.button_secondary_bg_dark : (opacity < 90 ? R.drawable.button_secondary_bg_transparent : R.drawable.button_secondary_bg));
            setText(v, id, text);
        }
    }

    static void applyToday(RemoteViews v, Context context) {
        int base = background(context);
        int opacity = opacity(context);
        boolean lightText = useLightText(context, base);
        int text = lightText ? Color.rgb(248, 249, 247) : Color.rgb(31, 36, 32);
        int muted = lightText ? Color.rgb(202, 208, 203) : Color.rgb(83, 91, 84);
        int accent = accent(context);
        int divider = Color.argb(lightText ? 110 : 85,
                Color.red(accent), Color.green(accent), Color.blue(accent));

        setRoundedBackground(v, R.id.today_root, withOpacity(base, opacity),
                lightText ? R.drawable.widget_bg_dark : (opacity < 90 ? R.drawable.widget_bg_transparent : R.drawable.widget_bg));
        setText(v, R.id.today_title, text);
        setText(v, R.id.today_subtitle, muted);
        setBackgroundColor(v, R.id.today_divider, divider);

        int[] rows = new int[]{
                R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4,
                R.id.today_row_5, R.id.today_row_6, R.id.today_row_7
        };
        int[] times = new int[]{
                R.id.today_time_1, R.id.today_time_2, R.id.today_time_3, R.id.today_time_4,
                R.id.today_time_5, R.id.today_time_6, R.id.today_time_7
        };
        for (int id : rows) setText(v, id, text);
        for (int id : times) setText(v, id, accent);
        setText(v, R.id.today_empty, muted);
        setText(v, R.id.today_more, accent);
    }

    static void refreshAll(Context context) {
        refresh(context, SmallWidgetProvider.class);
        refresh(context, MediumWidgetProvider.class);
        refresh(context, LargeWidgetProvider.class);
        refresh(context, TodayWidgetProvider.class);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int clampOpacity(int value) {
        return Math.max(20, Math.min(100, value));
    }

    private static boolean useLightText(Context context, int background) {
        String mode = textMode(context);
        if (TEXT_LIGHT.equals(mode)) return true;
        if (TEXT_DARK.equals(mode)) return false;
        return Color.luminance(background) < 0.43;
    }

    private static int withOpacity(int color, int opacity) {
        return Color.argb(Math.round(255f * clampOpacity(opacity) / 100f),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blend(int color, int target, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(color) * (1f - amount) + Color.red(target) * amount);
        int g = Math.round(Color.green(color) * (1f - amount) + Color.green(target) * amount);
        int b = Math.round(Color.blue(color) * (1f - amount) + Color.blue(target) * amount);
        return Color.rgb(r, g, b);
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

    private static void setRoundedBackground(RemoteViews v, int id, int color, int fallbackDrawable) {
        try {
            v.setInt(id, "setBackgroundResource", fallbackDrawable);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                v.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color));
            }
        } catch (Exception ignored) {}
    }

    private static void setBackgroundColor(RemoteViews v, int id, int color) {
        try { v.setInt(id, "setBackgroundColor", color); } catch (Exception ignored) {}
    }

    private static void setText(RemoteViews v, int id, int color) {
        try { v.setTextColor(id, color); } catch (Exception ignored) {}
    }
}
