package it.digiuno.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Anello animato del digiuno: avanza con le ore e pulsa mentre il digiuno è in corso. */
final class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tBig = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private double elapsedH;
    private double goalH = 16;
    private boolean active;
    private String top = "";
    private String big = "00:00:00";
    private String sub = "";
    private float shown;      // progresso mostrato (0..1), si avvicina piano al vero
    private float pulse;      // 0..1 in loop
    private ValueAnimator pulser;

    RingView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(14 * d);
        track.setColor(Ui.SURFACE2);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(14 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.FILL);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Ui.GOLD_LIGHT);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(2 * d);
        tick.setColor(0x55FFFFFF);
        tTop.setColor(Ui.GOLD);
        tTop.setTextAlign(Paint.Align.CENTER);
        tTop.setTextSize(15 * d);
        tTop.setTypeface(Typeface.SERIF);
        tBig.setColor(Ui.TEXT);
        tBig.setTextAlign(Paint.Align.CENTER);
        tBig.setTextSize(44 * d);
        tBig.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        tSub.setColor(Ui.MUTED);
        tSub.setTextAlign(Paint.Align.CENTER);
        tSub.setTextSize(14 * d);
    }

    void setState(double elapsedHours, double goalHours, boolean isActive, String topText, String bigText, String subText) {
        elapsedH = elapsedHours;
        goalH = goalHours > 0 ? goalHours : 16;
        top = topText;
        big = bigText;
        sub = subText;
        if (isActive != active) {
            active = isActive;
            updatePulser();
        }
        invalidate();
    }

    private void updatePulser() {
        if (active && isAttachedToWindow()) {
            if (pulser == null) {
                pulser = ValueAnimator.ofFloat(0f, 1f);
                pulser.setDuration(2200);
                pulser.setRepeatCount(ValueAnimator.INFINITE);
                pulser.setRepeatMode(ValueAnimator.REVERSE);
                pulser.setInterpolator(new LinearInterpolator());
                pulser.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        pulse = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
            }
            if (!pulser.isStarted()) pulser.start();
        } else if (pulser != null) {
            pulser.cancel();
            pulse = 0;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePulser();
    }

    @Override protected void onDetachedFromWindow() {
        if (pulser != null) pulser.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int max = Ui.dp(320);
        int size = Math.min(w, max);
        setMeasuredDimension(size, size);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        arc.setShader(new SweepGradient(w / 2f, h / 2f,
            new int[] {Ui.TEAL, Ui.GOLD, Ui.GOLD_LIGHT}, new float[] {0f, 0.65f, 1f}));
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float stroke = track.getStrokeWidth();
        float r = Math.min(w, h) / 2f - stroke;
        box.set(cx - r, cy - r, cx + r, cy + r);

        canvas.drawCircle(cx, cy, r, track);

        float target = (float) Calc.clamp(elapsedH / goalH, 0, 1);
        shown += (target - shown) * 0.12f;
        boolean settling = Math.abs(target - shown) > 0.001f;
        if (!settling) shown = target;

        // segni delle fasi dentro l'obiettivo
        for (Phase ph : Phases.ALL) {
            if (ph.startHour <= 0 || ph.startHour >= goalH) continue;
            double ang = Math.toRadians(360.0 * ph.startHour / goalH - 90.0);
            float x1 = (float) (cx + (r + stroke * 0.9f) * Math.cos(ang));
            float y1 = (float) (cy + (r + stroke * 0.9f) * Math.sin(ang));
            float x2 = (float) (cx + (r + stroke * 1.3f) * Math.cos(ang));
            float y2 = (float) (cy + (r + stroke * 1.3f) * Math.sin(ang));
            canvas.drawLine(x1, y1, x2, y2, tick);
        }

        if (shown > 0.002f) {
            canvas.save();
            canvas.rotate(-90, cx, cy);
            canvas.drawArc(box, 0, 360f * shown, false, arc);
            canvas.restore();

            double ang = Math.toRadians(360.0 * shown - 90.0);
            float ex = (float) (cx + r * Math.cos(ang));
            float ey = (float) (cy + r * Math.sin(ang));
            if (active) {
                glow.setColor(Ui.GOLD);
                glow.setAlpha((int) (40 + 70 * pulse));
                canvas.drawCircle(ex, ey, stroke * (1.2f + 0.8f * pulse), glow);
            }
            canvas.drawCircle(ex, ey, stroke * 0.32f, dot);
        }

        float base = cy;
        canvas.drawText(top, cx, base - tBig.getTextSize() * 0.62f, tTop);
        canvas.drawText(big, cx, base + tBig.getTextSize() * 0.32f, tBig);
        canvas.drawText(sub, cx, base + tBig.getTextSize() * 0.82f, tSub);

        if (settling) postInvalidateOnAnimation();
    }
}

/** Barra di avanzamento semplice con angoli arrotondati. */
final class BarView extends View {
    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float fraction;

    BarView(Context c, int color) {
        super(c);
        back.setColor(Ui.SURFACE2);
        front.setColor(color);
    }

    void setFraction(double f) {
        fraction = (float) Calc.clamp(f, 0, 1);
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Ui.dp(10));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        c.drawRoundRect(r, h / 2, h / 2, back);
        if (fraction > 0.001f) {
            r.set(0, 0, Math.max(h, w * fraction), h);
            c.drawRoundRect(r, h / 2, h / 2, front);
        }
    }
}

/** Grafico a barre degli ultimi digiuni, con il segno dell'obiettivo. */
final class ChartView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private List<HistoryEntry> items;

    ChartView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        label.setColor(Ui.MUTED);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(10 * d);
        value.setColor(Ui.TEXT);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTextSize(11 * d);
        goalMark.setColor(Ui.GOLD);
        goalMark.setStrokeWidth(2.5f * d);
        goalMark.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1 * d);
    }

    void setItems(List<HistoryEntry> list) {
        items = list;
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Ui.dp(190));
    }

    @Override protected void onDraw(Canvas c) {
        if (items == null || items.isEmpty()) return;
        float w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float top = 20 * d, bottom = h - 22 * d;
        double max = 12;
        for (HistoryEntry e : items) max = Math.max(max, Math.max(e.hours(), e.goalHours));
        max = Math.ceil(max / 6.0) * 6.0;
        c.drawLine(0, bottom, w, bottom, grid);
        int n = items.size();
        float slot = w / n;
        float bw = Math.min(slot * 0.56f, 34 * d);
        SimpleDateFormat f = new SimpleDateFormat("d/M", Locale.ITALY);
        for (int i = 0; i < n; i++) {
            HistoryEntry e = items.get(i);
            float cx = slot * i + slot / 2f;
            float bh = (float) ((bottom - top) * (e.hours() / max));
            bar.setColor(e.reached() ? Ui.TEAL : 0xFF6F7F9E);
            r.set(cx - bw / 2, bottom - Math.max(bh, 3 * d), cx + bw / 2, bottom);
            c.drawRoundRect(r, 6 * d, 6 * d, bar);
            float gy = (float) (bottom - (bottom - top) * (e.goalHours / max));
            c.drawLine(cx - bw / 2 - 3 * d, gy, cx + bw / 2 + 3 * d, gy, goalMark);
            c.drawText(Fmt.num(e.hours(), e.hours() >= 10 ? 0 : 1) + "h", cx, bottom - Math.max(bh, 3 * d) - 5 * d, value);
            c.drawText(f.format(new Date(e.end)), cx, h - 5 * d, label);
        }
    }
}
