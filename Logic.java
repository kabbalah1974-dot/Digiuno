package it.digiuno.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Logica pura dell'app (nessuna dipendenza da Android): fasi, stime, storico, formati. */

final class Phase {
    final double startHour;
    final String name;
    final String shortBody;
    final String body;

    Phase(double startHour, String name, String shortBody, String body) {
        this.startHour = startHour;
        this.name = name;
        this.shortBody = shortBody;
        this.body = body;
    }
}

final class Phases {
    static final Phase[] ALL = {
        new Phase(0, "Digestione",
            "Stai usando gli zuccheri dell'ultimo pasto.",
            "Il corpo sta digerendo e usa gli zuccheri del pasto come carburante. L'insulina è alta: è la fase in cui il corpo accumula energia."),
        new Phase(4, "Zuccheri in calo",
            "L'insulina comincia a scendere.",
            "L'assorbimento del pasto finisce e l'insulina scende. Il corpo comincia a usare il glicogeno, la scorta di zuccheri immagazzinata nel fegato."),
        new Phase(8, "Insulina bassa",
            "Il corpo si prepara a usare i grassi.",
            "Con l'insulina bassa il corpo comincia a liberare i grassi di riserva. È normale sentire fame a ondate: spesso passa in poco tempo, e bere acqua aiuta."),
        new Phase(12, "Uso dei grassi",
            "I grassi diventano il carburante principale.",
            "Dopo circa 12 ore dall'ultimo pasto il corpo libera sempre più acidi grassi dal tessuto adiposo e li usa come energia. Il fegato comincia a produrre i primi chetoni, ancora in quantità bassa."),
        new Phase(16, "Chetosi leggera",
            "I chetoni salgono piano piano.",
            "Le scorte di glicogeno del fegato calano molto e i chetoni, un carburante ricavato dai grassi, aumentano gradualmente. Alcune persone notano più lucidità mentale, ma varia da persona a persona."),
        new Phase(24, "Chetosi in aumento",
            "Le scorte di zuccheri sono quasi finite.",
            "Il glicogeno del fegato è quasi esaurito e il corpo mantiene da solo la glicemia, producendo glucosio da altre fonti. I chetoni continuano a salire. Da qui in poi bevi con regolarità e ascolta il corpo."),
        new Phase(36, "Digiuno prolungato",
            "Grassi e chetoni sono il carburante principale.",
            "Il glicogeno del fegato non contribuisce più in modo significativo all'energia. È un digiuno lungo: se compaiono capogiri, nausea o debolezza forte, interrompilo."),
        new Phase(48, "Chetosi profonda",
            "I chetoni crescono in modo costante.",
            "Insulina molto bassa e chetosi più marcata. Digiuni di questa durata vanno affrontati solo con il consenso del medico, facendo attenzione a liquidi e sali minerali."),
        new Phase(72, "Oltre 72 ore",
            "Digiuno molto lungo.",
            "I chetoni sono alti e il cervello li usa in modo significativo. Un digiuno così lungo va fatto solo sotto controllo medico; per finire, rialimentati con gradualità.")
    };

    static int indexAt(double hours) {
        int idx = 0;
        for (int i = 0; i < ALL.length; i++) {
            if (hours >= ALL[i].startHour) idx = i;
        }
        return idx;
    }

    /** Ora di inizio della fase successiva, oppure -1 se è l'ultima. */
    static double nextStart(int idx) {
        return idx + 1 < ALL.length ? ALL[idx + 1].startHour : -1;
    }
}

final class Autophagy {
    static final double START_H = 24;
    static final double FULL_H = 48;
    static final String NOTE =
        "Nell'uomo l'autofagia non si può misurare con un'app e i tempi esatti non sono stabiliti: "
        + "i dati vengono soprattutto da studi sugli animali. Questa è solo un'indicazione orientativa.";

    static double progress(double hours) {
        return Calc.clamp((hours - START_H) / (FULL_H - START_H), 0, 1);
    }

    static String status(double hours) {
        if (hours < START_H) return "Non ancora: negli studi parte dopo circa 24 ore.";
        if (hours < FULL_H) return "Possibile fase iniziale (stima da studi sugli animali).";
        return "Possibile fase più avanzata (stima da studi sugli animali).";
    }
}

final class Calc {
    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double bmi(double cm, double kg) {
        double m = cm / 100.0;
        return kg / (m * m);
    }

    static String bmiLabel(double bmi) {
        if (bmi < 18.5) return "sottopeso";
        if (bmi < 25) return "nella norma";
        if (bmi < 30) return "sovrappeso";
        return "obesità";
    }

    /** Obiettivo acqua giornaliero in ml (circa 33 ml per kg, tra 1500 e 3500, arrotondato a 50). */
    static int waterGoalMl(double kg) {
        long ml = Math.round(kg * 33.0 / 50.0) * 50;
        return (int) Math.max(1500, Math.min(3500, ml));
    }

    /** Metabolismo basale (formula di Mifflin-St Jeor) in kcal al giorno. */
    static double bmrPerDay(boolean male, double kg, double cm, int age) {
        double b = 10 * kg + 6.25 * cm - 5 * age;
        return male ? b + 5 : b - 161;
    }

    static boolean validHeight(double cm) { return cm >= 100 && cm <= 250; }
    static boolean validWeight(double kg) { return kg >= 30 && kg <= 300; }
    static boolean validAge(int age) { return age >= 18 && age <= 110; }
}

/** Stima orientativa dell'esaurimento degli zuccheri del fegato e dell'inizio della chetosi. */
final class Ketosis {
    /** Scorta di glicogeno del fegato dopo un pasto misto: circa 100 g, cioè circa 400 kcal. */
    static final double RESERVE_KCAL = 400;
    /** Solo una parte delle calorie dell'attività pesa sul fegato (i muscoli usano il proprio glicogeno). */
    static final double ACTIVE_SHARE = 0.25;

    /** Kcal "di zuccheri del fegato" usate dopo certe ore, a ritmo costante. La quota di zuccheri scende da 0,5 a 0,2 in 24 ore. */
    static double used(double hours, double rateKcalPerHour) {
        if (hours <= 0 || rateKcalPerHour <= 0) return 0;
        double t = Math.min(hours, 24);
        double u = 0.5 * t - 0.00625 * t * t;
        if (hours > 24) u += 0.2 * (hours - 24);
        return rateKcalPerHour * u;
    }

    static double effectiveRate(double restKcalPerHour, double activeKcalPerHour) {
        return Math.max(0, restKcalPerHour) + ACTIVE_SHARE * Math.max(0, activeKcalPerHour);
    }

    /** Ore dopo le quali la scorta del fegato è finita, limitate tra 8 e 30. */
    static double depletionHours(double rateKcalPerHour) {
        if (rateKcalPerHour <= 0) return 16;
        double lo = 0, hi = 200;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (used(mid, rateKcalPerHour) < RESERVE_KCAL) lo = mid; else hi = mid;
        }
        return Calc.clamp(hi, 8, 30);
    }

    static double markedKetosisHours(double depletionHours) {
        return Math.min(depletionHours + 8, 48);
    }

    static double usedPercent(double hours, double rateKcalPerHour) {
        return Calc.clamp(used(hours, rateKcalPerHour) / RESERVE_KCAL * 100.0, 0, 100);
    }

    /** Usa il dato di Samsung Health solo se è plausibile rispetto alla formula. */
    static double sanitizeRest(double fromDevice, double fromFormula) {
        if (fromDevice >= 0.6 * fromFormula && fromDevice <= 1.6 * fromFormula) return fromDevice;
        return fromFormula;
    }
}

final class HistoryEntry {
    final long start;
    final long end;
    final double goalHours;

    HistoryEntry(long start, long end, double goalHours) {
        this.start = start;
        this.end = end;
        this.goalHours = goalHours;
    }

    double hours() { return Math.max(0, (end - start) / 3600000.0); }

    boolean reached() { return hours() >= goalHours - 0.0001; }
}

final class HistoryCodec {
    static String encode(List<HistoryEntry> list) {
        StringBuilder sb = new StringBuilder();
        for (HistoryEntry e : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.start).append(',').append(e.end).append(',')
              .append(String.format(Locale.US, "%.2f", e.goalHours));
        }
        return sb.toString();
    }

    static List<HistoryEntry> decode(String s) {
        List<HistoryEntry> out = new ArrayList<HistoryEntry>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            String[] f = part.split(",");
            if (f.length != 3) continue;
            try {
                long a = Long.parseLong(f[0].trim());
                long b = Long.parseLong(f[1].trim());
                double g = Double.parseDouble(f[2].trim());
                if (b >= a && g > 0) out.add(new HistoryEntry(a, b, g));
            } catch (NumberFormatException ignored) {
                // voce rovinata: si salta
            }
        }
        return out;
    }

    /** Aggiunge in fondo e tiene al massimo le ultime max voci. */
    static List<HistoryEntry> add(List<HistoryEntry> list, HistoryEntry e, int max) {
        List<HistoryEntry> out = new ArrayList<HistoryEntry>(list);
        out.add(e);
        while (out.size() > max) out.remove(0);
        return out;
    }
}

final class Fmt {
    static String hms(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /** Es. 16.5 -> "16 h 30 min", 16 -> "16 h", 0.25 -> "15 min". */
    static String hm(double hours) {
        long totalMin = Math.round(Math.max(0, hours) * 60);
        long h = totalMin / 60, m = totalMin % 60;
        if (h == 0) return m + " min";
        if (m == 0) return h + " h";
        return h + " h " + m + " min";
    }

    static String num(double v, int decimals) {
        return String.format(Locale.ITALY, "%." + decimals + "f", v);
    }
}
