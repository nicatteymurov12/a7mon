package az.rahattv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.util.Calendar;
import java.util.List;

/** Device facts (memory, storage, network, clock) and the RAM booster. */
final class Sys {
    private Sys() {}

    private static final String[] DAYS = {
            "Bazar", "Bazar ertəsi", "Çərşənbə axşamı", "Çərşənbə", "Cümə axşamı", "Cümə", "Şənbə"};
    private static final String[] MONTHS = {
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr"};

    static String clock() {
        Calendar c = Calendar.getInstance();
        return String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static String date() {
        Calendar c = Calendar.getInstance();
        int d = c.get(Calendar.DAY_OF_WEEK) - 1;
        int m = c.get(Calendar.MONTH);
        return DAYS[Math.max(0, Math.min(6, d))] + ", " + c.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[Math.max(0, Math.min(11, m))] + " " + c.get(Calendar.YEAR);
    }

    /** True when the TV clock is clearly behind (lost after a power cut) — breaks Play Market and Netflix. */
    static boolean clockWrong() {
        return System.currentTimeMillis() < BuildInfo.BUILD_TIME - 24L * 3600 * 1000;
    }

    static long[] ram(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return new long[]{mi.availMem, mi.totalMem};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    static long[] storage() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            return new long[]{s.getAvailableBytes(), s.getTotalBytes()};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    /** "Wi-Fi", "Kabel (Ethernet)" or null when offline. */
    @SuppressWarnings("deprecation")
    static String network(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return null;
            switch (ni.getType()) {
                case ConnectivityManager.TYPE_WIFI: return "Wi-Fi";
                case ConnectivityManager.TYPE_ETHERNET: return "Kabel (Ethernet)";
                default: return ni.getTypeName();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** -1 unknown, 0 off, 1 on. */
    @SuppressWarnings("deprecation")
    static int unknownSources(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) == 1 ? 1 : 0;
        } catch (Throwable t) {
            try {
                return Settings.Global.getInt(c.getContentResolver(), Settings.Global.INSTALL_NON_MARKET_APPS) == 1 ? 1 : 0;
            } catch (Throwable t2) {
                return -1;
            }
        }
    }

    /**
     * Stops idle background processes of downloaded (and updated) apps. Only
     * processes Android itself considers safe to kill are touched, so nothing
     * on screen or playing music is interrupted, and TV system services are skipped.
     * Returns the number of packages asked to stop.
     */
    static int boost(Context c) {
        int n = 0;
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            PackageManager pm = c.getPackageManager();
            String self = c.getPackageName();
            String ime = null;
            try {
                ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            } catch (Throwable ignored) {
            }
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                String p = ai.packageName;
                if (p == null || p.equals(self)) continue;
                if (ime != null && ime.startsWith(p + "/")) continue;
                if (p.equals(Apps.GMS) || p.equals(Apps.GSF) || p.equals("android") || p.startsWith("com.android.")) continue;
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (system && !updated) continue;
                if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                try {
                    am.killBackgroundProcesses(p);
                    n++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }
}
