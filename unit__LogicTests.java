package it.digiuno.app;

import java.util.ArrayList;
import java.util.List;

/** Prove automatiche della logica pura. Se una fallisce, la costruzione dell'app si ferma. */
final class LogicTests {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(boolean ok, String name) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FALLITA: " + name);
        }
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    public static void main(String[] args) {
        phases();
        autophagy();
        calc();
        ketosis();
        history();
        formats();
        System.out.println("Prove riuscite: " + passed + ", fallite: " + failed);
        if (failed > 0) System.exit(1);
    }

    private static void phases() {
        check(Phases.indexAt(0) == 0, "fase 0h");
        check(Phases.indexAt(3.99) == 0, "fase 3.99h");
        check(Phases.indexAt(4) == 1, "fase 4h");
        check(Phases.indexAt(12) == 3, "fase 12h");
        check(Phases.indexAt(23.9) == 4, "fase 23.9h");
        check(Phases.indexAt(24) == 5, "fase 24h");
        check(Phases.indexAt(100) == Phases.ALL.length - 1, "fase oltre 72h");
        check(Phases.indexAt(-5) == 0, "fase negativa");
        check(near(Phases.nextStart(0), 4, 1e-9), "prossima dopo 0");
        check(Phases.nextStart(Phases.ALL.length - 1) == -1, "nessuna dopo l'ultima");
        for (int i = 1; i < Phases.ALL.length; i++) {
            check(Phases.ALL[i].startHour > Phases.ALL[i - 1].startHour, "fasi in ordine " + i);
        }
        for (Phase p : Phases.ALL) {
            check(p.name.length() > 0 && p.body.length() > 20 && p.shortBody.length() > 5, "testi fase " + p.name);
        }
    }

    private static void autophagy() {
        check(Autophagy.progress(0) == 0, "autofagia 0h");
        check(Autophagy.progress(24) == 0, "autofagia 24h");
        check(near(Autophagy.progress(36), 0.5, 1e-9), "autofagia 36h");
        check(Autophagy.progress(48) == 1, "autofagia 48h");
        check(Autophagy.progress(200) == 1, "autofagia oltre");
        check(Autophagy.status(10).startsWith("Non ancora"), "stato <24");
        check(Autophagy.status(30).contains("iniziale"), "stato 24-48");
        check(Autophagy.status(60).contains("avanzata"), "stato >48");
    }

    private static void calc() {
        check(near(Calc.bmi(180, 81), 25.0, 1e-9), "BMI 180/81");
        check(Calc.bmiLabel(17).equals("sottopeso"), "BMI sottopeso");
        check(Calc.bmiLabel(22).equals("nella norma"), "BMI normale");
        check(Calc.bmiLabel(27).equals("sovrappeso"), "BMI sovrappeso");
        check(Calc.bmiLabel(33).equals("obesità"), "BMI obesità");
        check(Calc.waterGoalMl(70) == 2300, "acqua 70kg");
        check(Calc.waterGoalMl(30) == 1500, "acqua minima");
        check(Calc.waterGoalMl(200) == 3500, "acqua massima");
        check(near(Calc.bmrPerDay(true, 80, 180, 40), 1730, 1e-9), "BMR uomo");
        check(near(Calc.bmrPerDay(false, 60, 165, 30), 1320.25, 1e-9), "BMR donna");
        check(Calc.validHeight(170) && !Calc.validHeight(50) && !Calc.validHeight(300), "altezza valida");
        check(Calc.validWeight(70) && !Calc.validWeight(10) && !Calc.validWeight(500), "peso valido");
        check(Calc.validAge(51) && !Calc.validAge(17) && !Calc.validAge(150), "età valida");
        check(Calc.clamp(5, 0, 3) == 3 && Calc.clamp(-1, 0, 3) == 0, "clamp");
    }

    private static void ketosis() {
        double rate = 72;
        check(Ketosis.used(0, rate) == 0, "uso 0h");
        check(Ketosis.used(5, 0) == 0, "uso a ritmo 0");
        double prev = -1;
        boolean monotone = true;
        for (double h = 0; h <= 100; h += 0.5) {
            double u = Ketosis.used(h, rate);
            if (u < prev) monotone = false;
            prev = u;
        }
        check(monotone, "uso crescente nel tempo");
        double d = Ketosis.depletionHours(rate);
        check(d >= 12 && d <= 16, "esaurimento a riposo tra 12 e 16 ore: " + d);
        check(near(Ketosis.used(d, rate), Ketosis.RESERVE_KCAL, 1.0), "uso all'esaurimento = scorta");
        check(Ketosis.depletionHours(150) < Ketosis.depletionHours(60), "più consumo, esaurimento prima");
        check(Ketosis.depletionHours(1000) == 8, "limite minimo 8h");
        check(Ketosis.depletionHours(1) == 30, "limite massimo 30h");
        check(Ketosis.depletionHours(0) == 16, "ritmo zero = 16h");
        check(near(Ketosis.markedKetosisHours(14), 22, 1e-9), "chetosi marcata +8h");
        check(Ketosis.markedKetosisHours(30) == 38 && Ketosis.markedKetosisHours(45) == 48, "chetosi marcata massimo 48h");
        check(Ketosis.usedPercent(0, rate) == 0, "percentuale 0h");
        check(Ketosis.usedPercent(40, rate) == 100, "percentuale tetto 100");
        double p7 = Ketosis.usedPercent(7, rate);
        check(p7 > 20 && p7 < 70, "percentuale 7h plausibile: " + p7);
        check(near(Ketosis.effectiveRate(70, 40), 80, 1e-9), "ritmo efficace");
        check(near(Ketosis.effectiveRate(-5, -5), 0, 1e-9), "ritmo efficace non negativo");
        check(Ketosis.sanitizeRest(70, 72) == 70, "dato dispositivo plausibile");
        check(Ketosis.sanitizeRest(5, 72) == 72, "dato troppo basso scartato");
        check(Ketosis.sanitizeRest(500, 72) == 72, "dato troppo alto scartato");
    }

    private static void history() {
        List<HistoryEntry> list = new ArrayList<HistoryEntry>();
        list.add(new HistoryEntry(1000, 1000 + 16 * 3600000L, 16));
        list.add(new HistoryEntry(5000, 5000 + 10 * 3600000L, 16));
        String s = HistoryCodec.encode(list);
        List<HistoryEntry> back = HistoryCodec.decode(s);
        check(back.size() == 2, "storico: due voci");
        check(back.get(0).start == 1000 && near(back.get(0).hours(), 16, 1e-9), "storico: prima voce");
        check(back.get(0).reached(), "storico: obiettivo raggiunto");
        check(!back.get(1).reached(), "storico: obiettivo non raggiunto");
        check(HistoryCodec.decode(null).isEmpty(), "storico: null");
        check(HistoryCodec.decode("").isEmpty(), "storico: vuoto");
        check(HistoryCodec.decode("abc;1,2;x,y,z;5,3,16;1,2,0").isEmpty(), "storico: voci rovinate scartate");
        check(HistoryCodec.decode("1000,2000,16.00;rotto;3000,9000,12.50").size() == 2, "storico: salta solo la rovinata");
        List<HistoryEntry> big = new ArrayList<HistoryEntry>();
        for (int i = 0; i < 5; i++) big = HistoryCodec.add(big, new HistoryEntry(i, i + 10, 1), 3);
        check(big.size() == 3 && big.get(0).start == 2, "storico: tiene solo le ultime");
    }

    private static void formats() {
        check(Fmt.hms(0).equals("00:00:00"), "hms 0");
        check(Fmt.hms(3661000).equals("01:01:01"), "hms 1h1m1s");
        check(Fmt.hms(-5).equals("00:00:00"), "hms negativo");
        check(Fmt.hms(100 * 3600000L).equals("100:00:00"), "hms oltre 99 ore");
        check(Fmt.hm(16).equals("16 h"), "hm 16");
        check(Fmt.hm(16.5).equals("16 h 30 min"), "hm 16.5");
        check(Fmt.hm(0.25).equals("15 min"), "hm 15 min");
        check(Fmt.num(23.456, 1).equals("23,5"), "num italiano");
    }
}
