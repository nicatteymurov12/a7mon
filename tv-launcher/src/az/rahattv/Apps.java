package az.rahattv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finding, describing and launching installed apps. Every call is crash-safe. */
final class Apps {
    private Apps() {}

    /** TV build first, then the phone build. */
    static final String[] NETFLIX = {"com.netflix.ninja", "com.netflix.mediaclient"};
    static final String[] YOUTUBE = {
            "com.google.android.youtube.tv", "com.google.android.youtube.googletv",
            "com.google.android.youtube", "com.liskovsoft.smarttubetv.beta",
            "com.liskovsoft.smarttubetv", "com.teamsmart.videomanager.tv"};
    static final String PLAY_STORE = "com.android.vending";
    static final String GMS = "com.google.android.gms";
    static final String GSF = "com.google.android.gsf";
    static final String[] WEBVIEW = {"com.google.android.webview", "com.android.webview", "com.android.chrome"};

    static final class Entry {
        String pkg;
        ComponentName cn;
        String label;
        boolean leanback;
        boolean system;
        Drawable art;
        boolean banner;
        int color = 0xFF334155;
    }

    // ---------------------------------------------------------------- loading

    static List<Entry> load(Context c, Set<String> hidden) {
        PackageManager pm = c.getPackageManager();
        String self = c.getPackageName();
        Map<String, Entry> byComponent = new LinkedHashMap<>();
        Set<String> leanbackPkgs = new HashSet<>();

        for (ResolveInfo ri : query(pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))) {
            Entry e = entry(pm, ri, true);
            if (e == null || e.pkg.equals(self) || hidden.contains(e.pkg)) continue;
            byComponent.put(e.cn.flattenToShortString(), e);
            leanbackPkgs.add(e.pkg);
        }
        for (ResolveInfo ri : query(pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))) {
            Entry e = entry(pm, ri, false);
            if (e == null || e.pkg.equals(self) || hidden.contains(e.pkg)) continue;
            if (leanbackPkgs.contains(e.pkg)) continue;
            String key = e.cn.flattenToShortString();
            if (!byComponent.containsKey(key)) byComponent.put(key, e);
        }

        List<Entry> list = new ArrayList<>(byComponent.values());
        final Collator col = Collator.getInstance();
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return col.compare(a.label, b.label);
            }
        });
        return list;
    }

    private static List<ResolveInfo> query(PackageManager pm, Intent i) {
        try {
            List<ResolveInfo> r = pm.queryIntentActivities(i, 0);
            return r != null ? r : Collections.<ResolveInfo>emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static Entry entry(PackageManager pm, ResolveInfo ri, boolean leanback) {
        try {
            if (ri == null || ri.activityInfo == null) return null;
            Entry e = new Entry();
            e.pkg = ri.activityInfo.packageName;
            e.cn = new ComponentName(e.pkg, ri.activityInfo.name);
            CharSequence l = ri.loadLabel(pm);
            e.label = l != null ? l.toString().trim() : e.pkg;
            if (e.label.isEmpty()) e.label = e.pkg;
            e.leanback = leanback;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            e.system = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            Drawable banner = null;
            try {
                banner = ri.activityInfo.loadBanner(pm);
            } catch (Throwable ignored) {
            }
            if (banner != null) {
                e.art = banner;
                e.banner = true;
            } else {
                e.art = ri.loadIcon(pm);
            }
            e.color = tileColor(e.art);
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    /** App art for one package (banner preferred), or null. */
    static Entry describe(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = launchIntent(c, pkg);
        if (i == null) return null;
        ResolveInfo ri = null;
        try {
            ri = pm.resolveActivity(i, 0);
        } catch (Throwable ignored) {
        }
        return ri != null ? entry(pm, ri, true) : null;
    }

    /** Average colour of an icon, darkened, used as the tile background. */
    static int tileColor(Drawable d) {
        if (d == null) return 0xFF334155;
        try {
            Bitmap bm = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            copy.setBounds(0, 0, 12, 12);
            copy.draw(cv);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int p = bm.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                    n++;
                }
            }
            bm.recycle();
            if (n == 0) return 0xFF334155;
            int avg = Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
            return Ui.darker(avg, 0.55f);
        } catch (Throwable t) {
            return 0xFF334155;
        }
    }

    // ---------------------------------------------------------------- queries

    static PackageInfo info(Context c, String pkg) {
        if (pkg == null) return null;
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0);
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean installed(Context c, String pkg) {
        return info(c, pkg) != null;
    }

    static boolean enabled(Context c, String pkg) {
        PackageInfo pi = info(c, pkg);
        return pi != null && pi.applicationInfo != null && pi.applicationInfo.enabled;
    }

    static String firstInstalled(Context c, String[] pkgs) {
        for (String p : pkgs) if (installed(c, p)) return p;
        return null;
    }

    static String firstLaunchable(Context c, String[] pkgs) {
        for (String p : pkgs) if (launchIntent(c, p) != null) return p;
        return null;
    }

    static String version(Context c, String pkg) {
        PackageInfo pi = info(c, pkg);
        if (pi == null) return null;
        return pi.versionName != null ? pi.versionName : String.valueOf(pi.versionCode);
    }

    static Intent launchIntent(Context c, String pkg) {
        if (pkg == null) return null;
        PackageManager pm = c.getPackageManager();
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        } catch (Throwable ignored) {
        }
        if (i == null) {
            try {
                i = pm.getLaunchIntentForPackage(pkg);
            } catch (Throwable ignored) {
            }
        }
        return i;
    }

    /** Other launchers (normally the TV's original menu with the HDMI / antenna sources). */
    static List<ResolveInfo> otherHomes(Context c) {
        List<ResolveInfo> out = new ArrayList<>();
        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : query(c.getPackageManager(), h)) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (p.equals(c.getPackageName())) continue;
            // the system "choose an app" dialog and setup wizards are not real menus
            if (p.equals("android") || p.contains("setupwizard") || p.contains("provision")) continue;
            out.add(ri);
        }
        return out;
    }

    /** Package of the launcher the Home button opens, "android" if none is chosen yet. */
    static String defaultHome(Context c) {
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(h, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null ? ri.activityInfo.packageName : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- actions

    static boolean start(Context c, Intent i) {
        if (i == null) return false;
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean launch(Context c, String pkg) {
        Intent i = launchIntent(c, pkg);
        if (i != null && start(c, i)) return true;
        toast(c, "Tətbiq açılmadı");
        return false;
    }

    static boolean launch(Context c, ComponentName cn, boolean leanback) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (start(c, i)) return true;
        return launch(c, cn.getPackageName());
    }

    static void playStore(Context c, String pkg) {
        if (!installed(c, PLAY_STORE)) {
            toast(c, "Play Market bu TV-də yoxdur");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        i.setPackage(PLAY_STORE);
        if (start(c, i)) return;
        if (!start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)))) {
            launch(c, PLAY_STORE);
        }
    }

    static void playSearch(Context c, String q) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q)));
        i.setPackage(PLAY_STORE);
        if (!start(c, i)) launch(c, PLAY_STORE);
    }

    static void appInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        if (!start(c, i)) openSettings(c, Settings.ACTION_APPLICATION_SETTINGS);
    }

    static void uninstall(Context c, String pkg) {
        if (!start(c, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)))) {
            appInfo(c, pkg);
        }
    }

    static void openSettings(Context c, String action) {
        if (action != null && start(c, new Intent(action))) return;
        if (start(c, new Intent(Settings.ACTION_SETTINGS))) return;
        toast(c, "Parametrlər açılmadı");
    }

    static void openUrl(Context c, String url) {
        if (!start(c, new Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            toast(c, "Brauzer tapılmadı");
        }
    }

    static void toast(Context c, String s) {
        try {
            Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }
}
