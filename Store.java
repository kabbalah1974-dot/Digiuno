package it.digiuno.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Tutti i dati restano sul telefono, in preferenze private dell'app. */
final class Store {
    private static final int MAX_HISTORY = 200;
    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("digiuno", Context.MODE_PRIVATE);
    }

    // ---- profilo ----
    boolean hasProfile() { return p.contains("heightCm") && p.contains("weightKg") && p.contains("age"); }
    double heightCm() { return Double.longBitsToDouble(p.getLong("heightCm", Double.doubleToLongBits(170))); }
    double weightKg() { return Double.longBitsToDouble(p.getLong("weightKg", Double.doubleToLongBits(70))); }
    int age() { return p.getInt("age", 40); }
    boolean male() { return p.getBoolean("male", true); }

    void saveProfile(double heightCm, double weightKg, int age, boolean male) {
        p.edit()
            .putLong("heightCm", Double.doubleToLongBits(heightCm))
            .putLong("weightKg", Double.doubleToLongBits(weightKg))
            .putInt("age", age)
            .putBoolean("male", male)
            .apply();
    }

    boolean accepted() { return p.getBoolean("accepted", false); }
    void setAccepted() { p.edit().putBoolean("accepted", true).apply(); }

    // ---- digiuno in corso ----
    boolean fasting() { return p.getBoolean("fasting", false) && p.getLong("startMs", 0) > 0; }
    long startMs() { return p.getLong("startMs", 0); }
    double goalHours() { return Double.longBitsToDouble(p.getLong("goalHours", Double.doubleToLongBits(16))); }
    double lastGoal() { return Double.longBitsToDouble(p.getLong("lastGoal", Double.doubleToLongBits(16))); }

    void startFast(long startMs, double goalHours) {
        p.edit()
            .putBoolean("fasting", true)
            .putLong("startMs", startMs)
            .putLong("goalHours", Double.doubleToLongBits(goalHours))
            .putLong("lastGoal", Double.doubleToLongBits(goalHours))
            .apply();
    }

    void endFast() { p.edit().putBoolean("fasting", false).apply(); }

    double elapsedHours(long nowMs) {
        if (!fasting()) return 0;
        return Math.max(0, (nowMs - startMs()) / 3600000.0);
    }

    // ---- storico ----
    List<HistoryEntry> history() { return HistoryCodec.decode(p.getString("history", "")); }

    void addHistory(HistoryEntry e) {
        List<HistoryEntry> l = HistoryCodec.add(history(), e, MAX_HISTORY);
        p.edit().putString("history", HistoryCodec.encode(l)).apply();
    }

    void clearHistory() { p.edit().remove("history").apply(); }

    // ---- acqua ----
    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    int waterMl() {
        return today().equals(p.getString("waterDay", "")) ? p.getInt("waterMl", 0) : 0;
    }

    void addWater(int delta) {
        int v = Math.max(0, Math.min(10000, waterMl() + delta));
        p.edit().putString("waterDay", today()).putInt("waterMl", v).apply();
    }

    boolean waterReminders() { return p.getBoolean("waterReminders", true); }
    void setWaterReminders(boolean on) { p.edit().putBoolean("waterReminders", on).apply(); }
}
