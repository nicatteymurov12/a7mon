package az.rahattv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Catches the reason when another app crashes ("X keeps stopping") or freezes.
 * While Android shows its crash dialog, the crashed process is still listed in
 * ActivityManager's error state together with the exception text; we read it
 * then, explain it in plain words and keep the last few for the Yoxlama screen.
 */
final class CrashWatch {
    private CrashWatch() {}

    private static final int KEEP = 8;

    static final class Report {
        long time;
        String pkg;
        String label;
        boolean frozen;
        String shortMsg;
        String detail;
        String key;
    }

    // ------------------------------------------------------------------ detection

    /** A crash/freeze of another app that has not been reported yet, or null. */
    static Report poll(Context c) {
        List<ActivityManager.ProcessErrorStateInfo> errs;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            errs = am.getProcessesInErrorState();
        } catch (Throwable t) {
            return null;
        }
        if (errs == null) return null;
        for (ActivityManager.ProcessErrorStateInfo e : errs) {
            try {
                if (e == null || e.condition == ActivityManager.ProcessErrorStateInfo.NO_ERROR) continue;
                String pkg = packageOf(c, e);
                if (pkg == null || pkg.equals(c.getPackageName())) continue;
                Report r = new Report();
                r.time = System.currentTimeMillis();
                r.pkg = pkg;
                r.label = label(c, pkg);
                r.frozen = e.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING;
                r.shortMsg = clean(e.shortMsg != null ? e.shortMsg : e.longMsg);
                r.detail = trim(join(e.longMsg, e.stackTrace), 900);
                r.key = pkg + "|" + e.pid + "|" + (r.shortMsg != null ? r.shortMsg.hashCode() : 0);
                if (seen(c, r.key)) continue;
                save(c, r);
                return r;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String packageOf(Context c, ActivityManager.ProcessErrorStateInfo e) {
        String p = e.processName;
        if (p != null) {
            int i = p.indexOf(':');
            if (i > 0) p = p.substring(0, i);
            if (Apps.installed(c, p)) return p;
        }
        try {
            String[] pk = c.getPackageManager().getPackagesForUid(e.uid);
            if (pk != null && pk.length > 0) return pk[0];
        } catch (Throwable ignored) {
        }
        return p;
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            CharSequence l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (l != null && l.length() > 0) return l.toString();
        } catch (Throwable ignored) {
        }
        return pkg;
    }

    // ------------------------------------------------------------------ explanation

    static boolean isYoutube(String pkg) {
        for (String p : Apps.YOUTUBE) if (p.equals(pkg)) return true;
        return false;
    }

    /** Plain-language reason + what to do. */
    static String advice(Report r) {
        String all = ((r.shortMsg != null ? r.shortMsg : "") + "\n" + (r.detail != null ? r.detail : "")).toLowerCase(Locale.US);
        StringBuilder s = new StringBuilder();
        if (Sys.clockWrong()) {
            s.append("Diqqət: TV-nin saatı səhvdir — bu özü də tətbiqləri çökdürə bilər. Əvvəlcə tarixi düzəldin.\n\n");
        }
        if (r.frozen) {
            s.append("Tətbiq donub qaldı (cavab vermədi). Bu TV-də RAM azdır: ana ekranda \"Sürətləndir\" basın və yenidən açın.");
        } else if (has(all, "unsatisfiedlinkerror", "dlopen failed", "couldn't find \"lib", "no implementation found")) {
            s.append("Bu APK bu TV-nin prosessoruna uyğun deyil. Saytdan \"armeabi-v7a\" (ARM 32-bit) versiyasını yükləyin.");
        } else if (has(all, "resources$notfoundexception", "missingsplit", "split_config", "requires split")) {
            s.append("APK yarımçıqdır: bu tətbiq bir neçə hissədən ibarətdir (XAPK/APKM paketi). Tək, tam \".apk\" faylını tapıb quraşdırın.");
        } else if (has(all, "outofmemoryerror", "failed to allocate")) {
            s.append("RAM çatmadı. \"Sürətləndir\" basın, sonra açın. Təkrarlanırsa, tətbiqin \"Lite\" və ya daha köhnə versiyasını yoxlayın.");
        } else if (has(all, "webview", "chromium", "missingwebviewpackage")) {
            s.append("Android System WebView problemi. Play Market-də \"Android System WebView\" axtarıb yeniləyin (Yoxlama bölməsində düymə var).");
        } else if (has(all, "com.google.android.gms", "googleplayservices", "googleapiavailability", "google play services")) {
            s.append("Google Play Xidmətləri köhnədir və ya söndürülüb. Play Market-dən \"Google Play Services\"-i yeniləyin və TV-ni yenidən yandırın.");
        } else if (has(all, "nosuchmethoderror", "noclassdeffounderror", "classnotfoundexception", "verifyerror", "abstractmethoderror")) {
            s.append("Bu versiya Android 7 üçün nəzərdə tutulmayıb. Tətbiqi silin və daha köhnə versiyasını quraşdırın.");
        } else if (has(all, "sqlite", "database")) {
            s.append("Tətbiqin yadda saxladığı məlumatlar zədələnib. \"Düzəlt\" → Yaddaş → \"Məlumatları təmizlə\".");
        } else if (has(all, "sslhandshake", "certpathvalidator", "certificate")) {
            s.append("Təhlükəsiz bağlantı xətası. Çox vaxt TV-nin saatı səhv olanda olur — tarix və saatı yoxlayın.");
        } else if (has(all, "securityexception", "permission denial")) {
            s.append("Tətbiqə lazım olan icazə yoxdur. \"Düzəlt\" → İcazələr bölməsində hamısını açın.");
        } else {
            s.append("Tətbiqin öz xətasıdır. Ardıcıl edin:\n"
                    + "1) \"Düzəlt\" → Məcburi dayandır → Yaddaş → Keşi təmizlə, sonra yenidən açın.\n"
                    + "2) Kömək etməsə: \"Məlumatları təmizlə\".\n"
                    + "3) Kömək etməsə: \"Yenilə\" (Play Market) və ya tətbiq məlumatında \"Yeniləmələri sil\".");
        }
        if (isYoutube(r.pkg)) {
            s.append("\n\nYouTube yenə də açılmırsa: SmartTube — köhnə Android TV-lərdə işləyən pulsuz, qeyri-rəsmi YouTube proqramı. "
                    + "APK faylını kompüterdə github.com/yuliskov/SmartTube səhifəsindən yükləyib fləşə qoyun → \"USB / APK\".");
        }
        return s.toString();
    }

    private static boolean has(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    // ------------------------------------------------------------------ storage

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("rahattv_crash", Context.MODE_PRIVATE);
    }

    private static boolean seen(Context c, String key) {
        for (Report r : history(c)) if (key.equals(r.key)) return true;
        return false;
    }

    private static void save(Context c, Report r) {
        try {
            JSONArray old = new JSONArray(sp(c).getString("log", "[]"));
            JSONArray out = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("time", r.time);
            o.put("pkg", r.pkg);
            o.put("label", r.label);
            o.put("frozen", r.frozen);
            o.put("short", r.shortMsg != null ? r.shortMsg : "");
            o.put("detail", r.detail != null ? r.detail : "");
            o.put("key", r.key);
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < KEEP; i++) out.put(old.get(i));
            sp(c).edit().putString("log", out.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Newest first. */
    static List<Report> history(Context c) {
        List<Report> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("log", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Report r = new Report();
                r.time = o.optLong("time");
                r.pkg = o.optString("pkg");
                r.label = o.optString("label", r.pkg);
                r.frozen = o.optBoolean("frozen");
                r.shortMsg = o.optString("short");
                r.detail = o.optString("detail");
                r.key = o.optString("key");
                list.add(r);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    static Report lastFor(Context c, String pkg) {
        for (Report r : history(c)) if (pkg.equals(r.pkg)) return r;
        return null;
    }

    static void clear(Context c) {
        sp(c).edit().remove("log").apply();
    }

    static String when(long t) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.US, "%02d.%02d %02d:%02d",
                c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }

    // ------------------------------------------------------------------ text helpers

    private static String join(String a, String b) {
        if (a == null) return b;
        if (b == null || b.contains(a)) return b != null ? b : a;
        return a + "\n" + b;
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return trim(s, 220);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
