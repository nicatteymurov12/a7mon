package az.rahattv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Rahat TV's own settings and the "make it the Home screen" helper. */
public class SettingsActivity extends Activity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this, "Rahat TV — ayarlar", null);
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

    private void build() {
        box.removeAllViews();
        String home = Apps.defaultHome(this);
        boolean isHome = getPackageName().equals(home);

        // 1. Home screen
        FrameLayout h = Ui.row(this, isHome ? Ui.OK : Ui.WARN,
                isHome ? "Rahat TV əsas menyudur" : "Rahat TV-ni əsas menyu et",
                isHome ? "Home düyməsi bu menyunu açır. Köhnə menyu ana ekrandakı \"TV / Mənbə\" plitəsindədir."
                        : "OK basın. Sual çıxsa \"Rahat TV\" seçin, sonra \"Həmişə\" (Always). "
                        + "Sual çıxmırsa, köhnə menyunun \"Tətbiq məlumatı\" açılacaq — orada \"Defoltları təmizlə\" basın və Home düyməsini basın.",
                isHome ? null : "OK");
        h.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { makeHome(); }
        });
        Ui.addRow(box, h);

        // 2. start on boot
        final boolean boot = Prefs.bootStart(this);
        FrameLayout bt = Ui.row(this, 0, "TV yananda Rahat TV-ni aç: " + (boot ? "AÇIQ" : "BAĞLI"),
                "Home düyməsi köhnə menyunu açmağa davam edirsə bunu yandırın.", boot ? "Söndür" : "Yandır");
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setBootStart(SettingsActivity.this, !boot);
                build();
                focusRow(1);
            }
        });
        Ui.addRow(box, bt);

        // 3. hidden apps
        final int hidden = Prefs.hidden(this).size();
        FrameLayout hd = Ui.row(this, 0, "Gizlədilmiş tətbiqlər: " + hidden,
                hidden > 0 ? "Hamısını yenidən göstərmək üçün OK basın." : "Ana ekranda tətbiqin üstündə MENU → \"Gizlət\".",
                hidden > 0 ? "Göstər" : null);
        hd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hidden == 0) return;
                Prefs.clearHidden(SettingsActivity.this);
                Apps.toast(SettingsActivity.this, "Bütün tətbiqlər yenidən göstərilir");
                build();
                focusRow(2);
            }
        });
        Ui.addRow(box, hd);

        // 4. favourites
        final int favs = Prefs.favorites(this).size();
        FrameLayout fv = Ui.row(this, 0, "Sevimlilər: " + favs,
                favs > 0 ? "Siyahını təmizləmək üçün OK basın." : "Ana ekranda tətbiqin üstündə MENU → \"Sevimlilərə əlavə et\".",
                favs > 0 ? "Təmizlə" : null);
        fv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (favs == 0) return;
                Prefs.saveFavorites(SettingsActivity.this, new java.util.ArrayList<String>());
                build();
                focusRow(3);
            }
        });
        Ui.addRow(box, fv);

        // 5. animations
        FrameLayout an = Ui.row(this, 0, "TV-ni daha cəld et (animasiyalar)",
                "Tərtibatçı seçimləri → \"Pəncərə animasiya miqyası\", \"Keçid animasiya miqyası\", \"Animator müddəti\" — hər üçünü 0.5x edin.\n"
                        + "Bu bölmə görünmürsə: Parametrlər → Haqqında → \"Build nömrəsi\"nin üstündə 7 dəfə OK basın.",
                "Aç");
        an.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Apps.openSettings(SettingsActivity.this, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS); }
        });
        Ui.addRow(box, an);

        // 6. about
        TextView about = Ui.text(this, "Rahat TV " + BuildInfo.VERSION + " · Android 7+ TV üçün yüngül menyu. "
                + "Sistem fayllarına toxunmur — silmək üçün: Parametrlər → Tətbiqlər → Rahat TV → Sil.", 12, Ui.MUTED, false);
        box.addView(about, Ui.lpMargins(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 18, 0, 0));

        if (getCurrentFocus() == null && box.getChildCount() > 0) box.getChildAt(0).requestFocus();
    }

    private void focusRow(int i) {
        if (i < box.getChildCount()) box.getChildAt(i).requestFocus();
    }

    private void makeHome() {
        String home = Apps.defaultHome(this);
        if (getPackageName().equals(home)) {
            Apps.toast(this, "Rahat TV artıq əsas menyudur");
            return;
        }
        if (home == null || "android".equals(home)) {
            // No default yet: pressing Home shows the "choose a launcher" question.
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            if (Apps.start(this, i)) return;
        }
        // Another launcher is the default: Android 7 has a Home-app screen on some TVs…
        if (Apps.start(this, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        // …otherwise its defaults must be cleared in its App info.
        List<android.content.pm.ResolveInfo> homes = Apps.otherHomes(this);
        String target = home;
        if ((target == null || "android".equals(target)) && !homes.isEmpty()) {
            target = homes.get(0).activityInfo.packageName;
        }
        if (target != null && !"android".equals(target)) {
            Apps.toast(this, "\"Defoltları təmizlə\" (Clear defaults) basın, sonra Home düyməsini basın");
            Apps.appInfo(this, target);
        } else {
            Apps.openSettings(this, Settings.ACTION_SETTINGS);
        }
    }
}
