package it.digiuno.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riceve gli allarmi di sistema, anche con l'app chiusa. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        if (in == null || !Notifier.ACTION_ALARM.equals(in.getAction())) return;
        Store s = new Store(c);
        if (!s.fasting()) {
            Notifier.cancelOngoing(c);
            return;
        }
        int kind = in.getIntExtra(Notifier.EXTRA_KIND, 0);
        int idx = in.getIntExtra(Notifier.EXTRA_IDX, 0);
        if (kind == Notifier.K_PHASE) {
            Notifier.notifyPhase(c, idx);
            Notifier.showOngoing(c, s);
        } else if (kind == Notifier.K_GOAL) {
            Notifier.notifyGoal(c, s);
        } else if (kind == Notifier.K_WATER) {
            Notifier.notifyWater(c, s);
            Notifier.scheduleNextWater(c, s, System.currentTimeMillis());
        }
    }
}
