package az.rahattv;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finds APK files on USB flash drives (and internal storage) and installs them. */
public class UsbActivity extends Activity {

    private static final int REQ_STORAGE = 7;
    private static final int MAX_DEPTH = 4;
    private static final int MAX_VISITS = 8000;
    private static final int MAX_FILES = 80;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout col;
    private LinearLayout list;
    private TextView status;
    private int scanGen;
    private boolean alive;

    private static final class Found {
        File file;
        boolean bundle;      // .xapk / .apks / .apkm — cannot be installed directly
        String label;
        String pkg;
        String version;
        String state;
        int stateColor;
        Drawable icon;
        String volume;
        boolean preferred;   // inside a "RahatTV" folder
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        alive = true;
        col = Ui.page(this, "USB-dən quraşdır",
                "Fləş kartı TV-yə taxın. APK faylları avtomatik tapılacaq. Faylı seçib OK basın — Android quraşdırma pəncərəsi açılacaq.");

        FrameLayout rescan = Ui.row(this, 0, "Yenidən axtar", "Fləş kartı indi taxmısınızsa bunu basın", "OK");
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startScan(); }
        });
        Ui.addRow(col, rescan);

        if (Sys.unknownSources(this) == 0) {
            FrameLayout us = Ui.row(this, Ui.WARN, "\"Naməlum mənbələr\" bağlıdır",
                    "APK quraşdırmaq üçün bunu bir dəfə açmaq lazımdır: Parametrlər → Təhlükəsizlik → Naməlum mənbələr", "Aç");
            us.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Apps.openSettings(UsbActivity.this, Settings.ACTION_SECURITY_SETTINGS); }
            });
            Ui.addRow(col, us);
        }

        status = Ui.text(this, "", 14, Ui.MUTED, false);
        col.addView(status, Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 14, 0, 8));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        col.addView(list);

        rescan.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_MOUNTED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addAction(Intent.ACTION_MEDIA_REMOVED);
        f.addDataScheme("file");
        try {
            registerReceiver(mediaReceiver, f);
        } catch (Throwable ignored) {
        }

        if (hasStoragePermission()) {
            startScan();
        } else {
            status.setText("Fləş karta baxmaq üçün icazə verin…");
            try {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            } catch (Throwable t) {
                startScan();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alive = false;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(mediaReceiver);
        } catch (Throwable ignored) {
        }
    }

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.removeCallbacks(scanLater);
            handler.postDelayed(scanLater, 1500);
        }
    };

    private final Runnable scanLater = new Runnable() {
        @Override public void run() { startScan(); }
    };

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQ_STORAGE) return;
        if (hasStoragePermission()) {
            startScan();
        } else {
            status.setText("İcazə verilmədi. \"Yenidən axtar\" basın və \"İcazə ver\" seçin.");
            status.setTextColor(Ui.WARN);
        }
    }

    // ------------------------------------------------------------------ scanning

    private void startScan() {
        if (!hasStoragePermission()) {
            try {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                return;
            } catch (Throwable ignored) {
            }
        }
        final int gen = ++scanGen;
        status.setText("Axtarılır…");
        status.setTextColor(Ui.MUTED);
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Found> found;
                int volumes;
                try {
                    Map<String, String> roots = roots(app);
                    volumes = roots.size();
                    found = scan(app, roots);
                } catch (Throwable t) {
                    found = new ArrayList<>();
                    volumes = 0;
                }
                final List<Found> res = found;
                final int vols = volumes;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen == scanGen && alive && !isFinishing()) show(res, vols);
                    }
                });
            }
        }, "rahattv-usb").start();
    }

    /** Storage roots → human-readable name. Duplicates (symlinks) are removed. */
    static Map<String, String> roots(Context c) {
        Map<String, String> out = new LinkedHashMap<>();
        List<File> candidates = new ArrayList<>();
        try {
            File[] dirs = c.getExternalFilesDirs(null);
            if (dirs != null) {
                for (File d : dirs) {
                    if (d == null) continue;
                    String p = d.getAbsolutePath();
                    int i = p.indexOf("/Android/data/");
                    if (i > 0) candidates.add(new File(p.substring(0, i)));
                }
            }
        } catch (Throwable ignored) {
        }
        String[] parents = {"/storage", "/mnt/usb", "/mnt/usbhost", "/mnt/usb_storage", "/mnt/media_rw", "/mnt/external_sd", "/udisk"};
        for (String parent : parents) {
            File[] kids = null;
            try {
                kids = new File(parent).listFiles();
            } catch (Throwable ignored) {
            }
            if (kids == null) continue;
            for (File k : kids) {
                String n = k.getName();
                if (n.equals("self") || n.equals("emulated") || n.equals("enc_emulated") || n.startsWith(".")) continue;
                if (k.isDirectory()) candidates.add(k);
            }
        }
        try {
            candidates.add(Environment.getExternalStorageDirectory());
        } catch (Throwable ignored) {
        }

        String internal = null;
        try {
            internal = Environment.getExternalStorageDirectory().getCanonicalPath();
        } catch (Throwable ignored) {
        }
        List<String> seen = new ArrayList<>();
        for (File f : candidates) {
            String canon;
            try {
                canon = f.getCanonicalPath();
            } catch (Throwable t) {
                canon = f.getAbsolutePath();
            }
            if (seen.contains(canon) || !f.canRead()) continue;
            seen.add(canon);
            boolean isInternal = canon.equals(internal) || canon.contains("/emulated/");
            out.put(f.getAbsolutePath(), isInternal ? "Daxili yaddaş" : "USB: " + f.getName());
        }
        return out;
    }

    private static List<Found> scan(Context c, Map<String, String> roots) {
        List<Found> out = new ArrayList<>();
        int[] visits = {0};
        for (Map.Entry<String, String> r : roots.entrySet()) {
            walk(new File(r.getKey()), r.getValue(), 0, out, visits);
            if (out.size() >= MAX_FILES || visits[0] > MAX_VISITS) break;
        }
        PackageManager pm = c.getPackageManager();
        for (Found f : out) describe(pm, f);
        Collections.sort(out, new Comparator<Found>() {
            @Override
            public int compare(Found a, Found b) {
                if (a.preferred != b.preferred) return a.preferred ? -1 : 1;
                boolean au = a.volume.startsWith("USB"), bu = b.volume.startsWith("USB");
                if (au != bu) return au ? -1 : 1;
                if (a.bundle != b.bundle) return a.bundle ? 1 : -1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    private static void walk(File dir, String volume, int depth, List<Found> out, int[] visits) {
        if (depth > MAX_DEPTH || out.size() >= MAX_FILES || visits[0] > MAX_VISITS) return;
        File[] kids;
        try {
            kids = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (kids == null) return;
        for (File k : kids) {
            if (++visits[0] > MAX_VISITS || out.size() >= MAX_FILES) return;
            String n = k.getName();
            if (n.startsWith(".")) continue;
            if (k.isDirectory()) {
                // skip app-private and system folders: huge and never contain user APKs
                if (depth == 0 && (n.equals("Android") || n.equals("LOST.DIR") || n.equals("System Volume Information"))) continue;
                walk(k, volume, depth + 1, out, visits);
            } else {
                String low = n.toLowerCase(Locale.US);
                boolean apk = low.endsWith(".apk");
                boolean bundle = low.endsWith(".xapk") || low.endsWith(".apks") || low.endsWith(".apkm");
                if (!apk && !bundle) continue;
                Found f = new Found();
                f.file = k;
                f.bundle = bundle;
                f.label = n;
                f.volume = volume;
                f.preferred = k.getAbsolutePath().toLowerCase(Locale.US).contains("/rahattv/");
                out.add(f);
            }
        }
    }

    private static void describe(PackageManager pm, Found f) {
        String path = f.file.getAbsolutePath();
        if (f.bundle) {
            f.state = "Bu \"bundle\" faylıdır (XAPK/APKS/APKM) — birbaşa quraşdırılmır. Adi .apk faylı yükləyin.";
            f.stateColor = Ui.BAD;
            return;
        }
        PackageInfo pi = null;
        try {
            pi = pm.getPackageArchiveInfo(path, 0);
        } catch (Throwable ignored) {
        }
        if (pi == null || pi.applicationInfo == null) {
            f.state = "Fayl zədəlidir və ya bu TV üçün uyğun deyil";
            f.stateColor = Ui.BAD;
            return;
        }
        ApplicationInfo ai = pi.applicationInfo;
        ai.sourceDir = path;
        ai.publicSourceDir = path;
        try {
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) f.label = l.toString();
        } catch (Throwable ignored) {
        }
        try {
            f.icon = pm.getApplicationIcon(ai);
        } catch (Throwable ignored) {
        }
        f.pkg = pi.packageName;
        f.version = pi.versionName != null ? pi.versionName : String.valueOf(pi.versionCode);
        int minSdk = 0;
        try {
            minSdk = ai.getClass().getField("minSdkVersion").getInt(ai);
        } catch (Throwable ignored) {
        }
        PackageInfo have = null;
        try {
            have = pm.getPackageInfo(pi.packageName, 0);
        } catch (Throwable ignored) {
        }
        if (minSdk > Build.VERSION.SDK_INT) {
            f.state = "Bu versiya daha yeni Android tələb edir (bu TV: Android " + Build.VERSION.RELEASE + "). Köhnə versiyasını tapın.";
            f.stateColor = Ui.BAD;
        } else if (have == null) {
            f.state = "Yeni tətbiq — quraşdırmağa hazırdır";
            f.stateColor = Ui.OK;
        } else if (have.versionCode < pi.versionCode) {
            f.state = "Yeniləmə: " + have.versionName + " → " + f.version;
            f.stateColor = Ui.OK;
        } else if (have.versionCode == pi.versionCode) {
            f.state = "Bu versiya artıq quraşdırılıb";
            f.stateColor = Ui.MUTED;
        } else {
            f.state = "TV-də daha yeni versiya var (" + have.versionName + ")";
            f.stateColor = Ui.WARN;
        }
    }

    private void show(List<Found> found, int volumes) {
        list.removeAllViews();
        boolean usb = false;
        for (Found f : found) if (f.volume.startsWith("USB")) usb = true;
        if (found.isEmpty()) {
            status.setText(volumes <= 1
                    ? "Fləş kart tapılmadı. Kartı taxın, 5 saniyə gözləyin və \"Yenidən axtar\" basın."
                    : "Fləş kartda APK fayl tapılmadı. Faylları kartın içində RahatTV/apps qovluğuna kopyalayın.");
            status.setTextColor(Ui.WARN);
            return;
        }
        status.setText(found.size() + " fayl tapıldı" + (usb ? "" : " (yalnız daxili yaddaşda)"));
        status.setTextColor(Ui.MUTED);
        for (final Found f : found) {
            String detail = f.state + "\n" + f.volume + " · " + f.file.getName()
                    + (f.version != null ? " · v" + f.version : "")
                    + " · " + Ui.mb(f.file.length());
            FrameLayout r = Ui.row(this, f.stateColor, f.label, detail, f.bundle ? null : "Quraşdır");
            if (f.icon != null) {
                LinearLayout h = (LinearLayout) r.getChildAt(0);
                LinearLayout.LayoutParams ip = Ui.lp(40, 40);
                ip.rightMargin = Ui.px(14);
                h.addView(Ui.image(this, f.icon), 1, ip);
            }
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { install(f); }
            });
            Ui.addRow(list, r);
        }
    }

    // ------------------------------------------------------------------ install

    private void install(final Found f) {
        if (f.bundle) {
            Ui.dialog(this)
                    .setTitle("Bu fayl quraşdırılmır")
                    .setMessage("\"" + f.file.getName() + "\" bir neçə hissədən ibarət paketdir (XAPK/APKS/APKM).\n\n"
                            + "Android 7 TV bunu birbaşa quraşdıra bilmir. Saytda həmin tətbiqin adi \".apk\" "
                            + "versiyasını (\"APK\", \"nodpi\", \"armeabi-v7a\") seçib yükləyin.")
                    .setPositiveButton("Aydındır", null)
                    .show();
            return;
        }
        if (!f.file.exists()) {
            Apps.toast(this, "Fayl tapılmadı — fləş kart çıxarılıb?");
            startScan();
            return;
        }
        if (Sys.unknownSources(this) == 0) {
            Ui.dialog(this)
                    .setTitle("Əvvəlcə icazə lazımdır")
                    .setMessage("Parametrlər → Təhlükəsizlik → \"Naməlum mənbələr\" seçimini aktiv edin, sonra geri qayıdıb yenidən OK basın.")
                    .setPositiveButton("Parametrləri aç", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { Apps.openSettings(UsbActivity.this, Settings.ACTION_SECURITY_SETTINGS); }
                    })
                    .setNegativeButton("Yenə də cəhd et", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { openInstaller(f.file); }
                    })
                    .show();
            return;
        }
        openInstaller(f.file);
    }

    private void openInstaller(File file) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        if (!Apps.start(this, i)) {
            Ui.dialog(this)
                    .setTitle("Quraşdırıcı açılmadı")
                    .setMessage("Bu TV-də APK quraşdırıcısı bloklanıb. TV-nin öz \"Fayl meneceri\" / \"Media\" proqramından "
                            + "fləş kartı açıb faylı oradan quraşdırmağı yoxlayın.")
                    .setPositiveButton("Bağla", null)
                    .show();
        }
    }
}
