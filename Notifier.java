package it.digiuno.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

/** Notifiche e promemoria che funzionano anche ad app chiusa (allarmi di sistema). */
final class Notifier {
    static final String CH_ONGOING = "in_corso";
    static final String CH_ALERT = "avvisi";
    static final String ACTION_ALARM = "it.digiuno.app.ALARM";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_IDX = "idx";
    static final int K_PHASE = 1;
    static final int K_GOAL = 2;
    static final int K_WATER = 3;

    private static final int ID_ONGOING = 1;
    private static final int ID_PHASE = 10;
    private static final int ID_GOAL = 20;
    private static final int ID_WATER = 30;
    private static final int RC_PHASE = 1000;
    private static final int RC_GOAL = 2000;
    private static final int RC_WATER = 3000;
    private static final long WATER_EVERY_MS = 2 * 3600000L;
    private static final int GOLD = 0xFFD4AF6A;

    private Notifier() {}

    static void createChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ongoing = new NotificationChannel(CH_ONGOING, "Digiuno in corso", NotificationManager.IMPORTANCE_LOW);
        ongoing.setDescription("Mostra il tempo trascorso e la fase attuale");
        NotificationChannel alerts = new NotificationChannel(CH_ALERT, "Fasi e promemoria", NotificationManager.IMPORTANCE_DEFAULT);
        alerts.setDescription("Avvisi quando cambia la fase, a fine digiuno e per bere");
        nm.createNotificationChannel(ongoing);
        nm.createNotificationChannel(alerts);
    }

    private static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void post(Context c, int id, Notification n) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, n);
    }

    // ---- notifica fissa con cronometro ----
    static void showOngoing(Context c, Store s) {
        if (!s.fasting() || !canNotify(c)) return;
        createChannels(c);
        double h = s.elapsedHours(System.currentTimeMillis());
        Phase ph = Phases.ALL[Phases.indexAt(h)];
        Notification n = new Notification.Builder(c, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Digiuno in corso · " + ph.name)
            .setContentText(ph.shortBody)
            .setStyle(new Notification.BigTextStyle().bigText(ph.shortBody + " Obiettivo: " + Fmt.hm(s.goalHours()) + "."))
            .setWhen(s.startMs())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(GOLD)
            .setContentIntent(openApp(c))
            .build();
        post(c, ID_ONGOING, n);
    }

    static void cancelOngoing(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(ID_ONGOING);
    }

    // ---- avvisi ----
    static void notifyPhase(Context c, int idx) {
        if (!canNotify(c) || idx < 0 || idx >= Phases.ALL.length) return;
        createChannels(c);
        Phase ph = Phases.ALL[idx];
        Notification n = new Notification.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Nuova fase: " + ph.name)
            .setContentText(ph.shortBody)
            .setStyle(new Notification.BigTextStyle().bigText(ph.body))
            .setAutoCancel(true)
            .setColor(GOLD)
            .setContentIntent(openApp(c))
            .build();
        post(c, ID_PHASE, n);
    }

    static void notifyGoal(Context c, Store s) {
        if (!canNotify(c)) return;
        createChannels(c);
        String text = "Hai raggiunto le " + Fmt.hm(s.goalHours()) + ". Puoi chiudere il digiuno quando vuoi, "
            + "senza fretta: rompilo con qualcosa di leggero.";
        Notification n = new Notification.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Obiettivo raggiunto")
            .setContentText("Hai raggiunto le " + Fmt.hm(s.goalHours()) + ".")
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setColor(GOLD)
            .setContentIntent(openApp(c))
            .build();
        post(c, ID_GOAL, n);
    }

    static void notifyWater(Context c, Store s) {
        if (!canNotify(c)) return;
        createChannels(c);
        int goal = Calc.waterGoalMl(s.weightKg());
        Notification n = new Notification.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Un po' d'acqua?")
            .setContentText("Oggi sei a " + s.waterMl() + " di " + goal + " ml.")
            .setAutoCancel(true)
            .setColor(GOLD)
            .setContentIntent(openApp(c))
            .build();
        post(c, ID_WATER, n);
    }

    // ---- allarmi ----
    private static PendingIntent alarmIntent(Context c, int requestCode, int kind, int idx) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        i.putExtra(EXTRA_KIND, kind);
        i.putExtra(EXTRA_IDX, idx);
        return PendingIntent.getBroadcast(c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAlarm(Context c, long atMs, int requestCode, int kind, int idx) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, alarmIntent(c, requestCode, kind, idx));
    }

    static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < Phases.ALL.length; i++) am.cancel(alarmIntent(c, RC_PHASE + i, K_PHASE, i));
        am.cancel(alarmIntent(c, RC_GOAL, K_GOAL, 0));
        am.cancel(alarmIntent(c, RC_WATER, K_WATER, 0));
    }

    /** Programma i cambi di fase, la fine del digiuno e il primo promemoria dell'acqua. */
    static void scheduleAll(Context c, Store s) {
        cancelAll(c);
        if (!s.fasting()) return;
        scheduleMilestones(c, s);
        scheduleNextWater(c, s, System.currentTimeMillis());
    }

    /** Solo cambi di fase e fine digiuno (rimettere lo stesso allarme lo sostituisce, non lo duplica). */
    static void scheduleMilestones(Context c, Store s) {
        if (!s.fasting()) return;
        long now = System.currentTimeMillis();
        long start = s.startMs();
        for (int i = 1; i < Phases.ALL.length; i++) {
            long at = start + (long) (Phases.ALL[i].startHour * 3600000L);
            if (at > now + 5000) setAlarm(c, at, RC_PHASE + i, K_PHASE, i);
        }
        long goalAt = start + (long) (s.goalHours() * 3600000L);
        if (goalAt > now + 5000) setAlarm(c, goalAt, RC_GOAL, K_GOAL, 0);
    }

    /** Prossimo promemoria acqua: ogni 2 ore, solo tra le 8 e le 22, e solo finché dura il digiuno. */
    static void scheduleNextWater(Context c, Store s, long afterMs) {
        if (!s.fasting() || !s.waterReminders()) return;
        long at = nextWaterTime(afterMs + WATER_EVERY_MS);
        long end = s.startMs() + (long) (s.goalHours() * 3600000L);
        if (at > end) return;
        setAlarm(c, at, RC_WATER, K_WATER, 0);
    }

    /** Se l'orario cade di notte, lo sposta alle 8:00 del mattino. */
    static long nextWaterTime(long candidateMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(candidateMs);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if (hour >= 22) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else if (hour < 8) {
            cal.set(Calendar.HOUR_OF_DAY, 8);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        return cal.getTimeInMillis();
    }
}
