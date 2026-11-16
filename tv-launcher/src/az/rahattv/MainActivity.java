package az.rahattv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The new TV home screen. */
public class MainActivity extends Activity {

    private static final float GAP = 16f;
    private static final int COLS = 5;
    private static final float TILE_W = (Ui.CONTENT_W - GAP * (COLS - 1)) / COLS; // 160 units

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ScrollView scroll;
    private TextView clockTv, dateTv, statusTv;
    private LinearLayout favBox, appsBox;
    private TextView favTitle, appsTitle;
    private Quick qNetflix, qYoutube, qPlay, qBrowser, qTv;
    private View firstFocus;

    private int loadGen;
    private boolean resumed;
    private boolean reloadPending;
    private boolean boosting;
    private List<Apps.Entry> lastApps = new ArrayList<>();

    /** A big tile in the top rows. */
    private static final class Quick {
        FrameLayout root;
        ImageView icon;
        TextView title, sub;
        int glyph;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        buildUi();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        try {
            registerReceiver(packageReceiver, f);
        } catch (Throwable ignored) {
        }
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(tick);
        tick.run();
        refreshQuick();
        if (reloadPending) {
            reloadPending = false;
            reload();
        }
        watchCrashes();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Android's "X keeps stopping" window takes the focus away from us
        watchCrashes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(tick);
        handler.removeCallbacks(crashPoll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(packageReceiver);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the top.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) goTop();
    }

    @Override
    public void onBackPressed() {
        // A launcher never closes; Back just returns to the top row.
        goTop();
    }

    private void goTop() {
        if (scroll != null) scroll.smoothScrollTo(0, 0);
        if (firstFocus != null) firstFocus.requestFocus();
    }

    // ------------------------------------------------------------------ crashes of other apps

    private int crashPolls;
    private android.app.AlertDialog crashDialog;

    private void watchCrashes() {
        crashPolls = 0;
        handler.removeCallbacks(crashPoll);
        handler.postDelayed(crashPoll, 250);
    }

    private final Runnable crashPoll = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            CrashWatch.Report r = CrashWatch.poll(MainActivity.this);
            if (r != null) {
                showRepair(r.pkg, r.label, r, true);
                return;
            }
            // the crash window can appear a moment after we come back
            if (++crashPolls < 8) handler.postDelayed(this, 600);
        }
    };

    /** Explains why an app does not open and offers the fixes Android allows. */
    private void showRepair(final String pkg, String label, CrashWatch.Report r, boolean justCrashed) {
        if (crashDialog != null && crashDialog.isShowing()) crashDialog.dismiss();
        String title;
        StringBuilder msg = new StringBuilder();
        if (r != null) {
            title = label + (r.frozen ? " donub qaldı" : " bağlandı (xəta)");
            msg.append(CrashWatch.advice(r));
            if (r.shortMsg != null && r.shortMsg.length() > 0) {
                msg.append("\n\nXətanın mətni (lazım olsa şəklini çəkin):\n").append(r.shortMsg);
            }
            if (!justCrashed) msg.append("\n(").append(CrashWatch.when(r.time)).append(")");
        } else {
            title = label + " açılmır?";
            CrashWatch.Report fake = new CrashWatch.Report();
            fake.pkg = pkg;
            msg.append(CrashWatch.advice(fake));
            msg.append("\n\nTətbiq çökəndə Rahat TV səbəbini avtomatik göstərəcək.");
        }
        android.app.AlertDialog.Builder b = Ui.dialog(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Düzəlt", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { Apps.appInfo(MainActivity.this, pkg); }
                })
                .setNegativeButton("Bağla", null);
        if (Apps.installed(this, Apps.PLAY_STORE)) {
            b.setNeutralButton("Yenilə", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { Apps.playStore(MainActivity.this, pkg); }
            });
        }
        try {
            crashDialog = b.show();
        } catch (Throwable ignored) {
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clockTv.setText(Sys.clock());
            dateTv.setText(Sys.date());
            refreshStatus();
            handler.postDelayed(this, 20_000);
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!resumed) {
                reloadPending = true;
                return;
            }
            handler.removeCallbacks(reloadRunnable);
            handler.postDelayed(reloadRunnable, 700);
        }
    };

    private final Runnable reloadRunnable = new Runnable() {
        @Override
        public void run() {
            reload();
        }
    };

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setBackground(Ui.screenBackground());
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.px(Ui.MARGIN_X), Ui.px(Ui.MARGIN_Y), Ui.px(Ui.MARGIN_X), Ui.px(Ui.MARGIN_Y + 20));
        scroll.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(header());

        // Row 1: the main things to watch
        col.addView(Ui.section(this, "Əsas"), sectionLp());
        LinearLayout r1 = row();
        qNetflix = quick(r1, "Netflix", Glyph.PLAY, 0xFFB91C1C, 0xFF7F1D1D, 100, new Runnable() {
            @Override public void run() { openNetflix(); }
        });
        qYoutube = quick(r1, "YouTube", Glyph.PLAY, 0xFFDC2626, 0xFF991B1B, 100, new Runnable() {
            @Override public void run() { openYoutube(); }
        });
        qPlay = quick(r1, "Play Market", Glyph.BAG, 0xFF059669, 0xFF065F46, 100, new Runnable() {
            @Override public void run() { openPlay(); }
        });
        qBrowser = quick(r1, "Brauzer", Glyph.GLOBE, 0xFF2563EB, 0xFF1E3A8A, 100, new Runnable() {
            @Override public void run() { openBrowser(); }
        });
        qTv = quick(r1, "TV / Mənbə", Glyph.TV, 0xFF7C3AED, 0xFF4C1D95, 100, new Runnable() {
            @Override public void run() { openTvSources(); }
        });
        col.addView(r1);
        firstFocus = qNetflix.root;

        // Row 2: tools
        col.addView(Ui.section(this, "Alətlər"), sectionLp());
        LinearLayout r2 = row();
        quick(r2, "USB / APK", Glyph.USB, 0xFF0F766E, 0xFF134E4A, 64, new Runnable() {
            @Override public void run() { Apps.start(MainActivity.this, new Intent(MainActivity.this, UsbActivity.class)); }
        }).sub.setText("Quraşdır");
        quick(r2, "Sürətləndir", Glyph.BOLT, 0xFFD97706, 0xFF92400E, 64, new Runnable() {
            @Override public void run() { boost(); }
        }).sub.setText("RAM təmizlə");
        quick(r2, "Yoxlama", Glyph.CHECK, 0xFF0284C7, 0xFF075985, 64, new Runnable() {
            @Override public void run() { Apps.start(MainActivity.this, new Intent(MainActivity.this, CheckActivity.class)); }
        }).sub.setText("Netflix / Play");
        quick(r2, "Parametrlər", Glyph.GEAR, 0xFF475569, 0xFF1E293B, 64, new Runnable() {
            @Override public void run() { Apps.openSettings(MainActivity.this, Settings.ACTION_SETTINGS); }
        }).sub.setText("Android");
        quick(r2, "Rahat TV", Glyph.SLIDERS, 0xFF4F46E5, 0xFF312E81, 64, new Runnable() {
            @Override public void run() { Apps.start(MainActivity.this, new Intent(MainActivity.this, SettingsActivity.class)); }
        }).sub.setText("Ayarlar");
        col.addView(r2);

        favTitle = Ui.section(this, "Sevimlilər");
        favTitle.setVisibility(View.GONE);
        col.addView(favTitle, sectionLp());
        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.VERTICAL);
        favBox.setClipChildren(false);
        col.addView(favBox);

        appsTitle = Ui.section(this, "Bütün tətbiqlər");
        col.addView(appsTitle, sectionLp());
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        appsBox.setClipChildren(false);
        appsBox.addView(Ui.text(this, "Yüklənir…", 15, Ui.MUTED, false));
        col.addView(appsBox);

        TextView hint = Ui.text(this, "OK — aç   ·   OK-nu basılı saxla və ya MENU — sevimlilər, gizlət, sil, keşi təmizlə",
                12, Ui.MUTED, false);
        col.addView(hint, Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 0, 0));

        setContentView(scroll);
        firstFocus.post(new Runnable() {
            @Override public void run() { firstFocus.requestFocus(); }
        });
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, "Rahat TV", 26, Ui.TEXT, true));
        statusTv = Ui.text(this, "", 13, Ui.MUTED, false);
        left.addView(statusTv, Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 0, 0));
        h.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clockTv = Ui.text(this, Sys.clock(), 34, Ui.TEXT, true);
        clockTv.setGravity(Gravity.END);
        dateTv = Ui.text(this, Sys.date(), 13, Ui.MUTED, false);
        dateTv.setGravity(Gravity.END);
        right.addView(clockTv, Ui.lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));
        right.addView(dateTv, Ui.lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 0));
        h.addView(right);
        return h;
    }

    private LinearLayout.LayoutParams sectionLp() {
        return Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 10);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private void addToRow(LinearLayout row, View v, float w, float h) {
        LinearLayout.LayoutParams p = Ui.lp(w, h);
        if (row.getChildCount() > 0) p.leftMargin = Ui.px(GAP);
        row.addView(v, p);
    }

    private Quick quick(LinearLayout row, String title, int glyph, int c1, int c2, float height, final Runnable action) {
        Quick q = new Quick();
        q.glyph = glyph;
        q.root = new FrameLayout(this);
        q.root.setBackground(Ui.gradient(c1, c2, 12));
        boolean tall = height >= 90;

        q.icon = Ui.image(this, new Glyph(glyph, 0xFFFFFFFF));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        q.title = Ui.text(this, title, tall ? 18 : 14, 0xFFFFFFFF, true);
        q.title.setSingleLine(true);
        q.title.setEllipsize(TextUtils.TruncateAt.END);
        q.sub = Ui.text(this, "", tall ? 12 : 11, 0xCCFFFFFF, false);
        q.sub.setSingleLine(true);
        q.sub.setEllipsize(TextUtils.TruncateAt.END);
        // explicit MATCH_PARENT inside a parent of known width: a wrap_content parent
        // would squeeze the title down to the subtitle's width ("Par…")
        texts.addView(q.title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        texts.addView(q.sub, Ui.lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 3, 0, 0));

        if (tall) {
            // icon pinned to the top, texts to the bottom: nothing can be pushed out of the tile
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.px(32), Ui.px(32), Gravity.TOP | Gravity.START);
            ip.setMargins(Ui.px(14), Ui.px(12), 0, 0);
            q.root.addView(q.icon, ip);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            tp.setMargins(Ui.px(14), 0, Ui.px(10), Ui.px(10));
            q.root.addView(texts, tp);
        } else {
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            inner.setPadding(Ui.px(12), 0, Ui.px(8), 0);
            LinearLayout.LayoutParams ip = Ui.lp(26, 26);
            ip.rightMargin = Ui.px(9);
            inner.addView(q.icon, ip);
            inner.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            q.root.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        q.root.setContentDescription(title);
        Ui.focusEffect(q.root, 12, c1, null);
        q.root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { safe(action); }
        });
        addToRow(row, q.root, TILE_W, height);
        return q;
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Apps.toast(this, "Xəta baş verdi, yenidən cəhd edin");
        }
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        StringBuilder s = new StringBuilder();
        boolean warn = false;
        String net = Sys.network(this);
        if (net == null) {
            s.append("İnternet yoxdur");
            warn = true;
        } else {
            s.append("İnternet: ").append(net);
        }
        long[] ram = Sys.ram(this);
        if (ram[1] > 0) s.append("   ·   RAM: ").append(Ui.mb(ram[0])).append(" boş / ").append(Ui.mb(ram[1]));
        long[] st = Sys.storage();
        if (st[1] > 0) s.append("   ·   Yaddaş: ").append(Ui.mb(st[0])).append(" boş");
        if (Sys.clockWrong()) {
            s.append("   ·   SAAT SƏHVDİR — Yoxlama bölməsindən düzəldin");
            warn = true;
        }
        statusTv.setText(s);
        statusTv.setTextColor(warn ? Ui.WARN : Ui.MUTED);
    }

    private void refreshQuick() {
        setQuickState(qNetflix, Apps.firstLaunchable(this, Apps.NETFLIX));
        setQuickState(qYoutube, Apps.firstLaunchable(this, Apps.YOUTUBE));
        String play = Apps.launchIntent(this, Apps.PLAY_STORE) != null ? Apps.PLAY_STORE : null;
        setQuickState(qPlay, play);
        qBrowser.sub.setText("İnternet");
        List<ResolveInfo> homes = Apps.otherHomes(this);
        qTv.sub.setText(homes.isEmpty() ? "HDMI, antena" : "Köhnə menyu, HDMI");
    }

    private void setQuickState(Quick q, String pkg) {
        Drawable icon = null;
        if (pkg != null) {
            try {
                icon = getPackageManager().getApplicationIcon(pkg);
            } catch (Throwable ignored) {
            }
        }
        q.icon.setImageDrawable(icon != null ? icon : new Glyph(q.glyph, 0xFFFFFFFF));
        q.sub.setText(pkg != null ? "Aç" : "Quraşdırılmayıb");
    }

    // ------------------------------------------------------------------ apps

    private void reload() {
        final int gen = ++loadGen;
        final Set<String> hidden = Prefs.hidden(this);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Apps.Entry> list;
                try {
                    list = Apps.load(app, hidden);
                } catch (Throwable t) {
                    list = new ArrayList<>();
                }
                final List<Apps.Entry> result = list;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen || isFinishing() || isDestroyed()) return;
                        lastApps = result;
                        render();
                    }
                });
            }
        }, "rahattv-apps").start();
    }

    private void render() {
        // remember which app tile had focus so the remote does not "jump" after a refresh
        String focusedKey = null;
        View cur = getCurrentFocus();
        if (cur != null && cur.getTag() instanceof String && isInside(cur, appsBox, favBox)) {
            focusedKey = (String) cur.getTag();
        }

        Map<String, Apps.Entry> byPkg = new HashMap<>();
        for (Apps.Entry e : lastApps) if (!byPkg.containsKey(e.pkg)) byPkg.put(e.pkg, e);

        List<View> favTiles = new ArrayList<>();
        for (String p : Prefs.favorites(this)) {
            Apps.Entry e = byPkg.get(p);
            if (e != null) favTiles.add(appTile(e, "fav:"));
        }
        favBox.removeAllViews();
        if (!favTiles.isEmpty()) grid(favBox, favTiles);
        favTitle.setVisibility(favTiles.isEmpty() ? View.GONE : View.VISIBLE);
        favBox.setVisibility(favTiles.isEmpty() ? View.GONE : View.VISIBLE);

        List<View> tiles = new ArrayList<>();
        for (Apps.Entry e : lastApps) tiles.add(appTile(e, "app:"));
        appsBox.removeAllViews();
        appsTitle.setText("Bütün tətbiqlər (" + lastApps.size() + ")");
        if (tiles.isEmpty()) {
            appsBox.addView(Ui.text(this, "Tətbiq tapılmadı", 15, Ui.MUTED, false));
        } else {
            grid(appsBox, tiles);
        }

        if (focusedKey != null) {
            View again = scroll.findViewWithTag(focusedKey);
            if (again != null) again.requestFocus(); else firstFocus.requestFocus();
        }
        refreshQuick();
    }

    private static boolean isInside(View v, View a, View b) {
        Object p = v.getParent();
        while (p instanceof View) {
            if (p == a || p == b) return true;
            p = ((View) p).getParent();
        }
        return false;
    }

    private void grid(LinearLayout box, List<View> tiles) {
        LinearLayout r = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLS == 0) {
                r = row();
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rp.topMargin = Ui.px(GAP);
                box.addView(r, rp);
            }
            addToRow(r, tiles.get(i), TILE_W, 90);
        }
    }

    private View appTile(final Apps.Entry e, String tagPrefix) {
        FrameLayout t = new FrameLayout(this);
        t.setTag(tagPrefix + e.cn.flattenToShortString());
        t.setContentDescription(e.label);
        if (e.banner) {
            t.setBackground(Ui.round(0xFF000000, 10));
            t.setClipToOutline(true);
            ImageView iv = Ui.image(this, e.art);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            t.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            t.setBackground(Ui.gradient(Ui.darker(e.color, 1.25f), e.color, 10));
            LinearLayout v = new LinearLayout(this);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER);
            v.setPadding(Ui.px(8), Ui.px(8), Ui.px(8), Ui.px(8));
            v.addView(Ui.image(this, e.art), Ui.lp(42, 42));
            TextView l = Ui.text(this, e.label, 13, 0xFFFFFFFF, true);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            l.setGravity(Gravity.CENTER);
            v.addView(l, Ui.lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
            t.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        Ui.focusEffect(t, 10, e.color, null);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Apps.launch(MainActivity.this, e.cn, e.leanback); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { appMenu(e); return true; }
        });
        t.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent ev) {
                if (keyCode == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_UP) {
                    appMenu(e);
                    return true;
                }
                return keyCode == KeyEvent.KEYCODE_MENU;
            }
        });
        return t;
    }

    private void appMenu(final Apps.Entry e) {
        final boolean fav = Prefs.isFavorite(this, e.pkg);
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Aç");
        actions.add(new Runnable() { @Override public void run() { Apps.launch(MainActivity.this, e.cn, e.leanback); } });
        labels.add(fav ? "Sevimlilərdən çıxar" : "Sevimlilərə əlavə et");
        actions.add(new Runnable() { @Override public void run() { Prefs.toggleFavorite(MainActivity.this, e.pkg); render(); } });
        if (fav) {
            labels.add("Sevimlilərdə birinci et");
            actions.add(new Runnable() {
                @Override public void run() {
                    List<String> f = Prefs.favorites(MainActivity.this);
                    f.remove(e.pkg);
                    f.add(0, e.pkg);
                    Prefs.saveFavorites(MainActivity.this, f);
                    render();
                }
            });
        }
        labels.add("Açılmır? — səbəb və təmir");
        actions.add(new Runnable() {
            @Override public void run() { showRepair(e.pkg, e.label, CrashWatch.lastFor(MainActivity.this, e.pkg), false); }
        });
        labels.add("Tətbiq məlumatı (keşi təmizlə, dayandır)");
        actions.add(new Runnable() { @Override public void run() { Apps.appInfo(MainActivity.this, e.pkg); } });
        labels.add("Bu menyudan gizlət");
        actions.add(new Runnable() {
            @Override public void run() {
                Prefs.hide(MainActivity.this, e.pkg);
                Apps.toast(MainActivity.this, "Gizlədildi. Geri qaytarmaq: Rahat TV → Ayarlar");
                reload();
            }
        });
        if (!e.system) {
            labels.add("Tətbiqi sil");
            actions.add(new Runnable() { @Override public void run() { Apps.uninstall(MainActivity.this, e.pkg); } });
        }
        Ui.dialog(this)
                .setTitle(e.label)
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which >= 0 && which < actions.size()) safe(actions.get(which));
                    }
                })
                .setNegativeButton("Bağla", null)
                .show();
    }

    // ------------------------------------------------------------------ quick actions

    /** Play Market and Netflix refuse to connect when the TV clock is wrong; catch that first. */
    private void withClockCheck(final Runnable go) {
        if (!Sys.clockWrong()) {
            go.run();
            return;
        }
        Ui.dialog(this)
                .setTitle("TV-nin saatı səhvdir")
                .setMessage("Tarix: " + Sys.date() + "\n\nSaat səhv olanda Play Market, Netflix və YouTube "
                        + "\"bağlantı xətası\" verir. Əvvəlcə tarixi düzəldin (\"Avtomatik tarix və saat\" seçin).")
                .setPositiveButton("Saatı düzəlt", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { Apps.openSettings(MainActivity.this, Settings.ACTION_DATE_SETTINGS); }
                })
                .setNegativeButton("Yenə də aç", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { safe(go); }
                })
                .show();
    }

    private void openNetflix() {
        final String pkg = Apps.firstLaunchable(this, Apps.NETFLIX);
        if (pkg != null) {
            withClockCheck(new Runnable() { @Override public void run() { Apps.launch(MainActivity.this, pkg); } });
            return;
        }
        choose("Netflix quraşdırılmayıb",
                new String[]{"Play Market-də axtar", "USB-dən quraşdır (APK fayl)", "Niyə görünmür? — Yoxlama"},
                new Runnable[]{
                        new Runnable() { @Override public void run() { Apps.playSearch(MainActivity.this, "Netflix"); } },
                        new Runnable() { @Override public void run() { Apps.start(MainActivity.this, new Intent(MainActivity.this, UsbActivity.class)); } },
                        new Runnable() { @Override public void run() { Apps.start(MainActivity.this, new Intent(MainActivity.this, CheckActivity.class)); } },
                });
    }

    private void openYoutube() {
        final String pkg = Apps.firstLaunchable(this, Apps.YOUTUBE);
        if (pkg != null) {
            withClockCheck(new Runnable() { @Override public void run() { Apps.launch(MainActivity.this, pkg); } });
            return;
        }
        choose("YouTube quraşdırılmayıb",
                new String[]{"Play Market-də axtar", "USB-dən quraşdır (APK fayl)", "Brauzerdə aç"},
                new Runnable[]{
                        new Runnable() { @Override public void run() { Apps.playSearch(MainActivity.this, "YouTube"); } },
                        new Runnable() { @Override public void run() { Apps.start(MainActivity.this, new Intent(MainActivity.this, UsbActivity.class)); } },
                        new Runnable() { @Override public void run() { Apps.openUrl(MainActivity.this, "https://www.youtube.com"); } },
                });
    }

    private void openPlay() {
        if (Apps.launchIntent(this, Apps.PLAY_STORE) != null) {
            withClockCheck(new Runnable() { @Override public void run() { Apps.launch(MainActivity.this, Apps.PLAY_STORE); } });
            return;
        }
        if (Apps.installed(this, Apps.PLAY_STORE)) {
            Ui.dialog(this)
                    .setTitle("Play Market söndürülüb")
                    .setMessage("Play Market bu TV-də var, amma deaktiv edilib. Açılan pəncərədə \"Aktiv et\" düyməsini basın.")
                    .setPositiveButton("Aç", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { Apps.appInfo(MainActivity.this, Apps.PLAY_STORE); }
                    })
                    .setNegativeButton("Bağla", null)
                    .show();
            return;
        }
        Ui.dialog(this)
                .setTitle("Play Market tapılmadı")
                .setMessage("Bu TV-də Google Play Market quraşdırılmayıb. Tətbiqləri USB fləş kartdan APK fayl kimi quraşdıra bilərsiniz.")
                .setPositiveButton("USB-dən quraşdır", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { Apps.start(MainActivity.this, new Intent(MainActivity.this, UsbActivity.class)); }
                })
                .setNegativeButton("Bağla", null)
                .show();
    }

    private void openBrowser() {
        if (Apps.start(this, new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")))) return;
        choose("Brauzer tapılmadı",
                new String[]{"Play Market-də brauzer axtar"},
                new Runnable[]{new Runnable() { @Override public void run() { Apps.playSearch(MainActivity.this, "TV browser"); } }});
    }

    private void openTvSources() {
        final List<ResolveInfo> homes = Apps.otherHomes(this);
        if (homes.isEmpty()) {
            Apps.toast(this, "Pultdakı SOURCE / INPUT düyməsini basın");
            return;
        }
        if (homes.size() == 1) {
            launchHome(homes.get(0));
            return;
        }
        String[] names = new String[homes.size()];
        Runnable[] acts = new Runnable[homes.size()];
        for (int i = 0; i < homes.size(); i++) {
            final ResolveInfo ri = homes.get(i);
            CharSequence l = null;
            try {
                l = ri.loadLabel(getPackageManager());
            } catch (Throwable ignored) {
            }
            names[i] = l != null ? l.toString() : ri.activityInfo.packageName;
            acts[i] = new Runnable() { @Override public void run() { launchHome(ri); } };
        }
        choose("Köhnə TV menyusu", names, acts);
    }

    private void launchHome(ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        if (!Apps.start(this, i)) Apps.toast(this, "Pultdakı SOURCE / INPUT düyməsini basın");
    }

    private void choose(String title, String[] names, final Runnable[] acts) {
        Ui.dialog(this)
                .setTitle(title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which >= 0 && which < acts.length) safe(acts[which]);
                    }
                })
                .setNegativeButton("Bağla", null)
                .show();
    }

    private void boost() {
        if (boosting) return;
        boosting = true;
        Apps.toast(this, "Təmizlənir…");
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long before = Sys.ram(app)[0];
                final int n = Sys.boost(app);
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                }
                final long after = Sys.ram(app)[0];
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        boosting = false;
                        long gained = after - before;
                        String msg = gained > 8L * 1024 * 1024
                                ? "Hazırdır! +" + Ui.mb(gained) + " RAM boşaldı (indi " + Ui.mb(after) + " boş)"
                                : "Hazırdır. RAM artıq yaxşı vəziyyətdədir (" + Ui.mb(after) + " boş)";
                        Apps.toast(MainActivity.this, msg);
                        refreshStatus();
                    }
                });
            }
        }, "rahattv-boost").start();
    }
}
