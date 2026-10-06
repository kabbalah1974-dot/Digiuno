package it.digiuno.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dopo il riavvio del telefono rimette in piedi allarmi e notifica del digiuno in corso. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        if (in == null || !Intent.ACTION_BOOT_COMPLETED.equals(in.getAction())) return;
        Store s = new Store(c);
        if (!s.fasting()) return;
        Notifier.createChannels(c);
        Notifier.scheduleAll(c, s);
        Notifier.showOngoing(c, s);
    }
}
