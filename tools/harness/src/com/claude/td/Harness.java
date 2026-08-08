package com.claude.td;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Harnesses;

import java.util.ArrayList;

/**
 * Headless smoke test: plays every map with a simple bot while rendering each
 * frame onto stub graphics. Catches crashes, non-finite geometry and obvious
 * balance problems without needing a device.
 */
public final class Harness {

    private static final float DT = 1f / 60f;
    private static final int W = 1080;
    private static final int H = 2160;

    public static void main(String[] args) {
        Ui.DP = 3f;
        Game g = new Game(new Save(new Context()));
        Hud hud = new Hud(g);
        hud.layout(W, H);
        g.resize(W, H, hud.topInset(), hud.bottomInset());
        Canvas c = new Canvas();

        // menu renders and responds before anything is started
        for (int i = 0; i < 30; i++) hud.draw(c, DT);
        hud.onDown(W * 0.5f, H * 0.5f);
        hud.onDown(-50, -50);

        boolean allOk = true;
        for (int map = 0; map < Level.MAP_COUNT; map++) {
            allOk &= play(g, hud, c, map);
        }

        // overlay states must all render and accept taps
        g.startLevel(0);
        exercise(g, hud, c);

        System.out.println("draw calls: " + Harnesses.drawCalls);
        System.out.println(allOk ? "HARNESS OK" : "HARNESS FAILED");
        if (!allOk) System.exit(1);
    }

    private static boolean play(Game g, Hud hud, Canvas c, int map) {
        g.startLevel(map);
        g.speed = 3;
        int frames = 0;
        int maxFrames = 60 * 60 * 12;   // 12 simulated minutes at 3x
        long t0 = System.currentTimeMillis();
        int lives = g.lives;
        StringBuilder losses = new StringBuilder();

        while (g.state == Game.PLAYING && frames < maxFrames) {
            g.update(DT);
            bot(g);
            hud.draw(c, DT);
            g.drawWorld(c);
            frames++;
            if (System.getenv("TD_TRACE") != null && frames % 90 == 0) {
                for (int i = 0; i < g.enemies.size(); i++) {
                    Enemy e = g.enemies.get(i);
                    if (e.type == Enemy.BOSS) {
                        System.out.printf("   f%-6d wave %2d boss hp %5.0f/%5.0f (%3.0f%%) "
                                + "progress %3.0f%%  enemies %d  towers %d%n",
                                frames, g.wave, e.hp, e.maxHp, 100 * e.hp / e.maxHp,
                                100 * e.dist / g.level.totalLen, g.enemies.size(),
                                g.towers.size());
                    }
                }
            }
            if (g.lives != lives) {
                losses.append(" w").append(g.wave).append(":-").append(lives - g.lives);
                lives = g.lives;
            }
        }

        boolean won = g.state == Game.VICTORY;
        System.out.printf(
                "map %d %-14s %-8s wave %2d/%d  lives %2d  gold %4d  towers %2d  "
                        + "frames %5d  %4dms%n",
                map, Level.nameOf(map), won ? "VICTORY" : (g.state == Game.GAME_OVER
                        ? "DEFEAT" : "TIMEOUT"),
                g.wave, Level.WAVES, g.lives, g.gold, g.towers.size(), frames,
                System.currentTimeMillis() - t0);
        if (losses.length() > 0) System.out.println("        leaks:" + losses);
        return g.state == Game.VICTORY || g.state == Game.GAME_OVER;
    }

    /** Exposed so the screenshot tool can drive the same bot. */
    static void playOneStep(Game g) { bot(g); }

    /** Small "plays like a human" bot: a handful of towers, then upgrades. */
    private static void bot(Game g) {
        if (g.state != Game.PLAYING) return;

        if (!g.waveActive && g.betweenWaves > 3f && g.towers.size() > 0 && g.lives > 4) {
            g.requestNextWave();
        }

        int maxTowers = Math.min(16, 5 + g.wave);
        int type = pickType(g);
        if (g.towers.size() < maxTowers && type >= 0 && g.gold >= Tower.COST[type] * 1.3f) {
            int[] cell = bestCell(g, type);
            if (cell != null && g.tryBuild(type, cell[0], cell[1])) {
                g.selected = null;
                return;
            }
        }

        // spend spare cash on upgrades, cheapest first
        Tower best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int i = 0; i < g.towers.size(); i++) {
            Tower t = g.towers.get(i);
            int cost = t.upgradeCost();
            if (cost > 0 && cost < bestCost) { bestCost = cost; best = t; }
        }
        if (best != null && g.gold >= bestCost) {
            g.selected = best;
            g.upgradeSelected();
            g.selected = null;
        }
    }

    private static int pickType(Game g) {
        int n = g.towers.size();
        if (n < 3) return Tower.ARROW;
        if (n == 3) return Tower.FROST;
        if (n == 4 || n == 7) return Tower.CANNON;
        if (n == 6 || n == 10) return Tower.TESLA;
        return n % 2 == 0 ? Tower.ARROW : Tower.CANNON;
    }

    private static int[] bestCell(Game g, int type) {
        ArrayList<float[]> road = new ArrayList<float[]>();
        for (int i = 0; i < Level.COLS; i++) {
            for (int j = 0; j < Level.ROWS; j++) {
                if (g.level.road[i][j]) {
                    road.add(new float[] { g.level.cellX(i), g.level.cellY(j) });
                }
            }
        }
        float range = Tower.rangeFor(type, 1, g.level.cs);
        int[] best = null;
        int bestScore = 0;
        for (int i = 0; i < Level.COLS; i++) {
            for (int j = 0; j < Level.ROWS; j++) {
                if (!g.level.buildable(i, j) || g.towerAt(i, j) != null) continue;
                float x = g.level.cellX(i), y = g.level.cellY(j);
                int score = 0;
                for (int k = 0; k < road.size(); k++) {
                    float[] p = road.get(k);
                    float dx = p[0] - x, dy = p[1] - y;
                    if (dx * dx + dy * dy <= range * range) score++;
                }
                if (score > bestScore) { bestScore = score; best = new int[] { i, j }; }
            }
        }
        return best;
    }

    /** Drive the HUD through every overlay and interaction path. */
    private static void exercise(Game g, Hud hud, Canvas c) {
        float w = W, h = H;

        // build bar: select each tower type, place one, inspect, upgrade, sell
        for (int i = 0; i < Tower.TYPES; i++) {
            g.gold = 5000;
            hud.onDown(w * (0.125f + i * 0.25f), h - Ui.dp(60));
            hud.onMove(w * 0.5f, h * 0.4f);
            hud.onUp(w * 0.5f, h * 0.4f);
            for (int f = 0; f < 5; f++) { g.update(DT); hud.draw(c, DT); g.drawWorld(c); }
            hud.onDown(w * 0.5f, h * 0.4f);          // select whatever is there
            hud.onDown(w * 0.68f, h - Ui.dp(58));    // upgrade
            hud.onDown(w * 0.68f, h - Ui.dp(58));
            hud.onDown(w * 0.90f, h - Ui.dp(58));    // sell
            hud.draw(c, DT);
        }

        int[] states = { Game.PAUSED, Game.GAME_OVER, Game.VICTORY, Game.MENU };
        for (int i = 0; i < states.length; i++) {
            g.state = states[i];
            for (int f = 0; f < 3; f++) hud.draw(c, DT);
            hud.onDown(w * 0.5f, h * 0.57f);
            hud.onDown(w * 0.5f, h * 0.63f);
            hud.onDown(w * 0.5f, h * 0.69f);
            hud.draw(c, DT);
        }
        System.out.println("ui paths exercised");
    }
}
