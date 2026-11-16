package az.rahattv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small persisted settings: favourites (ordered), hidden apps, start-on-boot. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("rahattv", Context.MODE_PRIVATE);
    }

    static List<String> favorites(Context c) {
        List<String> out = new ArrayList<>();
        String raw = sp(c).getString("favorites", "");
        for (String s : raw.split(",")) {
            s = s.trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    static boolean isFavorite(Context c, String pkg) {
        return favorites(c).contains(pkg);
    }

    static void toggleFavorite(Context c, String pkg) {
        List<String> f = favorites(c);
        if (!f.remove(pkg)) f.add(pkg);
        saveFavorites(c, f);
    }

    static void saveFavorites(Context c, List<String> f) {
        StringBuilder b = new StringBuilder();
        for (String s : f) {
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        sp(c).edit().putString("favorites", b.toString()).apply();
    }

    static Set<String> hidden(Context c) {
        return new HashSet<>(sp(c).getStringSet("hidden", new HashSet<String>()));
    }

    static void hide(Context c, String pkg) {
        Set<String> h = hidden(c);
        h.add(pkg);
        sp(c).edit().putStringSet("hidden", h).apply();
    }

    static void clearHidden(Context c) {
        sp(c).edit().remove("hidden").apply();
    }

    static boolean bootStart(Context c) {
        return sp(c).getBoolean("boot_start", false);
    }

    static void setBootStart(Context c, boolean on) {
        sp(c).edit().putBoolean("boot_start", on).apply();
    }
}
