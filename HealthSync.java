package it.digiuno.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.health.connect.datatypes.WeightRecord;
import android.health.connect.datatypes.units.Energy;
import android.os.Build;
import android.os.OutcomeReceiver;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Dati letti da Samsung Health tramite Health Connect (solo lettura). */
final class HealthData {
    boolean available;      // Health Connect presente sul telefono
    boolean permitted;      // almeno un permesso concesso
    Double heightCm;
    Double weightKg;
    Long lastBpm;
    long lastBpmTimeMs;
    Long stepsToday;
    Long sleepMsLast24h;
    Double activeKcalSinceStart;
    Double totalKcalSinceStart;

    boolean hasAnything() {
        return heightCm != null || weightKg != null || lastBpm != null || stepsToday != null
            || sleepMsLast24h != null || activeKcalSinceStart != null || totalKcalSinceStart != null;
    }
}

final class HealthSync {
    static final String[] PERMS = {
        "android.permission.health.READ_HEIGHT",
        "android.permission.health.READ_WEIGHT",
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_SLEEP",
        "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
        "android.permission.health.READ_TOTAL_CALORIES_BURNED"
    };

    private HealthSync() {}

    /** Health Connect è integrato in Android solo dalla versione 14. */
    static boolean supported() { return Build.VERSION.SDK_INT >= 34; }

    static boolean anyGranted(Context c) {
        if (!supported()) return false;
        for (String p : PERMS) {
            if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }

    static boolean allGranted(Context c) {
        if (!supported()) return false;
        for (String p : PERMS) {
            if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    static void request(Activity a, int code) {
        if (supported()) a.requestPermissions(PERMS, code);
    }

    /** Apre la schermata di Android dove si gestiscono i permessi di salute dell'app. */
    static void openSettings(Context c) {
        try {
            Intent i = new Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS");
            i.putExtra(Intent.EXTRA_PACKAGE_NAME, c.getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (RuntimeException e) {
            // schermata non disponibile: non si fa nulla
        }
    }

    /** Lettura bloccante: da chiamare fuori dal thread principale. fastStartMs = 0 se non c'è un digiuno. */
    static HealthData read(Context c, long fastStartMs) {
        HealthData d = new HealthData();
        if (!supported()) return d;
        Object svc = c.getSystemService("healthconnect");
        if (!(svc instanceof HealthConnectManager)) return d;
        d.available = true;
        d.permitted = anyGranted(c);
        if (!d.permitted) return d;
        new Reader((HealthConnectManager) svc, c).fill(d, fastStartMs);
        return d;
    }

    private interface Call<R> {
        void run(HealthConnectManager m, Executor ex, OutcomeReceiver<R, HealthConnectException> rcv);
    }

    private static final class Reader {
        private static final Executor DIRECT = new Executor() {
            @Override public void execute(Runnable r) { r.run(); }
        };
        private final HealthConnectManager m;
        private final Context ctx;

        Reader(HealthConnectManager m, Context ctx) {
            this.m = m;
            this.ctx = ctx;
        }

        private boolean has(String perm) {
            return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
        }

        @SuppressWarnings("unchecked")
        private <R> R call(Call<R> c) throws Exception {
            final CountDownLatch latch = new CountDownLatch(1);
            final Object[] out = new Object[2];
            c.run(m, DIRECT, new OutcomeReceiver<R, HealthConnectException>() {
                @Override public void onResult(R r) { out[0] = r; latch.countDown(); }
                @Override public void onError(HealthConnectException e) { out[1] = e; latch.countDown(); }
            });
            if (!latch.await(10, TimeUnit.SECONDS)) throw new Exception("timeout");
            if (out[1] != null) throw (Exception) out[1];
            return (R) out[0];
        }

        private static TimeInstantRangeFilter range(Instant from, Instant to) {
            return new TimeInstantRangeFilter.Builder().setStartTime(from).setEndTime(to).build();
        }

        private <T extends Record> List<T> latest(final Class<T> type, Duration back, int pageSize) throws Exception {
            final Instant now = Instant.now();
            final TimeInstantRangeFilter f = range(now.minus(back), now);
            final int size = pageSize;
            ReadRecordsResponse<T> r = this.<ReadRecordsResponse<T>>call(new Call<ReadRecordsResponse<T>>() {
                @Override
                public void run(HealthConnectManager mm, Executor ex, OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException> rcv) {
                    mm.readRecords(new ReadRecordsRequestUsingFilters.Builder<T>(type)
                        .setTimeRangeFilter(f).setAscending(false).setPageSize(size).build(), ex, rcv);
                }
            });
            return r.getRecords();
        }

        private <T> T aggregate(final AggregationType<T> type, Instant from, Instant to) throws Exception {
            final TimeInstantRangeFilter f = range(from, to);
            AggregateRecordsResponse<T> r = this.<AggregateRecordsResponse<T>>call(new Call<AggregateRecordsResponse<T>>() {
                @Override
                public void run(HealthConnectManager mm, Executor ex, OutcomeReceiver<AggregateRecordsResponse<T>, HealthConnectException> rcv) {
                    mm.aggregate(new AggregateRecordsRequest.Builder<T>(f).addAggregationType(type).build(), ex, rcv);
                }
            });
            return r.get(type);
        }

        void fill(HealthData d, long fastStartMs) {
            Instant now = Instant.now();

            if (has("android.permission.health.READ_HEIGHT")) {
                try {
                    List<HeightRecord> l = latest(HeightRecord.class, Duration.ofDays(3650), 1);
                    if (!l.isEmpty()) {
                        double cm = l.get(0).getHeight().getInMeters() * 100.0;
                        if (Calc.validHeight(cm)) d.heightCm = cm;
                    }
                } catch (Exception ignored) { }
            }
            if (has("android.permission.health.READ_WEIGHT")) {
                try {
                    List<WeightRecord> l = latest(WeightRecord.class, Duration.ofDays(3650), 1);
                    if (!l.isEmpty()) {
                        double kg = l.get(0).getWeight().getInGrams() / 1000.0;
                        if (Calc.validWeight(kg)) d.weightKg = kg;
                    }
                } catch (Exception ignored) { }
            }
            if (has("android.permission.health.READ_HEART_RATE")) {
                try {
                    List<HeartRateRecord> l = latest(HeartRateRecord.class, Duration.ofHours(48), 5);
                    long bestTime = -1;
                    for (HeartRateRecord r : l) {
                        for (HeartRateRecord.HeartRateSample s : r.getSamples()) {
                            long t = s.getTime().toEpochMilli();
                            if (t > bestTime) {
                                bestTime = t;
                                d.lastBpm = s.getBeatsPerMinute();
                                d.lastBpmTimeMs = t;
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }
            if (has("android.permission.health.READ_STEPS")) {
                try {
                    Instant midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
                    Long steps = aggregate(StepsRecord.STEPS_COUNT_TOTAL, midnight, now);
                    d.stepsToday = steps == null ? 0L : steps;
                } catch (Exception ignored) { }
            }
            if (has("android.permission.health.READ_SLEEP")) {
                try {
                    Long ms = aggregate(SleepSessionRecord.SLEEP_DURATION_TOTAL, now.minus(Duration.ofHours(24)), now);
                    d.sleepMsLast24h = ms == null ? 0L : ms;
                } catch (Exception ignored) { }
            }
            if (fastStartMs > 0 && fastStartMs < now.toEpochMilli()) {
                Instant from = Instant.ofEpochMilli(fastStartMs);
                if (has("android.permission.health.READ_ACTIVE_CALORIES_BURNED")) {
                    try {
                        Energy e = aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, from, now);
                        if (e != null) d.activeKcalSinceStart = e.getInCalories() / 1000.0;
                    } catch (Exception ignored) { }
                }
                if (has("android.permission.health.READ_TOTAL_CALORIES_BURNED")) {
                    try {
                        Energy e = aggregate(TotalCaloriesBurnedRecord.ENERGY_TOTAL, from, now);
                        if (e != null) d.totalKcalSinceStart = e.getInCalories() / 1000.0;
                    } catch (Exception ignored) { }
                }
            }
        }
    }
}
