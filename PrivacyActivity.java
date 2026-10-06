package it.digiuno.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Informativa sulla privacy, mostrata da Health Connect quando si toccano i permessi dell'app. */
public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(22), Ui.dp(28), Ui.dp(22), Ui.dp(28));
        col.setBackgroundColor(Ui.BG);
        col.addView(Ui.title(this, "Privacy", 28));
        TextView t = Ui.text(this,
            "Digiuno legge da Health Connect (Samsung Health) solo peso, altezza, battito, passi, sonno e calorie consumate. "
            + "Li usa soltanto per mostrarteli e per calcolare le stime dentro l'app.\n\n"
            + "I dati restano sul tuo telefono: l'app non li invia a nessuno, non li condivide e non li scrive su Health Connect. "
            + "Puoi togliere i permessi in qualsiasi momento dalle impostazioni di Health Connect.\n\n"
            + "Digiuno è un'app di benessere, non un dispositivo medico: le fasi e le stime sono orientative.",
            16, Ui.TEXT);
        col.addView(t, Ui.full(16));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(col);
        setContentView(sv);
    }
}
