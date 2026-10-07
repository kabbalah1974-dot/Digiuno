package it.digiuno.app;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * Collegamento facoltativo con l'app Passi. Digiuno chiede i dati solo quando l'utente accende il collegamento
 * per il digiuno in corso; Passi risponde solo a Digiuno (stesso nome, stessa firma).
 */
final class PassiLink {
    static final String AUTHORITY = "it.passi.app.data";

    private PassiLink() {}

    static boolean installed(Context c) {
        try {
            return c.getPackageManager().resolveContentProvider(AUTHORITY, 0) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Lettura bloccante: da chiamare fuori dal thread principale. Null se Passi non risponde. */
    static Bundle summary(Context c, long startMs) {
        try {
            Bundle extras = new Bundle();
            extras.putLong("startMs", startMs);
            return c.getContentResolver().call(Uri.parse("content://" + AUTHORITY), "summary", null, extras);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Aggiunge a d i dati di Passi: calorie attive dall'inizio del digiuno e passi di oggi. */
    static void fill(Context c, HealthData d, long startMs) {
        if (!installed(c)) {
            d.linkState = 2;
            return;
        }
        Bundle b = summary(c, startMs);
        if (b == null) {
            d.linkState = 3;
            return;
        }
        if (!b.getBoolean("ok", false)) {
            d.linkState = "no_profile".equals(b.getString("reason")) ? 4 : 3;
            return;
        }
        d.linkState = 1;
        d.linkSensorOk = b.getBoolean("sensorOk", true);
        d.linkSampleMs = b.getLong("sampleMs", 0);
        d.stepsToday = b.getLong("stepsToday", 0);
        d.activeKcalSinceStart = Math.max(0, b.getDouble("sinceKcal", 0));
        // le calorie totali di Samsung Health non si mescolano con quelle di Passi
        d.totalKcalSinceStart = null;
        d.kcalSource = "Passi";
    }
}
