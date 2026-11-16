package az.rahattv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Layout helpers. Every size is given in "units": the screen is treated as
 * 960 units wide, so the layout looks the same at 720p and 1080p whatever
 * density the TV firmware reports.
 */
final class Ui {
    private Ui() {}

    static final int BG_TOP = 0xFF0B1220;
    static final int BG_BOTTOM = 0xFF132A4A;
    static final int CARD = 0xFF1A2538;
    static final int CARD_FOCUS = 0xFF24406B;
    static final int TEXT = 0xFFF1F5F9;
    static final int MUTED = 0xFF94A3B8;
    static final int ACCENT = 0xFF3D8BFF;
    static final int OK = 0xFF22C55E;
    static final int WARN = 0xFFF59E0B;
    static final int BAD = 0xFFEF4444;

    /** Horizontal / vertical safe margins (TV overscan). */
    static final float MARGIN_X = 48f;
    static final float MARGIN_Y = 27f;
    /** Usable content width in units. */
    static final float CONTENT_W = 960f - 2 * MARGIN_X;

    private static float unit = 1f;

    static void init(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        if (w <= 0) w = 1280;
        unit = w / 960f;
    }

    static int px(float units) {
        return Math.max(units > 0 ? 1 : 0, Math.round(units * unit));
    }

    static TextView text(Context c, CharSequence s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        return g;
    }

    static GradientDrawable gradient(int a, int b, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(px(radius));
        return g;
    }

    static GradientDrawable screenBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM});
    }

    static int darker(int color, float f) {
        return Color.argb(255,
                Math.min(255, (int) (Color.red(color) * f)),
                Math.min(255, (int) (Color.green(color) * f)),
                Math.min(255, (int) (Color.blue(color) * f)));
    }

    static LinearLayout.LayoutParams lp(float w, float h) {
        return new LinearLayout.LayoutParams(w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h));
    }

    static LinearLayout.LayoutParams lpMargins(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    /** Grow + white outline when focused by the remote. */
    static void focusEffect(final View v, final float radius, final int normalColor, final Drawable custom) {
        v.setFocusable(true);
        v.setClickable(true);
        final GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setCornerRadius(px(radius));
        border.setStroke(px(3), Color.WHITE);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                // tiles grow 7 %, full-width list rows only 2 % so they stay on screen
                float s = has ? (view.getWidth() > px(400) ? 1.02f : 1.07f) : 1f;
                view.animate().scaleX(s).scaleY(s).setDuration(130).start();
                view.setElevation(has ? px(10) : 0);
                if (view instanceof FrameLayout) {
                    ((FrameLayout) view).setForeground(has ? border : null);
                }
            }
        });
    }

    /** Standard full-screen page: gradient background + scrolling column with safe margins. */
    static LinearLayout page(Activity a, String title, String subtitle) {
        init(a);
        ScrollView sv = new ScrollView(a);
        sv.setBackground(screenBackground());
        sv.setFillViewport(true);
        sv.setClipChildren(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(MARGIN_X), px(MARGIN_Y), px(MARGIN_X), px(MARGIN_Y));
        col.setClipChildren(false);
        col.setClipToPadding(false);
        sv.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(text(a, title, 28, TEXT, true));
        if (subtitle != null) {
            TextView st = text(a, subtitle, 14, MUTED, false);
            st.setLineSpacing(0, 1.2f);
            col.addView(st, lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 14));
        }
        a.setContentView(sv);
        return col;
    }

    /** A focusable full-width row: optional status dot, title, detail and a right-hand hint. */
    static FrameLayout row(Context c, int dotColor, CharSequence title, CharSequence detail, CharSequence hint) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(round(CARD, 10));
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(px(16), px(12), px(16), px(12));
        if (dotColor != 0) {
            View dot = new View(c);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(dotColor);
            dot.setBackground(d);
            LinearLayout.LayoutParams dp = lp(12, 12);
            dp.rightMargin = px(14);
            h.addView(dot, dp);
        }
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        TextView tt = text(c, title, 17, TEXT, true);
        tt.setTag("title");
        v.addView(tt);
        if (detail != null) {
            TextView dt = text(c, detail, 13, MUTED, false);
            dt.setTag("detail");
            dt.setLineSpacing(0, 1.15f);
            v.addView(dt, lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 0));
        }
        h.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (hint != null) {
            TextView ht = text(c, hint, 13, ACCENT, true);
            ht.setTag("hint");
            h.addView(ht, lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 12, 0, 0, 0));
        }
        f.addView(h, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        focusEffect(f, 10, CARD, null);
        return f;
    }

    static void addRow(LinearLayout col, View row) {
        col.addView(row, lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 5));
    }

    static TextView section(Context c, String title) {
        TextView t = text(c, title, 16, MUTED, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        return t;
    }

    static ImageView image(Context c, Drawable d) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }

    static String mb(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024f * 1024 * 1024));
        }
        return (bytes / (1024 * 1024)) + " MB";
    }
}
