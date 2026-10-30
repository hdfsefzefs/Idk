package com.example.clawd;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Random;

/** Floats Clawd above other apps and runs the same behaviour as the desktop pet. */
public class PetService extends Service implements PetView.Listener {

    private static final String CHANNEL = "clawd_pet";
    private static final String ACTION_STOP = "com.example.clawd.STOP";
    public static final String EXTRA_CHARACTER = "character";
    private static final int TICK_MS = 33;

    private static final String[] POKES = {"Hellooo :3", "摸摸我~", "要寫 code 嗎?", "*揮舞鉗子*", "嘿嘿 :3",
            "肚子餓了…", "今天也要加油喔", "我是 Clawd!", "有 bug 嗎?我來看看"};
    private static final String[] TUNG_POKES = {"Tung tung tung sahur!", "咚咚咚咚~起床啦!", "該吃 sahur 了!", "*揮球棒*", "咚!",
            "Sahur sahur!", "別睡啦,咚咚咚!"};
    private static final String[] ANNOYED = {"好啦好啦,別戳了…", "再戳我要夾你喔!", "欸欸欸~"};
    private static final String[][] CHAT = {
            {"早安~", "今天要做什麼呢?"},
            {"吃飯了嗎?", "午休一下吧"},
            {"辛苦了~", "今天寫得順嗎?"},
            {"這麼晚了…該睡了喔", "zzz…還不睡嗎?"},
    };

    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private PetView pet;
    private WindowManager.LayoutParams petLp;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private boolean bubbleShown;
    private String character = "clawd"; // clawd | tung

    private float u; // density: all desktop px speeds are scaled by this
    private int screenW, screenH, navH, viewW, viewH;
    // the system may place overlay windows offset from our requested x/y (status bar, cutout); measured once
    private int offX, offY;

    private String state = "idle"; // idle | walk | sleep | drag | fall
    private int dir = 1;
    private float speed = 1.5f;
    private float x, y, vx, vy;
    private long stateUntil;
    private float touchX, touchY, dragOffX, dragOffY, lastX, lastY;
    private boolean hasLast;
    private final long[] clicks = new long[6];
    private int clickCount;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        String wanted = intent != null && intent.hasExtra(EXTRA_CHARACTER)
                ? intent.getStringExtra(EXTRA_CHARACTER)
                : getSharedPreferences("pet", MODE_PRIVATE).getString("character", "clawd");
        if (pet != null && !wanted.equals(character)) destroyWindows();
        character = wanted;
        if (pet == null) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            createWindows();
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Clawd 桌寵", NotificationManager.IMPORTANCE_MIN));
        Intent stop = new Intent(this, PetService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_clawd)
                .setContentTitle("Clawd 在你的畫面上")
                .setContentText("點這裡讓它回家")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void createWindows() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        u = getResources().getDisplayMetrics().density;
        pet = new PetView(this, "tung".equals(character));
        pet.setListener(this);
        viewW = pet.viewWidth();
        viewH = pet.viewHeight();
        refreshBounds();

        x = screenW / 3f;
        y = groundY();
        petLp = new WindowManager.LayoutParams(viewW, viewH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        petLp.gravity = Gravity.TOP | Gravity.START;
        petLp.x = Math.round(x);
        petLp.y = Math.round(y);
        wm.addView(pet, petLp);

        bubble = new TextView(this);
        bubble.setTextColor(Color.parseColor("#222222"));
        bubble.setTextSize(13);
        bubble.setGravity(Gravity.CENTER);
        int p = Math.round(8 * u);
        bubble.setPadding(p, p / 2, p, p / 2);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(10 * u);
        bg.setStroke(Math.max(1, Math.round(2 * u)), Color.parseColor("#222222"));
        bubble.setBackground(bg);
        bubbleLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;

        setState("idle", 1500);
        handler.postDelayed(tickRunnable, TICK_MS);
        handler.postDelayed(idleRunnable, 2500);
        handler.postDelayed(measureOffset, 600);
    }

    private final Runnable measureOffset = new Runnable() {
        @Override public void run() {
            if (pet == null || !pet.isAttachedToWindow()) return;
            int[] loc = new int[2];
            pet.getLocationOnScreen(loc);
            offX = loc[0] - petLp.x;
            offY = loc[1] - petLp.y;
        }
    };

    @SuppressWarnings("deprecation")
    private void refreshBounds() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        boolean resized = screenW != 0 && (screenW != dm.widthPixels || screenH != dm.heightPixels);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        boolean gesture = Settings.Secure.getInt(getContentResolver(), "navigation_mode", 0) == 2;
        boolean portrait = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int id = getResources().getIdentifier(gesture ? "navigation_bar_gesture_height" : "navigation_bar_height",
                "dimen", "android");
        navH = id > 0 && (gesture || portrait) ? getResources().getDimensionPixelSize(id) : 0;
        if (resized && pet != null) { // rotated: hold still and re-measure the placement offset
            offX = 0;
            offY = 0;
            setState("idle", 1500);
            handler.removeCallbacks(measureOffset);
            handler.postDelayed(measureOffset, 700);
        }
    }

    private float groundY() { return screenH - viewH - navH; }

    private void setState(String next, long ms) {
        state = next;
        stateUntil = SystemClock.uptimeMillis() + ms;
        pet.setPetState(state, dir, speed);
    }

    private void pickNext() {
        float r = rnd.nextFloat();
        if (r < 0.5f) {
            dir = rnd.nextBoolean() ? -1 : 1;
            speed = rnd.nextFloat() < 0.25f ? 4f : 1f + rnd.nextFloat() * 1.5f;
            setState("walk", 2000 + rnd.nextInt(4000));
        } else if (r < 0.8f) {
            setState("idle", 2000 + rnd.nextInt(3000));
        } else {
            setState("sleep", 6000 + rnd.nextInt(8000));
        }
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, TICK_MS);
        }
    };

    private void tick() {
        long now = SystemClock.uptimeMillis();
        refreshBounds();
        float gy = groundY();
        float maxX = screenW - viewW;

        if (state.equals("drag")) {
            float nx = touchX - dragOffX;
            float ny = touchY - dragOffY;
            if (hasLast) { // smoothed release velocity so a flick throws Clawd
                vx = vx * 0.5f + (nx - lastX) * 0.5f;
                vy = vy * 0.5f + (ny - lastY) * 0.5f;
            }
            lastX = nx;
            lastY = ny;
            hasLast = true;
            x = nx;
            y = ny;
        } else if (state.equals("fall")) {
            vy += 1.2f * u;
            x += vx;
            y += vy;
            if (x < 0) { x = 0; vx = -vx * 0.6f; }
            if (x > maxX) { x = maxX; vx = -vx * 0.6f; }
            if (y < 0) { y = 0; vy = Math.abs(vy) * 0.5f; }
            if (y >= gy) {
                y = gy;
                float impact = Math.abs(vy);
                pet.land(impact / (20 * u));
                if (impact > 18 * u) say("好痛…", 1500);
                if (impact > 7 * u) {
                    vy = -impact * 0.45f;
                    vx *= 0.7f;
                } else {
                    vx = 0;
                    vy = 0;
                    setState("idle", 1500);
                }
            }
        } else {
            if (y < gy - 1) { // dropped from rest: start falling
                vx = 0;
                vy = 0;
                setState("fall", 0);
            } else {
                y = gy;
                if (now > stateUntil) pickNext();
            }
            if (state.equals("walk")) {
                x += speed * u * dir;
                if (x < 0) { x = 0; dir = 1; pet.setPetState(state, dir, speed); }
                if (x > maxX) { x = maxX; dir = -1; pet.setPetState(state, dir, speed); }
                if (rnd.nextFloat() < 0.004f) pet.hop();
            }
        }

        petLp.x = Math.round(x) - offX;
        petLp.y = Math.round(y) - offY;
        wm.updateViewLayout(pet, petLp);
        if (bubbleShown) placeBubble();
    }

    private final Runnable idleRunnable = new Runnable() {
        @Override public void run() {
            if (state.equals("idle")) {
                float r = rnd.nextFloat();
                if (r < 0.35f) pet.look(rnd.nextBoolean() ? -1 : 1, 1200);
                else if (r < 0.5f) pet.startAction("yawn", 1400);
                else if (r < 0.65f) pet.startAction("wave", 1600);
                else if (r < 0.75f) say(timeOfDayLine(), 2800);
            }
            handler.postDelayed(this, 2500);
        }
    };

    private String timeOfDayLine() {
        if ("tung".equals(character) && rnd.nextBoolean()) return TUNG_POKES[rnd.nextInt(TUNG_POKES.length)];
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int slot = h >= 5 && h < 11 ? 0 : h >= 11 && h < 14 ? 1 : h >= 14 && h < 20 ? 2 : 3;
        return CHAT[slot][rnd.nextInt(CHAT[slot].length)];
    }

    private final Runnable hideBubble = new Runnable() {
        @Override public void run() {
            if (bubbleShown) {
                wm.removeView(bubble);
                bubbleShown = false;
            }
        }
    };

    private void say(String text, long ms) {
        bubble.setText(text);
        if (!bubbleShown) {
            bubbleShown = true;
            placeBubble();
            wm.addView(bubble, bubbleLp);
        }
        handler.removeCallbacks(hideBubble);
        handler.postDelayed(hideBubble, ms);
    }

    private void placeBubble() {
        bubble.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int bw = bubble.getMeasuredWidth();
        int bh = bubble.getMeasuredHeight();
        // sit just above Clawd's head (4 rows of headroom in the view)
        int bx = Math.max(0, Math.min(screenW - bw, Math.round(x + viewW / 2f - bw / 2f)));
        int by = Math.max(0, Math.round(y + pet.headY() - bh));
        bubbleLp.x = bx - offX;
        bubbleLp.y = by - offY;
        if (bubble.isAttachedToWindow()) wm.updateViewLayout(bubble, bubbleLp);
    }

    // ---- touch from PetView ----

    @Override public void onDown(float rawX, float rawY) {
        touchX = rawX;
        touchY = rawY;
        dragOffX = rawX - x;
        dragOffY = rawY - y;
        hasLast = false;
        vx = 0;
        vy = 0;
        setState("drag", 0);
        if (rnd.nextBoolean()) {
            String[] lines = {"哇!", "放我下來~", "飛起來了!"};
            say(lines[rnd.nextInt(lines.length)], 1500);
        }
    }

    @Override public void onMove(float rawX, float rawY) {
        touchX = rawX;
        touchY = rawY;
    }

    @Override public void onUp(boolean click) {
        if (!state.equals("drag")) return;
        float cap = 40 * u;
        vx = Math.max(-cap, Math.min(cap, vx));
        vy = Math.max(-cap, Math.min(cap, vy));
        if (y < groundY() - 2 || Math.hypot(vx, vy) > 3 * u) setState("fall", 0);
        else setState("idle", 1500);
        if (click) poke();
    }

    private void poke() {
        long now = SystemClock.uptimeMillis();
        int kept = 0;
        for (int i = 0; i < clickCount; i++) {
            if (now - clicks[i] < 2000) clicks[kept++] = clicks[i];
        }
        clickCount = kept;
        if (clickCount < clicks.length) clicks[clickCount++] = now;

        if (state.equals("sleep") || state.equals("fall")) setState("idle", 2000);
        pet.doJump();
        if (clickCount >= 6) {
            clickCount = 0;
            pet.startAction("dizzy", 2600);
            say("暈~@_@", 2600);
        } else if (clickCount >= 3) {
            say(ANNOYED[rnd.nextInt(ANNOYED.length)], 2200);
        } else {
            String[] lines = "tung".equals(character) ? TUNG_POKES : POKES;
            say(lines[rnd.nextInt(lines.length)], 2200);
        }
    }

    private void destroyWindows() {
        handler.removeCallbacksAndMessages(null);
        if (bubbleShown) wm.removeView(bubble);
        bubbleShown = false;
        if (pet != null) wm.removeView(pet);
        pet = null;
        offX = 0;
        offY = 0;
        state = "idle";
    }

    @Override public void onDestroy() {
        destroyWindows();
        super.onDestroy();
    }
}
