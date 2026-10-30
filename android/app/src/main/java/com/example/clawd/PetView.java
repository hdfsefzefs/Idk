package com.example.clawd;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Draws the pixel Clawd and owns its purely visual animation state. */
public class PetView extends View {

    public interface Listener {
        void onDown(float rawX, float rawY);
        void onMove(float rawX, float rawY);
        void onUp(boolean click);
    }

    static final int COLS = 15;
    static final int ROWS = 12; // 4 rows of headroom for jumps, 6 body, 1 legs, 1 spare
    static final int BODY_ROWS = 6;
    private static final int BODY_COLOR = Color.parseColor("#D97757");
    private static final int EYE_COLOR = Color.parseColor("#1A1A1A");
    private static final int Z_COLOR = Color.parseColor("#8A8A8A");
    private static final int STAR_COLOR = Color.parseColor("#E6B800");

    // body rows with arms on row 3; eyes and mouth are drawn separately
    private static final String[] BODY = {
        "..BBBBBBBBBBB..",
        "..BBBBBBBBBBB..",
        "..BBBBBBBBBBB..",
        "BBBBBBBBBBBBBBB",
        "..BBBBBBBBBBB..",
        "..BBBBBBBBBBB..",
    };
    private static final String LEGS_A = "...B.B...B.B...";
    private static final String LEGS_B = "..B.B.....B.B..";
    private static final int[] EYE_COLS = {4, 10};

    private final Random rnd = new Random();
    private final Paint paint = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    final int ps; // pixel size in px

    private String state = "idle";
    private int dir = 1;
    private float speed = 1.5f;
    private int frame;
    private int jump;
    private long squashUntil;
    private float squash;
    private long blinkUntil;
    private long nextBlink;
    private int lookX;
    private long lookUntil;
    private String action = "";
    private long actionUntil;
    private long lastFrameAt;

    private Listener listener;
    private float downX, downY;
    private final float moveSlop;

    public PetView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        ps = Math.max(2, Math.round(5 * d));
        moveSlop = 8 * d;
        text.setFakeBoldText(true);
        text.setTextSize(ps * 2.2f);
    }

    public void setListener(Listener l) { listener = l; }

    public int viewWidth() { return COLS * ps; }

    public int viewHeight() { return ROWS * ps; }

    public void setPetState(String s, int d, float sp) {
        state = s;
        dir = d;
        speed = sp;
    }

    public void doJump() { jump = 20; }

    public void hop() { jump = 16; }

    public void land(float strength) {
        squash = Math.min(1f, strength);
        squashUntil = SystemClock.uptimeMillis() + 250;
    }

    public void look(int x, long ms) {
        lookX = x;
        lookUntil = SystemClock.uptimeMillis() + ms;
    }

    public void startAction(String type, long ms) {
        action = type;
        actionUntil = SystemClock.uptimeMillis() + ms;
    }

    private final Runnable anim = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            if (jump > 0) jump--;
            if (now > nextBlink) {
                blinkUntil = now + 140;
                nextBlink = now + 2500 + rnd.nextInt(3000);
            }
            long step = (speed > 2.5f && state.equals("walk")) ? 90 : 180;
            if (now - lastFrameAt >= step) {
                frame++;
                lastFrameAt = now;
            }
            invalidate();
            handler.postDelayed(this, 33);
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(anim);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(anim);
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(viewWidth(), viewHeight());
    }

    private void px(Canvas c, int col, int row, int color) {
        paint.setColor(color);
        c.drawRect(col * ps, row * ps, (col + 1) * ps, (row + 1) * ps, paint);
    }

    private void drawRow(Canvas c, String line, int row) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == 'B') px(c, i, row, BODY_COLOR);
        }
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        int w = viewWidth(), h = viewHeight();
        c.save();
        if (dir < 0) c.scale(-1, 1, w / 2f, h / 2f);

        if (now < squashUntil) {
            float k = Math.min(1f, (squashUntil - now) / 250f);
            float sy = 1 - 0.25f * squash * k;
            c.translate(w / 2f, h);
            c.scale(1 + (1 - sy) * 0.8f, sy);
            c.translate(-w / 2f, -h);
        }

        float t = now / 1000f;
        String act = now < actionUntil ? action : "";
        boolean sleeping = state.equals("sleep");
        boolean airborne = state.equals("drag") || state.equals("fall");
        int bob = 0;
        if (state.equals("idle")) bob = Math.round((float) Math.sin(t * 3));
        else if (state.equals("walk")) bob = frame % 2 == 0 ? 0 : -1;
        else if (sleeping) bob = Math.round((float) Math.sin(t * 1.2) * 0.6f);
        else if (airborne) bob = -1;
        int lift = jump > 0 ? Math.round((float) Math.sin(jump / 20f * Math.PI) * 4) : 0;
        int y0 = 4 + bob - lift;

        for (int r = 0; r < BODY.length; r++) drawRow(c, BODY[r], y0 + r);

        boolean blinking = now < blinkUntil;
        if (now > lookUntil) lookX = 0;
        for (int i = 0; i < EYE_COLS.length; i++) {
            int col = EYE_COLS[i] + lookX;
            if (act.equals("dizzy")) col = EYE_COLS[i] + (frame % 2 == 0 ? 1 : -1) * (i == 1 ? -1 : 1);
            if (sleeping || blinking || act.equals("yawn")) {
                px(c, col, y0 + 2, EYE_COLOR);
            } else if (airborne) {
                px(c, col, y0 + 1, EYE_COLOR);
                px(c, col, y0 + 2, EYE_COLOR);
                px(c, col + (i == 1 ? 1 : -1), y0 + 1, EYE_COLOR);
            } else {
                px(c, col, y0 + 1, EYE_COLOR);
                px(c, col, y0 + 2, EYE_COLOR);
            }
        }

        if (act.equals("yawn")) {
            for (int col = 6; col <= 8; col++) px(c, col, y0 + 4, EYE_COLOR);
        } else if (airborne || act.equals("dizzy")) {
            px(c, 7, y0 + 4, EYE_COLOR);
        }

        boolean flap = frame % 2 == 0;
        boolean waving = act.equals("wave");
        if (state.equals("walk") || airborne || waving || act.equals("dizzy")) {
            px(c, 0, y0 + (flap ? 2 : 3), BODY_COLOR);
            px(c, 14, y0 + (flap ? 3 : 2), BODY_COLOR);
            if (waving || airborne) {
                px(c, 0, y0 + (flap ? 1 : 2), BODY_COLOR);
                px(c, 14, y0 + (flap ? 2 : 1), BODY_COLOR);
            }
        }

        boolean stepB = (state.equals("walk") || airborne) && frame % 2 == 1;
        drawRow(c, stepB ? LEGS_B : LEGS_A, y0 + BODY_ROWS);
        c.restore();

        if (sleeping) {
            text.setColor(Z_COLOR);
            int zf = (int) Math.floor(t * 1.5) % 3;
            for (int i = 0; i <= zf; i++) c.drawText("z", ps * 11 + i * ps * 1.2f, ps * 3 - i * ps, text);
        }
        if (act.equals("dizzy")) {
            text.setColor(STAR_COLOR);
            float a = t * 6;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                c.drawText("★", ps * 6.5f + (float) Math.cos(ang) * ps * 4, ps * 3 + (float) Math.sin(ang) * ps * 0.8f, text);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                listener.onDown(downX, downY);
                return true;
            case MotionEvent.ACTION_MOVE:
                listener.onMove(e.getRawX(), e.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean click = e.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.hypot(e.getRawX() - downX, e.getRawY() - downY) <= moveSlop;
                listener.onUp(click);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }
}
