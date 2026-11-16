package az.rahattv;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaDrm;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.UUID;

/**
 * "Yoxlama": checks everything that usually breaks Play Market and Netflix on
 * Android 7 TVs (clock, network, Google services, WebView, DRM level, space)
 * and offers a one-press fix where Android allows it.
 */
public class CheckActivity extends Activity {

    private static final UUID WIDEVINE = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    /** Cached DRM answer: {level, detail}; null until measured. */
    private static volatile String[] drmCache;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout col;
    private LinearLayout box;
    private TextView summary;
    private int problems;
    private int drmIndex = -1;
    private boolean alive;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        alive = true;
        col = Ui.page(this, "Yoxlama: Netflix və Play Market",
                "Netflix və Play Market-in xəta verməsinin ən çox rast gəlinən səbəbləri. Qırmızı və sarı sətirləri seçib OK basın.");
        summary = Ui.text(this, "", 16, Ui.TEXT, true);
        col.addView(summary, Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 10));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        col.addView(box);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alive = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void build() {
        box.removeAllViews();
        problems = 0;

        // 1. clock
        if (Sys.clockWrong()) {
            add(Ui.BAD, "Tarix və saat səhvdir", "TV-də: " + Sys.date() + " " + Sys.clock()
                            + "\nPlay Market və Netflix bu səbəbdən \"bağlantı xətası\" verir. \"Avtomatik tarix və saat\" seçin.",
                    "Düzəlt", settings(Settings.ACTION_DATE_SETTINGS));
        } else {
            add(Ui.OK, "Tarix və saat", Sys.date() + " " + Sys.clock(), null, null);
        }

        // 2. network
        String net = Sys.network(this);
        if (net == null) {
            add(Ui.BAD, "İnternet yoxdur", "Wi-Fi və ya kabeli yoxlayın.", "Wi-Fi", settings(Settings.ACTION_WIFI_SETTINGS));
        } else {
            add(Ui.OK, "İnternet", "Qoşulub: " + net, null, null);
        }

        // 3. DRM (measured in the background, can take a second)
        drmIndex = box.getChildCount();
        if (drmCache != null) {
            addDrm(drmCache);
        } else {
            add(Ui.MUTED, "Widevine DRM (video qoruması)", "Yoxlanılır…", null, null).setTag("drm-pending");
            measureDrm();
        }

        // 4. Netflix
        String nf = Apps.firstInstalled(this, Apps.NETFLIX);
        if (nf == null) {
            add(Ui.WARN, "Netflix quraşdırılmayıb", "Play Market-də axtarın və ya APK faylını fləş kartdan quraşdırın.",
                    "Axtar", new Runnable() { @Override public void run() { Apps.playSearch(CheckActivity.this, "Netflix"); } });
        } else {
            boolean tv = nf.equals("com.netflix.ninja");
            final String p = nf;
            add(Ui.OK, "Netflix", (tv ? "TV versiyası" : "Telefon versiyası (pultla idarə bir az çətin ola bilər)")
                            + " · v" + Apps.version(this, nf)
                            + "\nXəta verirsə: Tətbiq məlumatı → \"Keşi təmizlə\", sonra TV-ni söndürüb yandırın.",
                    "Keş", new Runnable() { @Override public void run() { Apps.appInfo(CheckActivity.this, p); } });
        }

        // 5–7. Google Play stack
        googleRow(Apps.PLAY_STORE, "Google Play Market", true,
                "Bu TV-də Play Market yoxdur. Tətbiqləri USB-dən APK kimi quraşdırın.");
        googleRow(Apps.GMS, "Google Play Xidmətləri", true,
                "Play Xidmətləri olmadan Play Market və bir çox tətbiq işləmir.");
        googleRow(Apps.GSF, "Google Services Framework", false,
                "Bəzi TV-lərdə bu ayrıca görünmür — Play Market işləyirsə problem deyil.");

        // 8. WebView
        String wv = Apps.firstInstalled(this, Apps.WEBVIEW);
        if (wv == null) {
            add(Ui.WARN, "Android System WebView", "Tapılmadı. Bəzi tətbiqlərin girişi (login) açılmaya bilər.",
                    "Yenilə", playPage("com.google.android.webview"));
        } else {
            String v = Apps.version(this, wv);
            int major = major(v);
            boolean old = major > 0 && major < 100;
            add(old ? Ui.WARN : Ui.OK, "Android System WebView", "v" + v + (old
                            ? "\nKöhnədir — tətbiqlərin içində səhifələr açılmaya bilər. Play Market-dən yeniləyin."
                            : ""),
                    old ? "Yenilə" : null, old ? playPage("com.google.android.webview") : null);
        }

        // 9. YouTube
        String yt = Apps.firstInstalled(this, Apps.YOUTUBE);
        if (yt == null) {
            add(Ui.WARN, "YouTube quraşdırılmayıb", "Play Market-də axtarın.",
                    "Axtar", new Runnable() { @Override public void run() { Apps.playSearch(CheckActivity.this, "YouTube"); } });
        } else {
            add(Ui.OK, "YouTube", "v" + Apps.version(this, yt), null, null);
        }

        // 10. default launcher
        String home = Apps.defaultHome(this);
        if (getPackageName().equals(home)) {
            add(Ui.OK, "Əsas menyu", "Home düyməsi Rahat TV-ni açır.", null, null);
        } else {
            add(Ui.WARN, "Əsas menyu", "Home düyməsi hələ köhnə menyunu açır. Necə dəyişmək — burada.", "Bax",
                    new Runnable() { @Override public void run() { Apps.start(CheckActivity.this, new Intent(CheckActivity.this, SettingsActivity.class)); } });
        }

        // 11. unknown sources
        int us = Sys.unknownSources(this);
        if (us == 0) {
            add(Ui.WARN, "Naməlum mənbələr bağlıdır", "Fləş kartdan APK quraşdırmaq üçün açılmalıdır (Təhlükəsizlik bölməsi).",
                    "Aç", settings(Settings.ACTION_SECURITY_SETTINGS));
        } else if (us == 1) {
            add(Ui.OK, "Naməlum mənbələr", "Açıqdır — fləş kartdan APK quraşdırmaq olar.", null, null);
        }

        // 12. RAM
        long[] ram = Sys.ram(this);
        if (ram[1] > 0) {
            boolean small = ram[1] < 1700L * 1024 * 1024;
            add(small ? Ui.WARN : Ui.OK, "Operativ yaddaş (RAM)", Ui.mb(ram[0]) + " boş / " + Ui.mb(ram[1])
                    + (small ? "\nRAM azdır: Netflix-dən əvvəl ana ekranda \"Sürətləndir\" basın, eyni anda çox tətbiq açmayın." : ""),
                    null, null);
        }

        // 13. storage
        long[] st = Sys.storage();
        if (st[1] > 0) {
            long free = st[0];
            int color = free < 800L * 1024 * 1024 ? Ui.BAD : free < 1500L * 1024 * 1024 ? Ui.WARN : Ui.OK;
            add(color, "Daxili yaddaş", Ui.mb(free) + " boş / " + Ui.mb(st[1])
                            + (color != Ui.OK ? "\nYer azdır — yeniləmələr yüklənməyəcək. İstifadə etmədiyiniz tətbiqləri silin." : ""),
                    color != Ui.OK ? "Aç" : null, color != Ui.OK ? settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) : null);
        }

        // 14. device
        add(0, "Cihaz", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · Model: " + Build.MODEL
                + " · İstehsalçı: " + Build.MANUFACTURER + "\nÇip: " + Build.HARDWARE + " / " + Build.BOARD
                + " · Proqram: " + Build.DISPLAY, null, null);

        // Play Market repair steps
        TextView t = Ui.section(this, "Play Market xəta verirsə — ardıcıl edin");
        box.addView(t, Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 20, 0, 8));
        step("1. Play Market — keşi təmizlə", "\"Yaddaş\" → \"Keşi təmizlə\". Kömək etməsə \"Məlumatları təmizlə\".", Apps.PLAY_STORE);
        step("2. Google Play Xidmətləri — keşi təmizlə", "\"Yaddaş\" → \"Keşi təmizlə\" (məlumatları SİLMƏYİN).", Apps.GMS);
        step("3. Google Services Framework — keşi təmizlə", "Yalnız əvvəlki addımlar kömək etmədisə.", Apps.GSF);
        add(0, "4. TV-ni tam söndürüb 30 saniyə sonra yandırın", "Pult ilə yox — rozetkadan çıxarıb taxın.", null, null);

        summary.setText(problems == 0 ? "Hər şey qaydasındadır" : problems + " problem tapıldı — aşağıda baxın");
        summary.setTextColor(problems == 0 ? Ui.OK : Ui.WARN);

        if (box.getChildCount() > 0) box.getChildAt(0).requestFocus();
    }

    private void googleRow(final String pkg, String name, boolean required, String missingText) {
        if (!Apps.installed(this, pkg)) {
            add(required ? Ui.BAD : Ui.WARN, name + " tapılmadı", missingText, null, null);
        } else if (!Apps.enabled(this, pkg)) {
            add(Ui.BAD, name + " söndürülüb", "Açılan pəncərədə \"Aktiv et\" basın.", "Aktiv et",
                    new Runnable() { @Override public void run() { Apps.appInfo(CheckActivity.this, pkg); } });
        } else {
            add(Ui.OK, name, "v" + Apps.version(this, pkg), "Keş",
                    new Runnable() { @Override public void run() { Apps.appInfo(CheckActivity.this, pkg); } });
        }
    }

    private void step(String title, String detail, final String pkg) {
        boolean has = Apps.installed(this, pkg);
        add(0, title, has ? detail : "Bu TV-də yoxdur — keçin.", has ? "Aç" : null,
                has ? new Runnable() { @Override public void run() { Apps.appInfo(CheckActivity.this, pkg); } } : null);
    }

    private Runnable settings(final String action) {
        return new Runnable() { @Override public void run() { Apps.openSettings(CheckActivity.this, action); } };
    }

    private Runnable playPage(final String pkg) {
        return new Runnable() { @Override public void run() { Apps.playStore(CheckActivity.this, pkg); } };
    }

    private static int major(String v) {
        if (v == null) return -1;
        int n = 0, i = 0;
        while (i < v.length() && Character.isDigit(v.charAt(i))) {
            n = n * 10 + (v.charAt(i) - '0');
            if (n > 100000) break;
            i++;
        }
        return i == 0 ? -1 : n;
    }

    private View add(int color, String title, String detail, String hint, final Runnable action) {
        return insert(-1, color, title, detail, hint, action);
    }

    private View insert(int index, int color, String title, String detail, String hint, final Runnable action) {
        if (color == Ui.BAD || color == Ui.WARN) problems++;
        FrameLayout r = Ui.row(this, color, title, detail, hint);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (action == null) return;
                try {
                    action.run();
                } catch (Throwable t) {
                    Apps.toast(CheckActivity.this, "Açılmadı");
                }
            }
        });
        LinearLayout.LayoutParams p = Ui.lpMargins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 5);
        if (index < 0 || index > box.getChildCount()) box.addView(r, p); else box.addView(r, index, p);
        return r;
    }

    // ------------------------------------------------------------------ DRM

    private void measureDrm() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] res = readDrm();
                drmCache = res;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!alive || isFinishing() || drmIndex < 0 || drmIndex >= box.getChildCount()) return;
                        if (!"drm-pending".equals(box.getChildAt(drmIndex).getTag())) return;
                        boolean hadFocus = box.getChildAt(drmIndex).hasFocus();
                        box.removeViewAt(drmIndex);
                        int before = problems;
                        addDrmAt(res, drmIndex);
                        if (hadFocus) box.getChildAt(drmIndex).requestFocus();
                        if (problems != before) {
                            summary.setText(problems + " problem tapıldı — aşağıda baxın");
                            summary.setTextColor(Ui.WARN);
                        }
                    }
                });
            }
        }, "rahattv-drm").start();
    }

    private void addDrm(String[] r) {
        addDrmAt(r, -1);
    }

    private void addDrmAt(String[] r, int index) {
        String level = r[0];
        String extra = r[1];
        if ("L1".equals(level)) {
            insert(index, Ui.OK, "Widevine DRM: L1", "Netflix HD keyfiyyətdə göstərə bilər (cihaz Netflix tərəfindən dəstəklənirsə)." + extra, null, null);
        } else if (level != null && level.startsWith("L")) {
            insert(index, Ui.WARN, "Widevine DRM: " + level,
                    "Netflix bu TV-də ən çox SD (təxminən 480p) keyfiyyətdə göstərəcək. Bu, TV çipinin xüsusiyyətidir — "
                            + "heç bir proqram və ya launcher bunu dəyişə bilməz." + extra, null, null);
        } else {
            insert(index, Ui.BAD, "Widevine DRM yoxdur",
                    "Netflix və digər pullu video xidmətləri bu TV-də işləməyəcək. Ən etibarlı həll: HDMI-yə qoşulan "
                            + "sertifikatlı TV stick (Google TV / Android TV / Fire TV)." + extra, null, null);
        }
    }

    /** {level or null, extra text}. Never throws. */
    @SuppressWarnings("deprecation")
    private static String[] readDrm() {
        MediaDrm drm = null;
        try {
            if (!MediaDrm.isCryptoSchemeSupported(WIDEVINE)) return new String[]{null, ""};
            drm = new MediaDrm(WIDEVINE);
            String level = safeProp(drm, "securityLevel");
            String version = safeProp(drm, "version");
            String extra = version != null ? "\nWidevine versiyası: " + version : "";
            return new String[]{level != null ? level.trim() : "L3", extra};
        } catch (Throwable t) {
            return new String[]{null, ""};
        } finally {
            if (drm != null) {
                try {
                    drm.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String safeProp(MediaDrm d, String key) {
        try {
            return d.getPropertyString(key);
        } catch (Throwable t) {
            return null;
        }
    }
}
