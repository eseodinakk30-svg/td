package com.claude.td;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Surface + render thread; owns the game and the HUD. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread thread;
    private volatile boolean running;

    public final Game game;
    private final Hud hud;
    private final Object lock = new Object();

    private int vw, vh;

    /** Surface.lockHardwareCanvas() exists from API 23; fall back if it ever fails. */
    private boolean hardware = Build.VERSION.SDK_INT >= 23;
    private static final long FRAME_NANOS = 16_666_666L;

    public GameView(Context ctx) {
        super(ctx);
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        Ui.DP = dm.density <= 0 ? 2f : dm.density;

        game = new Game(new Save(ctx));
        hud = new Hud(game);

        getHolder().addCallback(this);
        setFocusable(true);
    }

    // ------------------------------------------------------------- lifecycle

    public void surfaceCreated(SurfaceHolder holder) {
        start();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized (lock) {
            vw = width;
            vh = height;
            hud.layout(width, height);
            game.resize(width, height, hud.topInset(), hud.bottomInset());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        stop();
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "td-loop");
        thread.start();
    }

    public void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try {
                t.join(800);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Called when the activity is backgrounded. */
    public void pauseGame() {
        synchronized (lock) {
            if (game.state == Game.PLAYING) game.state = Game.PAUSED;
        }
    }

    /** @return true if the back press was consumed. */
    public boolean onBack() {
        synchronized (lock) {
            if (game.state == Game.PLAYING) {
                if (game.selected != null) { game.selected = null; return true; }
                if (game.buildType >= 0) { game.buildType = -1; return true; }
                game.state = Game.PAUSED;
                return true;
            }
            if (game.state == Game.PAUSED || game.state == Game.GAME_OVER
                    || game.state == Game.VICTORY) {
                game.toMenu();
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        synchronized (lock) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    hud.onDown(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    hud.onMove(x, y);
                    break;
                case MotionEvent.ACTION_UP:
                    hud.onUp(x, y);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    game.hoverC = -1;
                    game.hoverR = -1;
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- main loop

    public void run() {
        long prev = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - prev) / 1e9f;
            prev = now;
            if (dt > 0.1f) dt = 0.1f;

            SurfaceHolder h = getHolder();
            Surface surface = h.getSurface();
            if (surface == null || !surface.isValid()) {
                idle();
                continue;
            }

            boolean hw = hardware;
            Canvas c = null;
            try {
                c = hw ? surface.lockHardwareCanvas() : h.lockCanvas();
            } catch (Throwable t) {
                hardware = false;
                hw = false;
                c = null;
            }
            if (c == null && hw) {
                hardware = false;
                hw = false;
                try {
                    c = h.lockCanvas();
                } catch (Throwable ignored) {
                    c = null;
                }
            }
            if (c == null) {
                idle();
                continue;
            }

            try {
                synchronized (lock) {
                    game.update(dt);
                    render(c, dt);
                }
            } finally {
                try {
                    if (hw) surface.unlockCanvasAndPost(c);
                    else h.unlockCanvasAndPost(c);
                } catch (Throwable ignored) {
                    // the surface disappeared mid-frame
                }
            }

            long frame = System.nanoTime() - now;
            long sleep = FRAME_NANOS - frame;
            if (sleep > 0) {
                try {
                    Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }

    private void idle() {
        try {
            Thread.sleep(16);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private void render(Canvas c, float dt) {
        c.drawColor(Ui.BG);
        if (vw == 0) return;

        if (game.level != null && game.state != Game.MENU) {
            int save = c.save();
            if (game.shake > 0) {
                float k = game.shake * Ui.dp(6);
                c.translate((float) (Math.random() - 0.5) * k, (float) (Math.random() - 0.5) * k);
            }
            game.drawWorld(c);
            c.restoreToCount(save);
        }
        hud.draw(c, dt);
    }
}
