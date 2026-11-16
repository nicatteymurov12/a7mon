package az.rahattv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Optional: opens Rahat TV a few seconds after the TV boots, for TVs whose
 * firmware ignores the chosen Home app. Off by default (Rahat TV → Ayarlar).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Prefs.bootStart(context)) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(app, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    app.startActivity(i);
                } catch (Throwable ignored) {
                } finally {
                    try {
                        pending.finish();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, 4000);
    }
}
