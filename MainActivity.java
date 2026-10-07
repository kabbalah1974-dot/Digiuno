package it.digiuno.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 11;
    private static final int REQ_HC = 12;
    private static final int[] GOALS = {12, 14, 16, 18, 20, 24, 36, 48, 72};
    private static final String[] TAB_NAMES = {"Digiuno", "Corpo", "Acqua", "Storico", "Profilo"};
    private static final String[] OFFSET_LABELS = {"adesso", "1 ora fa", "2 ore fa", "3 ore fa", "4 ore fa", "6 ore fa"};
    private static final double[] OFFSET_HOURS = {0, 1, 2, 3, 4, 6};
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private Store store;
    private FrameLayout content;
    private LinearLayout tabBar;
    private final TextView[] tabViews = new TextView[TAB_NAMES.length];
    private int tab = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private HealthData health;
    private long healthTimeMs;
    private boolean loadingHealth;
    private double selGoal = 16;
    private int offsetIdx = 0;

    // schermata Digiuno
    private RingView ring;
    private TextView phaseName, phaseBody, nextInfo, autoStatus;
    private BarView autoBar;
    private LinearLayout setupBox, timelineBox;
    private Button mainBtn;
    private String shownKey = "";

    // schermata Corpo
    private TextView connText, ketoHead, ketoText, ketoNote;
    private Button connBtn, permBtn;
    private BarView ketoBar;
    private final TextView[] metricVals = new TextView[8];

    // schermata Acqua
    private TextView waterBig, waterSub;
    private BarView waterBar;

    // schermata Profilo
    private EditText inHeight, inWeight, inAge;
    private boolean sexMale = true;
    private TextView sexMaleBtn, sexFemaleBtn;
    private CheckBox acceptBox;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (tab == 0) refreshFast();
            handler.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------------------------ ciclo di vita

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        store = new Store(this);
        Notifier.createChannels(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURFACE);
        selGoal = store.lastGoal();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        tabBar = buildTabBar();
        root.addView(tabBar, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);

        if (!store.hasProfile() || !store.accepted()) showOnboarding(); else showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (store.fasting()) {
            Notifier.scheduleMilestones(this, store);
            Notifier.showOngoing(this, store);
        }
        if (tab == 1) loadHealth(false);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bg.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean any = false;
        for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) any = true;
        if (code == REQ_NOTIF) {
            if (!any) {
                toast("Senza notifiche non posso avvisarti a app chiusa. Puoi attivarle dalle impostazioni del telefono.");
            } else if (store.fasting()) {
                Notifier.showOngoing(this, store);
            }
        } else if (code == REQ_HC) {
            if (any) {
                loadHealth(inHeight != null && inHeight.isAttachedToWindow());
            } else {
                toast("Permesso non concesso. Puoi darlo dalle impostazioni di Health Connect.");
                renderBody();
            }
        }
    }

    // ------------------------------------------------------------------ struttura

    private LinearLayout buildTabBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Ui.SURFACE);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TAB_NAMES[i], 12, Ui.MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(14), 0, Ui.dp(14));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(idx); }
            });
            tabViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        return bar;
    }

    private void styleTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean sel = i == tab;
            tabViews[i].setTextColor(sel ? Ui.GOLD_LIGHT : Ui.MUTED);
            tabViews[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void showOnboarding() {
        tab = -1;
        tabBar.setVisibility(View.GONE);
        content.removeAllViews();
        content.addView(buildProfileScreen(true));
    }

    private void showTab(int t) {
        tab = t;
        tabBar.setVisibility(View.VISIBLE);
        styleTabs();
        content.removeAllViews();
        switch (t) {
            case 0:
                shownKey = "";
                content.addView(buildFastScreen());
                refreshSetup();
                refreshFast();
                break;
            case 1:
                content.addView(buildBodyScreen());
                renderBody();
                renderKeto();
                loadHealth(false);
                break;
            case 2:
                content.addView(buildWaterScreen());
                renderWater();
                break;
            case 3:
                content.addView(buildHistoryScreen());
                break;
            default:
                content.addView(buildProfileScreen(false));
                break;
        }
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(20), Ui.dp(26), Ui.dp(20), Ui.dp(28));
        return l;
    }

    private ScrollView scroll(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private View header(String title, String sub) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.addView(Ui.title(this, title, 30));
        if (sub != null && !sub.isEmpty()) h.addView(Ui.text(this, sub, 14, Ui.MUTED), Ui.full(2));
        return h;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    private static long dayOf(long ms) {
        return (ms + TimeZone.getDefault().getOffset(ms)) / 86400000L;
    }

    private String whenText(long ms) {
        long diff = dayOf(ms) - dayOf(System.currentTimeMillis());
        String hm = new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(ms));
        if (diff == 0) return "oggi alle " + hm;
        if (diff == 1) return "domani alle " + hm;
        if (diff == -1) return "ieri alle " + hm;
        return new SimpleDateFormat("d MMM", Locale.ITALY).format(new Date(ms)) + " alle " + hm;
    }

    private String todayText() {
        String s = new SimpleDateFormat("EEEE d MMMM", Locale.ITALY).format(new Date());
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ schermata Digiuno

    private String warningText() {
        StringBuilder sb = new StringBuilder();
        double bmi = Calc.bmi(store.heightCm(), store.weightKg());
        if (bmi < 18.5) {
            sb.append("Il tuo indice di massa corporea è basso: il digiuno è sconsigliato senza il parere del medico.");
        }
        if (store.age() >= 65) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("Dopo i 65 anni parla con il medico prima di digiunare, soprattutto oltre le 16 ore.");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private View buildFastScreen() {
        LinearLayout col = column();
        col.addView(header("Digiuno", todayText()));

        String warn = warningText();
        if (warn != null) {
            LinearLayout w = Ui.card(this);
            w.setBackground(Ui.rect(0x33E57373, 16, 0x66E57373));
            w.addView(Ui.text(this, warn, 13, Ui.TEXT));
            col.addView(w, Ui.full(14));
        }

        ring = new RingView(this);
        col.addView(wrapSquare(ring), Ui.full(10));

        LinearLayout phaseCard = Ui.card(this);
        phaseName = Ui.title(this, "", 22);
        phaseBody = Ui.text(this, "", 15, Ui.TEXT);
        nextInfo = Ui.text(this, "", 13, Ui.MUTED);
        phaseCard.addView(phaseName);
        phaseCard.addView(phaseBody, Ui.full(8));
        phaseCard.addView(nextInfo, Ui.full(10));
        col.addView(phaseCard, Ui.full(16));

        setupBox = Ui.card(this);
        col.addView(setupBox, Ui.full(12));

        mainBtn = Ui.button(this, "Inizia il digiuno", true);
        mainBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onMainPressed(); }
        });
        col.addView(mainBtn, Ui.full(14));

        LinearLayout auto = Ui.card(this);
        auto.addView(Ui.title(this, "Autofagia", 20));
        autoBar = new BarView(this, Ui.TEAL);
        auto.addView(autoBar, Ui.full(12));
        autoStatus = Ui.text(this, "", 14, Ui.TEXT);
        auto.addView(autoStatus, Ui.full(10));
        auto.addView(Ui.text(this, Autophagy.NOTE, 12, Ui.MUTED), Ui.full(8));
        col.addView(auto, Ui.full(12));

        timelineBox = Ui.card(this);
        col.addView(timelineBox, Ui.full(12));

        col.addView(Ui.text(this,
            "Digiuno è un'app di benessere, non un dispositivo medico. Interrompi se hai capogiri, nausea forte o malessere.",
            11, Ui.MUTED), Ui.full(16));
        return scroll(col);
    }

    private View wrapSquare(RingView r) {
        FrameLayout f = new FrameLayout(this);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(MATCH, WRAP);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        f.addView(r, p);
        return f;
    }

    private LinearLayout chipRow(int from, int to) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = from; i < to; i++) {
            final int idx = i;
            boolean custom = idx == GOALS.length;
            boolean customActive = true;
            for (int g : GOALS) if (g == selGoal) customActive = false;
            boolean sel = custom ? customActive : GOALS[idx] == selGoal;
            String label = custom ? (customActive ? Fmt.num(selGoal, selGoal == Math.rint(selGoal) ? 0 : 1) + "h" : "Altro") : GOALS[idx] + "h";
            TextView chip = Ui.text(this, label, 15, sel ? 0xFF1A1405 : Ui.GOLD_LIGHT);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setPadding(0, Ui.dp(11), 0, Ui.dp(11));
            chip.setBackground(sel ? Ui.rect(Ui.GOLD, 14, 0) : Ui.rect(Ui.SURFACE2, 14, 0x33D4AF6A));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (idx < GOALS.length) {
                        selGoal = GOALS[idx];
                        refreshSetup();
                        refreshFast();
                    } else {
                        askCustomGoal();
                    }
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            p.leftMargin = Ui.dp(i == from ? 0 : 6);
            row.addView(chip, p);
        }
        return row;
    }

    private void askCustomGoal() {
        final EditText e = Ui.input(this, "Ore (da 1 a 120)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), 0);
        box.addView(e);
        dialog().setTitle("Durata personalizzata").setView(box)
            .setPositiveButton("Imposta", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    Double v = parse(e.getText().toString());
                    if (v == null || v < 1 || v > 120) {
                        toast("Inserisci un numero di ore tra 1 e 120.");
                        return;
                    }
                    selGoal = v;
                    refreshSetup();
                    refreshFast();
                }
            })
            .setNegativeButton("Annulla", null).show();
    }

    private void refreshSetup() {
        if (setupBox == null) return;
        setupBox.removeAllViews();
        if (store.fasting()) {
            setupBox.addView(Ui.text(this, "Obiettivo: " + Fmt.hm(store.goalHours()), 17, Ui.TEXT));
            long end = store.startMs() + (long) (store.goalHours() * 3600000L);
            setupBox.addView(Ui.text(this, "Iniziato " + whenText(store.startMs()) + "\nFine prevista " + whenText(end), 13, Ui.MUTED), Ui.full(6));
        } else {
            setupBox.addView(Ui.text(this, "Quanto vuoi digiunare?", 17, Ui.TEXT));
            setupBox.addView(chipRow(0, 5), Ui.full(12));
            setupBox.addView(chipRow(5, GOALS.length + 1), Ui.full(8));
            String label = offsetIdx == 0
                ? "Hai già smesso di mangiare? Imposta l'orario di inizio"
                : "Inizio: " + OFFSET_LABELS[offsetIdx] + " · tocca per cambiare";
            TextView off = Ui.text(this, label, 13, Ui.GOLD);
            off.setPadding(0, Ui.dp(8), 0, 0);
            off.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { askOffset(); }
            });
            setupBox.addView(off, Ui.full(8));
        }
        setupBox.addView(linkRow(), Ui.full(14));
    }

    /** Interruttore del collegamento con Passi: vale solo per il digiuno in corso o per quello che stai per iniziare. */
    private View linkRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean installed = PassiLink.installed(this);
        String label = installed
            ? "Collega Passi per questo digiuno (calorie e passi veri)"
            : "Passi non è installata: collegamento non disponibile";
        row.addView(Ui.text(this, label, 13, installed ? Ui.TEXT : Ui.MUTED), new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (installed) {
            Switch sw = new Switch(this);
            sw.setChecked(store.linkOn());
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    store.setLink(on);
                    if (on) toast("Collegamento acceso: i dati si vedono nella scheda Corpo. Si spegne da solo a fine digiuno.");
                }
            });
            row.addView(sw);
        }
        return row;
    }

    private boolean wantLink() { return store.fasting() && store.linkOn(); }

    private String linkLine() {
        if (loadingHealth) return "Leggo i dati di Passi…";
        if (health == null) return "Collegamento con Passi acceso.";
        switch (health.linkState) {
            case 1:
                String s = health.linkSampleMs > 0
                    ? "Dati di Passi: ultimo aggiornamento alle " + new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(health.linkSampleMs)) + "."
                    : "Dati di Passi ricevuti.";
                if (!health.linkSensorOk) {
                    s += " Passi non ha il permesso «Attività fisica»: aprila e consentilo, così conta i passi.";
                }
                return s;
            case 2:
                return "Passi non è installata su questo telefono.";
            case 4:
                return "Apri Passi una volta e inserisci i tuoi dati.";
            default:
                return "Passi non risponde. Aprila una volta e poi tocca Aggiorna.";
        }
    }

    private void askOffset() {
        dialog().setTitle("Ho finito di mangiare…")
            .setItems(OFFSET_LABELS, new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int which) {
                    offsetIdx = which;
                    refreshSetup();
                }
            }).show();
    }

    private void refreshFast() {
        if (ring == null || phaseName == null) return;
        long now = System.currentTimeMillis();
        boolean fasting = store.fasting();
        double h = fasting ? store.elapsedHours(now) : 0;
        double goal = fasting ? store.goalHours() : selGoal;
        int idx = Phases.indexAt(h);
        String key = fasting ? "f" + idx : "idle";
        if (!key.equals(shownKey)) {
            shownKey = key;
            if (fasting) {
                phaseName.setText(Phases.ALL[idx].name);
                phaseBody.setText(Phases.ALL[idx].body);
            } else {
                phaseName.setText("Il tuo corpo, ora dopo ora");
                phaseBody.setText("Quando inizi il digiuno qui vedrai cosa succede nel tuo corpo e quali benefici arrivano a ogni fase.");
            }
            mainBtn.setText(fasting ? "Termina il digiuno" : "Inizia il digiuno");
            renderTimeline(fasting, idx);
        }
        if (fasting) {
            String sub = h >= goal ? "obiettivo raggiunto" : Math.round(h / goal * 100) + "% di " + Fmt.hm(goal);
            ring.setState(h, goal, true, Phases.ALL[idx].name, Fmt.hms(now - store.startMs()), sub);
            double ns = Phases.nextStart(idx);
            nextInfo.setText(ns > 0
                ? "Prossima fase: " + Phases.ALL[idx + 1].name + " tra " + Fmt.hm(ns - h)
                : "Sei nell'ultima fase.");
        } else {
            ring.setState(0, goal, false, "Pronto", "00:00:00", "obiettivo " + Fmt.hm(goal));
            nextInfo.setText("");
        }
        autoBar.setFraction(Autophagy.progress(h));
        autoStatus.setText(fasting ? Autophagy.status(h) : "Si valuta durante il digiuno.");
    }

    private void renderTimeline(boolean fasting, int idx) {
        timelineBox.removeAllViews();
        timelineBox.addView(Ui.title(this, "Le fasi", 20));
        for (int i = 0; i < Phases.ALL.length; i++) {
            Phase p = Phases.ALL[i];
            String mark;
            int color;
            if (fasting && i < idx) { mark = "✓"; color = Ui.TEAL; }
            else if (fasting && i == idx) { mark = "●"; color = Ui.GOLD; }
            else { mark = "○"; color = Ui.MUTED; }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView m = Ui.text(this, mark, 15, color);
            m.setGravity(Gravity.CENTER);
            row.addView(m, new LinearLayout.LayoutParams(Ui.dp(26), WRAP));
            TextView t = Ui.text(this, Fmt.hm(p.startHour) + " · " + p.name, 15, (fasting && i == idx) ? Ui.GOLD_LIGHT : Ui.TEXT);
            if (fasting && i == idx) t.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(t);
            timelineBox.addView(row, Ui.full(10));
        }
    }

    private void onMainPressed() {
        if (store.fasting()) {
            double h = store.elapsedHours(System.currentTimeMillis());
            dialog().setTitle("Terminare il digiuno?")
                .setMessage("Hai digiunato per " + Fmt.hm(h) + ".")
                .setPositiveButton("Termina", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { stopFast(); }
                })
                .setNegativeButton("Continua", null).show();
        } else if (selGoal > 24) {
            dialog().setTitle("Digiuno lungo")
                .setMessage("Oltre le 24 ore serve prudenza: parlane prima con il medico, soprattutto se hai problemi di salute o prendi farmaci. Vuoi iniziare?")
                .setPositiveButton("Inizia", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { startFast(); }
                })
                .setNegativeButton("Annulla", null).show();
        } else {
            startFast();
        }
    }

    private void startFast() {
        long start = System.currentTimeMillis() - (long) (OFFSET_HOURS[offsetIdx] * 3600000L);
        store.startFast(start, selGoal);
        offsetIdx = 0;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        Notifier.scheduleAll(this, store);
        Notifier.showOngoing(this, store);
        shownKey = "";
        refreshSetup();
        refreshFast();
    }

    private void stopFast() {
        long now = System.currentTimeMillis();
        HistoryEntry e = new HistoryEntry(store.startMs(), now, store.goalHours());
        if (e.hours() >= 0.05) store.addHistory(e);
        store.endFast();
        Notifier.cancelAll(this);
        Notifier.cancelOngoing(this);
        shownKey = "";
        refreshSetup();
        refreshFast();
        if (e.reached()) {
            dialog().setTitle("Obiettivo raggiunto")
                .setMessage("Hai digiunato " + Fmt.hm(e.hours()) + ". Rompi il digiuno con qualcosa di leggero e bevi con calma.")
                .setPositiveButton("Ok", null).show();
        }
    }

    // ------------------------------------------------------------------ schermata Corpo

    private LinearLayout metricRow(String[] labels, int a, int b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = a; i <= b; i++) {
            LinearLayout c = Ui.card(this);
            c.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
            c.addView(Ui.text(this, labels[i], 12, Ui.MUTED));
            TextView v = Ui.text(this, "—", 19, Ui.TEXT);
            v.setTypeface(Typeface.SERIF);
            metricVals[i] = v;
            c.addView(v, Ui.full(4));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            p.leftMargin = Ui.dp(i == a ? 0 : 10);
            row.addView(c, p);
        }
        return row;
    }

    private View buildBodyScreen() {
        LinearLayout col = column();
        col.addView(header("Corpo", "Dati di Samsung Health e stima"));

        LinearLayout cc = Ui.card(this);
        connText = Ui.text(this, "", 14, Ui.TEXT);
        cc.addView(connText);
        connBtn = Ui.button(this, "Collega Samsung Health", true);
        connBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onConnectPressed(); }
        });
        cc.addView(connBtn, Ui.full(12));
        permBtn = Ui.button(this, "Gestisci i permessi", false);
        permBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { HealthSync.openSettings(MainActivity.this); }
        });
        cc.addView(permBtn, Ui.full(8));
        cc.addView(Ui.text(this,
            "Per vedere i dati: in Samsung Health apri Impostazioni, cerca Health Connect e attiva la sincronizzazione. "
            + "Poi tocca il pulsante qui sopra e consenti l'accesso. L'app legge soltanto, non scrive nulla.",
            12, Ui.MUTED), Ui.full(10));
        col.addView(cc, Ui.full(16));

        String[] labels = {"Peso", "Altezza", "Indice BMI", "Battito (ultimo)", "Passi oggi", "Sonno (24 h)", "Calorie attive*", "Calorie totali*"};
        for (int r = 0; r < 4; r++) col.addView(metricRow(labels, r * 2, r * 2 + 1), Ui.full(10));
        col.addView(Ui.text(this, "* dall'inizio del digiuno in corso", 11, Ui.MUTED), Ui.full(6));

        LinearLayout k = Ui.card(this);
        k.addView(Ui.title(this, "La tua stima di chetosi", 20));
        ketoHead = Ui.text(this, "", 14, Ui.TEXT);
        k.addView(ketoHead, Ui.full(12));
        ketoBar = new BarView(this, Ui.GOLD);
        k.addView(ketoBar, Ui.full(8));
        ketoText = Ui.text(this, "", 15, Ui.TEXT);
        k.addView(ketoText, Ui.full(12));
        ketoNote = Ui.text(this, "", 12, Ui.MUTED);
        k.addView(ketoNote, Ui.full(12));
        col.addView(k, Ui.full(16));
        return scroll(col);
    }

    private void onConnectPressed() {
        if (!HealthSync.supported()) {
            loadHealth(false);
            return;
        }
        if (HealthSync.allGranted(this)) {
            loadHealth(false);
        } else {
            HealthSync.request(this, REQ_HC);
        }
    }

    private void loadHealth(final boolean fillProfile) {
        final boolean link = wantLink();
        if ((!HealthSync.supported() && !link) || loadingHealth) return;
        loadingHealth = true;
        renderBody();
        final long start = store.fasting() ? store.startMs() : 0;
        final Context app = getApplicationContext();
        bg.execute(new Runnable() {
            @Override public void run() {
                final HealthData d = HealthSync.read(app, start);
                if (link) PassiLink.fill(app, d, start);
                handler.post(new Runnable() {
                    @Override public void run() {
                        loadingHealth = false;
                        health = d;
                        healthTimeMs = System.currentTimeMillis();
                        if (isDestroyed()) return;
                        renderBody();
                        renderKeto();
                        if (fillProfile) fillProfileFromHealth();
                    }
                });
            }
        });
    }

    private void renderBody() {
        if (tab != 1 || connText == null) return;
        String status;
        boolean showConnect = true;
        String connectLabel = "Collega Samsung Health";
        boolean showPerm = false;
        if (!HealthSync.supported()) {
            status = "Il collegamento con Samsung Health usa Health Connect, che è integrato in Android 14 o più recente. "
                + "Questo telefono ha una versione più vecchia: l'app funziona lo stesso, ma senza i dati di Samsung Health. "
                + "La stima usa peso, altezza ed età del tuo profilo.";
            showConnect = false;
            if (wantLink()) {
                showConnect = true;
                connectLabel = "Aggiorna da Passi";
            }
        } else if (loadingHealth) {
            status = "Sto leggendo i dati…";
        } else if (health == null) {
            status = "Non ancora collegato.";
        } else if (!health.available) {
            status = "Health Connect non risulta disponibile su questo telefono.";
            showConnect = false;
        } else if (!health.permitted) {
            status = "Non hai ancora dato il permesso di lettura.";
            showPerm = true;
        } else if (!health.hasAnything()) {
            status = "Collegato, ma non trovo ancora dati. Controlla che in Samsung Health la sincronizzazione con Health Connect sia attiva, poi tocca Aggiorna.";
            connectLabel = "Aggiorna dati";
            showPerm = true;
        } else {
            status = "Collegato. Dati letti alle " + new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(healthTimeMs)) + ".";
            connectLabel = "Aggiorna dati";
        }
        if (wantLink()) status += "\n\n" + linkLine();
        connText.setText(status);
        connBtn.setText(connectLabel);
        connBtn.setVisibility(showConnect ? View.VISIBLE : View.GONE);
        permBtn.setVisibility(showPerm ? View.VISIBLE : View.GONE);

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.ITALY);
        double kg = health != null && health.weightKg != null ? health.weightKg : store.weightKg();
        double cm = health != null && health.heightCm != null ? health.heightCm : store.heightCm();
        double bmi = Calc.bmi(cm, kg);
        metricVals[0].setText(Fmt.num(kg, 1) + " kg");
        metricVals[1].setText(Fmt.num(cm, 0) + " cm");
        metricVals[2].setText(Fmt.num(bmi, 1) + " · " + Calc.bmiLabel(bmi));
        metricVals[2].setTextSize(14);
        if (health != null && health.lastBpm != null) {
            metricVals[3].setText(health.lastBpm + " bpm");
        } else {
            metricVals[3].setText("—");
        }
        metricVals[4].setText(health != null && health.stepsToday != null ? nf.format(health.stepsToday) : "—");
        metricVals[5].setText(health != null && health.sleepMsLast24h != null && health.sleepMsLast24h > 0
            ? Fmt.hm(health.sleepMsLast24h / 3600000.0) : "—");
        boolean fasting = store.fasting();
        metricVals[6].setText(fasting && health != null && health.activeKcalSinceStart != null
            ? nf.format(Math.round(health.activeKcalSinceStart)) + " kcal" : "—");
        metricVals[7].setText(fasting && health != null && health.totalKcalSinceStart != null
            ? nf.format(Math.round(health.totalKcalSinceStart)) + " kcal" : "—");
    }

    private void renderKeto() {
        if (tab != 1 || ketoText == null) return;
        boolean fasting = store.fasting();
        long now = System.currentTimeMillis();
        double h = fasting ? store.elapsedHours(now) : 0;
        double bmrH = Calc.bmrPerDay(store.male(), store.weightKg(), store.heightCm(), store.age()) / 24.0;
        double rest = bmrH;
        double active = 0;
        String src = "formula con peso, altezza ed età";
        if (fasting && health != null && h >= 1) {
            boolean used = false;
            if (health.activeKcalSinceStart != null) {
                double a = health.activeKcalSinceStart / h;
                if (a >= 0 && a <= 600) {
                    active = a;
                    used = true;
                }
            }
            if (health.activeKcalSinceStart != null && health.totalKcalSinceStart != null
                    && health.totalKcalSinceStart >= health.activeKcalSinceStart) {
                double r = (health.totalKcalSinceStart - health.activeKcalSinceStart) / h;
                double sane = Ketosis.sanitizeRest(r, bmrH);
                if (sane == r) {
                    rest = sane;
                    used = true;
                }
            }
            if (used) src = "calorie lette da " + (health.kcalSource != null ? health.kcalSource : "Samsung Health");
        }
        double rate = Ketosis.effectiveRate(rest, active);
        double dep = Ketosis.depletionHours(rate);
        double marked = Ketosis.markedKetosisHours(dep);

        if (fasting) {
            double pct = Ketosis.usedPercent(h, rate);
            ketoHead.setText("Serbatoio di zuccheri del fegato: usato al " + Math.round(pct) + "%");
            ketoBar.setFraction(pct / 100.0);
            long depAt = store.startMs() + (long) (dep * 3600000L);
            long markedAt = store.startMs() + (long) (marked * 3600000L);
            StringBuilder sb = new StringBuilder();
            if (h >= dep) {
                sb.append("Zuccheri del fegato: stimati già finiti (verso le ").append(Fmt.hm(dep)).append(" di digiuno).\n\n");
            } else {
                sb.append("Zuccheri del fegato finiti: ").append(whenText(depAt)).append(" (tra circa ").append(Fmt.hm(dep - h)).append(").\n\n");
            }
            if (h >= marked) {
                sb.append("Chetosi più marcata: stimata già raggiunta.");
            } else {
                sb.append("Chetosi più marcata: ").append(whenText(markedAt)).append(" (tra circa ").append(Fmt.hm(marked - h)).append(").");
            }
            sb.append("\n\nCalcolo basato su: ").append(src).append(" (circa ").append(Math.round(rate)).append(" kcal all'ora).");
            ketoText.setText(sb.toString());
        } else {
            ketoHead.setText("Se inizi ora");
            ketoBar.setFraction(0);
            ketoText.setText("Zuccheri del fegato finiti dopo circa " + Fmt.hm(dep) + ".\n\nChetosi più marcata dopo circa " + Fmt.hm(marked)
                + ".\n\nCalcolo basato su: " + src + ". Durante il digiuno la stima si affina con le calorie che consumi davvero.");
        }
        ketoNote.setText("È una stima: può sbagliare di diverse ore da persona a persona. Solo un misuratore di chetoni lo conferma. "
            + "Per l'autofagia non esiste un calcolo affidabile: guarda l'indicazione orientativa nella scheda Digiuno.");
    }

    // ------------------------------------------------------------------ schermata Acqua

    private View buildWaterScreen() {
        LinearLayout col = column();
        col.addView(header("Acqua", "Bere con regolarità aiuta durante il digiuno"));

        LinearLayout c = Ui.card(this);
        waterBig = Ui.title(this, "", 40);
        waterSub = Ui.text(this, "", 14, Ui.MUTED);
        waterBar = new BarView(this, Ui.TEAL);
        c.addView(waterBig);
        c.addView(waterSub, Ui.full(2));
        c.addView(waterBar, Ui.full(14));
        col.addView(c, Ui.full(16));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] amounts = {150, 250, 500};
        for (int i = 0; i < amounts.length; i++) {
            final int ml = amounts[i];
            Button b = Ui.button(this, "+" + ml + " ml", true);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    store.addWater(ml);
                    renderWater();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            p.leftMargin = Ui.dp(i == 0 ? 0 : 8);
            row.addView(b, p);
        }
        col.addView(row, Ui.full(14));

        Button undo = Ui.button(this, "Togli 250 ml", false);
        undo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                store.addWater(-250);
                renderWater();
            }
        });
        col.addView(undo, Ui.full(8));

        LinearLayout rem = Ui.card(this);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView lt = Ui.text(this, "Promemoria ogni 2 ore durante il digiuno, dalle 8 alle 22", 14, Ui.TEXT);
        line.addView(lt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(store.waterReminders());
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                store.setWaterReminders(on);
                Notifier.scheduleAll(MainActivity.this, store);
            }
        });
        line.addView(sw);
        rem.addView(line);
        col.addView(rem, Ui.full(14));

        col.addView(Ui.text(this,
            "L'obiettivo è circa 33 ml per kg di peso (tra 1,5 e 3,5 litri). Se hai problemi di cuore o reni, chiedi al medico quanto bere.",
            12, Ui.MUTED), Ui.full(14));
        return scroll(col);
    }

    private void renderWater() {
        if (waterBig == null || tab != 2) return;
        int goal = Calc.waterGoalMl(store.weightKg());
        int ml = store.waterMl();
        waterBig.setText(ml + " ml");
        waterSub.setText("obiettivo di oggi: " + goal + " ml");
        waterBar.setFraction(goal > 0 ? (double) ml / goal : 0);
    }

    // ------------------------------------------------------------------ schermata Storico

    private View buildHistoryScreen() {
        LinearLayout col = column();
        col.addView(header("Storico", "I tuoi digiuni"));
        List<HistoryEntry> all = store.history();
        if (all.isEmpty()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, "Non ci sono ancora digiuni registrati. Quando ne termini uno, compare qui.", 15, Ui.TEXT));
            col.addView(c, Ui.full(16));
            return scroll(col);
        }
        double total = 0, best = 0;
        int reached = 0;
        for (HistoryEntry e : all) {
            total += e.hours();
            best = Math.max(best, e.hours());
            if (e.reached()) reached++;
        }
        LinearLayout stats = Ui.card(this);
        stats.addView(Ui.text(this, all.size() + " digiuni · " + reached + " con obiettivo raggiunto", 15, Ui.TEXT));
        stats.addView(Ui.text(this, "Totale " + Fmt.hm(total) + " · media " + Fmt.hm(total / all.size()) + " · più lungo " + Fmt.hm(best), 13, Ui.MUTED), Ui.full(6));
        col.addView(stats, Ui.full(16));

        LinearLayout chartCard = Ui.card(this);
        chartCard.addView(Ui.text(this, "Ultimi digiuni (il trattino dorato è l'obiettivo)", 12, Ui.MUTED));
        ChartView chart = new ChartView(this);
        int from = Math.max(0, all.size() - 10);
        chart.setItems(all.subList(from, all.size()));
        chartCard.addView(chart, Ui.full(10));
        col.addView(chartCard, Ui.full(12));

        SimpleDateFormat f = new SimpleDateFormat("d MMM · HH:mm", Locale.ITALY);
        int shown = 0;
        for (int i = all.size() - 1; i >= 0 && shown < 30; i--, shown++) {
            HistoryEntry e = all.get(i);
            LinearLayout row = Ui.card(this);
            row.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
            row.addView(Ui.text(this, Fmt.hm(e.hours()) + (e.reached() ? "  ✓" : ""), 17, e.reached() ? Ui.TEAL : Ui.TEXT));
            row.addView(Ui.text(this, "Iniziato " + f.format(new Date(e.start)) + " · obiettivo " + Fmt.hm(e.goalHours), 12, Ui.MUTED), Ui.full(2));
            col.addView(row, Ui.full(8));
        }

        Button clear = Ui.button(this, "Cancella lo storico", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog().setTitle("Cancellare lo storico?")
                    .setMessage("I digiuni registrati saranno eliminati. Non si può annullare.")
                    .setPositiveButton("Cancella", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int w) {
                            store.clearHistory();
                            showTab(3);
                        }
                    })
                    .setNegativeButton("Annulla", null).show();
            }
        });
        col.addView(clear, Ui.full(16));
        return scroll(col);
    }

    // ------------------------------------------------------------------ schermata Profilo

    private static Double parse(String s) {
        if (s == null) return null;
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return null;
        try {
            double v = Double.parseDouble(t);
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void styleSex() {
        if (sexMaleBtn == null) return;
        TextView[] b = {sexMaleBtn, sexFemaleBtn};
        for (int i = 0; i < 2; i++) {
            boolean sel = (i == 0) == sexMale;
            b[i].setTextColor(sel ? 0xFF1A1405 : Ui.GOLD_LIGHT);
            b[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            b[i].setBackground(sel ? Ui.rect(Ui.GOLD, 14, 0) : Ui.rect(Ui.SURFACE2, 14, 0x33D4AF6A));
        }
    }

    private TextView sexChip(String label, final boolean male) {
        TextView t = Ui.text(this, label, 16, Ui.GOLD_LIGHT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(12), 0, Ui.dp(12));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sexMale = male;
                styleSex();
            }
        });
        return t;
    }

    private View buildProfileScreen(final boolean onboarding) {
        LinearLayout col = column();
        col.addView(header(onboarding ? "Benvenuto" : "Profilo",
            onboarding ? "Quattro dati per personalizzare l'app" : "I tuoi dati di base"));

        LinearLayout warn = Ui.card(this);
        warn.setBackground(Ui.rect(0x22D4AF6A, 16, 0x55D4AF6A));
        warn.addView(Ui.title(this, "Prima di iniziare", 18));
        warn.addView(Ui.text(this,
            "Digiuno è un'app di benessere, non un dispositivo medico. Il digiuno non è adatto a minorenni, a chi è in gravidanza "
            + "o allatta, a chi ha o ha avuto disturbi alimentari, a chi ha il diabete di tipo 1 o usa insulina. "
            + "Se hai diabete, pressione alta, problemi di cuore o reni, o prendi farmaci, parlane prima con il medico. "
            + "Interrompi se hai capogiri, nausea forte, battito irregolare o malessere. Le fasi e le stime sono orientative, non diagnosi.",
            13, Ui.TEXT), Ui.full(8));
        col.addView(warn, Ui.full(16));

        LinearLayout form = Ui.card(this);
        inHeight = Ui.input(this, "Altezza in cm", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inWeight = Ui.input(this, "Peso in kg", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inAge = Ui.input(this, "Età in anni", InputType.TYPE_CLASS_NUMBER);
        if (store.hasProfile()) {
            inHeight.setText(Fmt.num(store.heightCm(), 0));
            inWeight.setText(Fmt.num(store.weightKg(), 1));
            inAge.setText(String.valueOf(store.age()));
            sexMale = store.male();
        }
        form.addView(Ui.text(this, "Altezza (cm)", 13, Ui.MUTED));
        form.addView(inHeight, Ui.full(6));
        form.addView(Ui.text(this, "Peso (kg)", 13, Ui.MUTED), Ui.full(14));
        form.addView(inWeight, Ui.full(6));
        form.addView(Ui.text(this, "Età (anni)", 13, Ui.MUTED), Ui.full(14));
        form.addView(inAge, Ui.full(6));
        form.addView(Ui.text(this, "Sesso (serve solo per stimare il metabolismo di base)", 13, Ui.MUTED), Ui.full(14));
        LinearLayout sex = new LinearLayout(this);
        sex.setOrientation(LinearLayout.HORIZONTAL);
        sexMaleBtn = sexChip("Uomo", true);
        sexFemaleBtn = sexChip("Donna", false);
        sex.addView(sexMaleBtn, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        fp.leftMargin = Ui.dp(8);
        sex.addView(sexFemaleBtn, fp);
        form.addView(sex, Ui.full(6));
        styleSex();
        col.addView(form, Ui.full(12));

        if (HealthSync.supported()) {
            Button imp = Ui.button(this, "Prendi peso e altezza da Samsung Health", false);
            imp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (HealthSync.anyGranted(MainActivity.this)) loadHealth(true);
                    else HealthSync.request(MainActivity.this, REQ_HC);
                }
            });
            col.addView(imp, Ui.full(12));
        }

        acceptBox = null;
        if (onboarding || !store.accepted()) {
            acceptBox = new CheckBox(this);
            acceptBox.setText("Ho letto l'avviso e ho più di 18 anni");
            acceptBox.setTextColor(Ui.TEXT);
            acceptBox.setTextSize(14);
            col.addView(acceptBox, Ui.full(12));
        }

        Button save = Ui.button(this, onboarding ? "Inizia" : "Salva", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveProfile(onboarding); }
        });
        col.addView(save, Ui.full(14));

        col.addView(Ui.text(this,
            "I dati restano sul tuo telefono. L'app non li invia a nessuno.", 12, Ui.MUTED), Ui.full(12));
        return scroll(col);
    }

    private void saveProfile(boolean onboarding) {
        Double h = parse(inHeight.getText().toString());
        Double w = parse(inWeight.getText().toString());
        Double a = parse(inAge.getText().toString());
        if (h == null || !Calc.validHeight(h)) { toast("Altezza non valida: inserisci i centimetri, tra 100 e 250."); return; }
        if (w == null || !Calc.validWeight(w)) { toast("Peso non valido: inserisci i chili, tra 30 e 300."); return; }
        if (a == null || a < 1 || a > 130) { toast("Età non valida."); return; }
        int age = (int) Math.round(a);
        if (age < 18) {
            dialog().setTitle("App non adatta ai minorenni")
                .setMessage("Il digiuno non è adatto a chi ha meno di 18 anni. Per questo non posso procedere.")
                .setPositiveButton("Ok", null).show();
            return;
        }
        if (!Calc.validAge(age)) { toast("Età non valida."); return; }
        if (acceptBox != null && !acceptBox.isChecked()) { toast("Per continuare conferma di aver letto l'avviso."); return; }
        store.saveProfile(h, w, age, sexMale);
        if (acceptBox != null) store.setAccepted();
        if (onboarding) {
            showTab(0);
        } else {
            toast("Salvato");
            showTab(4);
        }
    }

    private void fillProfileFromHealth() {
        if (inHeight == null || !inHeight.isAttachedToWindow() || health == null) return;
        boolean any = false;
        if (health.heightCm != null) { inHeight.setText(Fmt.num(health.heightCm, 0)); any = true; }
        if (health.weightKg != null) { inWeight.setText(Fmt.num(health.weightKg, 1)); any = true; }
        toast(any ? "Dati presi da Samsung Health: controllali e conferma." : "Non ho trovato peso o altezza in Samsung Health.");
    }
}
