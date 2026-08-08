package com.claude.td;

import android.graphics.Canvas;
import android.graphics.Path;

/** All on-screen UI: top bar, build bar, overlays and their touch handling. */
public class Hud {

    private final Game g;

    private float w, h;
    private float pad, topBarY, topBarH, barY, barH;

    private final Ui.Btn[] build = new Ui.Btn[Tower.TYPES];
    private final Ui.Btn upgrade = new Ui.Btn();
    private final Ui.Btn sell = new Ui.Btn();
    private final Ui.Btn close = new Ui.Btn();
    private final Ui.Btn startWave = new Ui.Btn();
    private final Ui.Btn pause = new Ui.Btn();
    private final Ui.Btn speed = new Ui.Btn();

    private final Ui.Btn[] mapCard = new Ui.Btn[Level.MAP_COUNT];
    private final Ui.Btn primary = new Ui.Btn();     // resume / retry / next
    private final Ui.Btn secondary = new Ui.Btn();   // restart / retry
    private final Ui.Btn menu = new Ui.Btn();

    private final Path tri = new Path();
    private float blink;

    public Hud(Game game) {
        this.g = game;
        for (int i = 0; i < build.length; i++) build[i] = new Ui.Btn();
        for (int i = 0; i < mapCard.length; i++) mapCard[i] = new Ui.Btn();
    }

    public float topInset() { return Ui.dp(72); }
    public float bottomInset() { return Ui.dp(118); }

    public void layout(int width, int height) {
        w = width;
        h = height;
        pad = Ui.dp(10);
        topBarH = Ui.dp(52);
        topBarY = Ui.dp(12);
        barH = Ui.dp(96);
        barY = h - barH - Ui.dp(10);

        float bw = (w - pad * 2 - Ui.dp(8) * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            build[i].set(pad + i * (bw + Ui.dp(8)), barY, bw, barH);
        }

        float iw = (w - pad * 2);
        upgrade.set(pad + iw * 0.47f, barY + Ui.dp(10), iw * 0.30f, barH - Ui.dp(20));
        sell.set(pad + iw * 0.79f, barY + Ui.dp(10), iw * 0.21f, barH - Ui.dp(20));
        close.set(w - pad - Ui.dp(26), barY - Ui.dp(30), Ui.dp(26), Ui.dp(26));

        startWave.set(w * 0.5f - Ui.dp(105), barY - Ui.dp(56), Ui.dp(210), Ui.dp(46));

        float sq = Ui.dp(38);
        pause.set(w - pad - Ui.dp(6) - sq, topBarY + (topBarH - sq) / 2, sq, sq);
        speed.set(pause.x - sq - Ui.dp(6), topBarY + (topBarH - sq) / 2, sq, sq);

        float cardH = Ui.dp(86);
        float top = h * 0.34f;
        for (int i = 0; i < mapCard.length; i++) {
            mapCard[i].set(w * 0.1f, top + i * (cardH + Ui.dp(14)), w * 0.8f, cardH);
        }

        layoutOverlay();
    }

    /** Positions and visibility of the three overlay buttons for the current state. */
    private void layoutOverlay() {
        float pw = Math.min(w * 0.8f, Ui.dp(300));
        float px = (w - pw) / 2f;
        float y0 = h * 0.55f;
        float bh = Ui.dp(50);
        float step = Ui.dp(60);

        primary.set(px, y0, pw, bh);
        secondary.set(px, y0 + step, pw, bh);
        menu.set(px, y0 + step * 2, pw, bh);
        primary.visible = secondary.visible = menu.visible = true;
        primary.enabled = secondary.enabled = menu.enabled = true;

        switch (g.state) {
            case Game.PAUSED:
                break;
            case Game.GAME_OVER:
                secondary.visible = false;
                menu.set(px, y0 + step, pw, bh);
                break;
            case Game.VICTORY:
                menu.visible = g.level != null && g.level.index + 1 < Level.MAP_COUNT;
                break;
            default:
                primary.visible = secondary.visible = menu.visible = false;
                break;
        }
    }

    // ------------------------------------------------------------------ input

    /** @return true when the touch was consumed by the UI. */
    public boolean onDown(float x, float y) {
        layoutOverlay();
        switch (g.state) {
            case Game.MENU:
                for (int i = 0; i < mapCard.length; i++) {
                    if (i < g.save.unlocked() && mapCard[i].hit(x, y)) {
                        g.startLevel(i);
                        return true;
                    }
                }
                return true;
            case Game.PAUSED:
                if (primary.hit(x, y)) { g.state = Game.PLAYING; return true; }
                if (secondary.hit(x, y)) { g.restart(); return true; }
                if (menu.hit(x, y)) { g.toMenu(); return true; }
                return true;
            case Game.GAME_OVER:
                if (primary.hit(x, y)) { g.restart(); return true; }
                if (menu.hit(x, y)) { g.toMenu(); return true; }
                return true;
            case Game.VICTORY:
                if (primary.hit(x, y)) {
                    int next = g.level.index + 1;
                    if (next < Level.MAP_COUNT) g.startLevel(next); else g.toMenu();
                    return true;
                }
                if (secondary.hit(x, y)) { g.restart(); return true; }
                if (menu.hit(x, y)) { g.toMenu(); return true; }
                return true;
            default:
                break;
        }

        if (pause.hit(x, y)) { g.state = Game.PAUSED; return true; }
        if (speed.hit(x, y)) { g.speed = g.speed >= 3 ? 1 : g.speed + 1; return true; }

        if (!g.waveActive && startWave.hit(x, y)) { g.requestNextWave(); return true; }

        if (g.selected != null) {
            if (close.hit(x, y)) { g.selected = null; return true; }
            if (upgrade.hit(x, y)) { g.upgradeSelected(); return true; }
            if (sell.hit(x, y)) { g.sellSelected(); return true; }
            if (y >= barY) return true;
        } else {
            for (int i = 0; i < build.length; i++) {
                if (build[i].hit(x, y)) {
                    g.buildType = (g.buildType == i) ? -1 : i;
                    g.selected = null;
                    return true;
                }
            }
            if (y >= barY) return true;
        }

        // world
        int c = g.level.colAt(x), r = g.level.rowAt(y);
        if (g.buildType >= 0) {
            g.hoverC = c;
            g.hoverR = r;
            return true;
        }
        Tower t = g.towerAt(c, r);
        g.selected = t;
        return true;
    }

    public void onMove(float x, float y) {
        if (g.state != Game.PLAYING || g.buildType < 0) return;
        if (y >= barY) return;
        g.hoverC = g.level.colAt(x);
        g.hoverR = g.level.rowAt(y);
    }

    public void onUp(float x, float y) {
        if (g.state != Game.PLAYING) return;
        if (g.buildType >= 0 && g.hoverC >= 0) {
            g.tryBuild(g.buildType, g.hoverC, g.hoverR);
            g.hoverC = -1;
            g.hoverR = -1;
        }
    }

    // ---------------------------------------------------------------- drawing

    public void draw(Canvas c, float dt) {
        blink += dt;
        layoutOverlay();
        if (g.state == Game.MENU) {
            drawMenu(c);
            return;
        }
        drawTopBar(c);
        if (g.selected != null) drawSelection(c); else drawBuildBar(c);
        if (!g.waveActive && g.state == Game.PLAYING && g.wave < Level.WAVES) drawWaveButton(c);

        if (g.state == Game.PAUSED) drawPaused(c);
        else if (g.state == Game.GAME_OVER) drawGameOver(c);
        else if (g.state == Game.VICTORY) drawVictory(c);
    }

    private void drawTopBar(Canvas c) {
        float l = pad, t = topBarY, r = w - pad, b = topBarY + topBarH;
        Ui.rect(c, l, t, r, b, Ui.dp(14), Ui.PANEL);
        Ui.stroke(c, l, t, r, b, Ui.dp(14), Ui.PANEL_EDGE, Ui.dp(1.2f));
        float cy = (t + b) / 2f;

        float x = l + Ui.dp(16);
        heart(c, x, cy, Ui.dp(9), g.lives > 5 ? Ui.RED : Ui.mix(Ui.RED, 0xFFFFFFFF,
                (float) Math.abs(Math.sin(blink * 4)) * 0.6f));
        Ui.textLeft(c, String.valueOf(g.lives), x + Ui.dp(14), cy, Ui.dp(17), Ui.TEXT);

        x += Ui.dp(58);
        coin(c, x, cy, Ui.dp(9));
        Ui.textLeft(c, String.valueOf(g.gold), x + Ui.dp(14), cy, Ui.dp(17), Ui.GOLD);

        float infoRight = speed.x - Ui.dp(10);
        String wv = "WAVE " + Math.max(1, g.wave) + "/" + Level.WAVES;
        Ui.textRight(c, wv, infoRight, cy - Ui.dp(8), Ui.dp(14), Ui.TEXT);
        String sub = g.waveActive ? ("LEFT " + g.remainingEnemies()) : g.level.name;
        Ui.textRight(c, sub, infoRight, cy + Ui.dp(10), Ui.dp(11), Ui.TEXT_DIM);

        speed.label = "";
        speed.draw(c, 0xFF223040, Ui.TEXT, Ui.dp(14));
        Ui.text(c, g.speed + "x", speed.cx(), speed.cy(), Ui.dp(15),
                g.speed > 1 ? Ui.ACCENT : Ui.TEXT);

        pause.label = "";
        pause.draw(c, 0xFF223040, Ui.TEXT, Ui.dp(14));
        float pcx = pause.cx(), pcy = pause.cy(), ps = Ui.dp(6);
        Ui.rect(c, pcx - ps, pcy - ps * 1.4f, pcx - ps * 0.25f, pcy + ps * 1.4f, Ui.dp(2), Ui.TEXT);
        Ui.rect(c, pcx + ps * 0.25f, pcy - ps * 1.4f, pcx + ps, pcy + ps * 1.4f, Ui.dp(2), Ui.TEXT);
    }

    private void drawBuildBar(Canvas c) {
        for (int i = 0; i < build.length; i++) {
            Ui.Btn b = build[i];
            boolean afford = g.canAfford(i);
            boolean sel = g.buildType == i;
            int base = sel ? Ui.mix(Tower.COLOR[i], 0xFF101820, 0.62f) : Ui.PANEL;
            Ui.rect(c, b.x, b.y, b.x + b.w, b.y + b.h, Ui.dp(14), base);
            Ui.stroke(c, b.x, b.y, b.x + b.w, b.y + b.h, Ui.dp(14),
                    sel ? Tower.COLOR[i] : Ui.PANEL_EDGE, sel ? Ui.dp(2f) : Ui.dp(1.2f));

            float icx = b.cx(), icy = b.y + b.h * 0.33f;
            Tower.drawIcon(c, i, icx, icy, Ui.dp(14));
            Ui.text(c, Tower.NAME[i], b.cx(), b.y + b.h * 0.63f, Ui.dp(11),
                    afford ? Ui.TEXT : Ui.TEXT_DIM);
            coin(c, b.cx() - Ui.dp(16), b.y + b.h * 0.82f, Ui.dp(6.5f));
            Ui.textLeft(c, String.valueOf(Tower.COST[i]), b.cx() - Ui.dp(8),
                    b.y + b.h * 0.82f, Ui.dp(13), afford ? Ui.GOLD : Ui.RED);
        }
    }

    private void drawSelection(Canvas c) {
        Tower t = g.selected;
        float l = pad, r = w - pad;
        Ui.rect(c, l, barY, r, barY + barH, Ui.dp(14), Ui.PANEL);
        Ui.stroke(c, l, barY, r, barY + barH, Ui.dp(14),
                Ui.alpha(Tower.COLOR[t.type], 0.8f), Ui.dp(1.6f));

        Tower.drawIcon(c, t.type, l + Ui.dp(22), barY + barH * 0.42f, Ui.dp(13));
        Ui.textLeft(c, Tower.NAME[t.type] + " LV" + t.level, l + Ui.dp(40),
                barY + Ui.dp(24), Ui.dp(13), Ui.TEXT);
        Ui.textLeft(c, t.statLine(), l + Ui.dp(40), barY + Ui.dp(46), Ui.dp(10.5f),
                Ui.TEXT_DIM);
        Ui.textLeft(c, "RANGE " + String.format("%.1f", t.rangeCells()),
                l + Ui.dp(40), barY + Ui.dp(66), Ui.dp(10.5f), Ui.TEXT_DIM);

        int cost = t.upgradeCost();
        upgrade.enabled = cost > 0 && g.gold >= cost;
        upgrade.label = "";
        int upFill = cost < 0 ? 0xFF2A3444
                : (g.gold >= cost ? Ui.mix(Ui.GREEN, 0xFF0E1218, 0.45f) : 0xFF2A3444);
        Ui.rect(c, upgrade.x, upgrade.y, upgrade.x + upgrade.w, upgrade.y + upgrade.h,
                Ui.dp(10), upFill);
        Ui.stroke(c, upgrade.x, upgrade.y, upgrade.x + upgrade.w, upgrade.y + upgrade.h,
                Ui.dp(10), cost > 0 && g.gold >= cost ? Ui.GREEN : Ui.PANEL_EDGE, Ui.dp(1.3f));
        if (cost < 0) {
            Ui.text(c, "MAX LEVEL", upgrade.cx(), upgrade.cy(), Ui.dp(14), Ui.GOLD);
        } else {
            Ui.text(c, "UPGRADE", upgrade.cx(), upgrade.cy() - Ui.dp(10), Ui.dp(13), Ui.TEXT);
            coin(c, upgrade.cx() - Ui.dp(18), upgrade.cy() + Ui.dp(12), Ui.dp(6.5f));
            Ui.textLeft(c, String.valueOf(cost), upgrade.cx() - Ui.dp(10),
                    upgrade.cy() + Ui.dp(12), Ui.dp(14),
                    g.gold >= cost ? Ui.GOLD : Ui.RED);
        }

        Ui.rect(c, sell.x, sell.y, sell.x + sell.w, sell.y + sell.h, Ui.dp(10), 0xFF2A2230);
        Ui.stroke(c, sell.x, sell.y, sell.x + sell.w, sell.y + sell.h, Ui.dp(10),
                Ui.alpha(Ui.RED, 0.7f), Ui.dp(1.3f));
        Ui.text(c, "SELL", sell.cx(), sell.cy() - Ui.dp(10), Ui.dp(13), Ui.TEXT);
        Ui.text(c, "+" + t.sellValue(), sell.cx(), sell.cy() + Ui.dp(12), Ui.dp(13), Ui.GOLD);

        Ui.circle(c, close.cx(), close.cy(), Ui.dp(13), Ui.PANEL);
        Ui.ring(c, close.cx(), close.cy(), Ui.dp(13), Ui.PANEL_EDGE, Ui.dp(1.2f));
        float k = Ui.dp(4.5f);
        Ui.line(c, close.cx() - k, close.cy() - k, close.cx() + k, close.cy() + k, Ui.TEXT, Ui.dp(2));
        Ui.line(c, close.cx() + k, close.cy() - k, close.cx() - k, close.cy() + k, Ui.TEXT, Ui.dp(2));
    }

    private void drawWaveButton(Canvas c) {
        Ui.Btn b = startWave;
        float pulse = 0.5f + 0.5f * (float) Math.sin(blink * 3.4);
        Ui.rect(c, b.x, b.y, b.x + b.w, b.y + b.h, Ui.dp(24),
                Ui.mix(0xFF13324A, Ui.ACCENT, 0.18f + pulse * 0.12f));
        Ui.stroke(c, b.x, b.y, b.x + b.w, b.y + b.h, Ui.dp(24),
                Ui.alpha(Ui.ACCENT, 0.6f + pulse * 0.4f), Ui.dp(1.8f));

        // countdown fill
        float frac = Math.max(0f, Math.min(1f, g.betweenWaves / Game.WAVE_PAUSE));
        Ui.rect(c, b.x + Ui.dp(3), b.y + b.h - Ui.dp(7),
                b.x + Ui.dp(3) + (b.w - Ui.dp(6)) * (1f - frac), b.y + b.h - Ui.dp(3),
                Ui.dp(2), Ui.alpha(Ui.ACCENT, 0.7f));

        tri.reset();
        float ax = b.x + Ui.dp(24), ay = b.cy(), s = Ui.dp(8);
        tri.moveTo(ax - s * 0.5f, ay - s);
        tri.lineTo(ax + s, ay);
        tri.lineTo(ax - s * 0.5f, ay + s);
        tri.close();
        Ui.P.setShader(null);
        Ui.P.setColor(Ui.ACCENT);
        c.drawPath(tri, Ui.P);

        Ui.text(c, "START WAVE " + (g.wave + 1), b.cx() + Ui.dp(10), b.cy() - Ui.dp(4),
                Ui.dp(16), Ui.TEXT);
        Ui.text(c, "bonus +" + (int) Math.ceil(g.betweenWaves) * 4,
                b.cx() + Ui.dp(10), b.cy() + Ui.dp(14), Ui.dp(11), Ui.TEXT_DIM);
    }

    // -------------------------------------------------------------- overlays

    private void scrim(Canvas c, float a) {
        Ui.rect(c, 0, 0, w, h, 0, Ui.alpha(0xFF060A10, a));
    }

    private void panelTitle(Canvas c, String title, String sub, int color) {
        Ui.text(c, title, w / 2f, h * 0.33f, Ui.dp(40), color);
        if (sub != null) Ui.text(c, sub, w / 2f, h * 0.33f + Ui.dp(38), Ui.dp(16), Ui.TEXT_DIM);
    }

    private void drawPaused(Canvas c) {
        scrim(c, 0.78f);
        panelTitle(c, "PAUSED", g.level.name, Ui.ACCENT);
        primary.label = "RESUME";
        secondary.label = "RESTART";
        menu.label = "MAIN MENU";
        primary.draw(c, Ui.mix(Ui.ACCENT, 0xFF0E1218, 0.55f), Ui.TEXT, Ui.dp(18));
        secondary.draw(c, 0xFF222C3A, Ui.TEXT, Ui.dp(18));
        menu.draw(c, 0xFF222C3A, Ui.TEXT_DIM, Ui.dp(18));
    }

    private void drawGameOver(Canvas c) {
        scrim(c, 0.82f);
        panelTitle(c, "DEFEAT", "You held out for " + Math.max(0, g.wave - 1) + " waves",
                Ui.RED);
        primary.label = "RETRY";
        menu.label = "MAIN MENU";
        primary.draw(c, Ui.mix(Ui.RED, 0xFF0E1218, 0.55f), Ui.TEXT, Ui.dp(18));
        menu.draw(c, 0xFF222C3A, Ui.TEXT_DIM, Ui.dp(18));
    }

    private void drawVictory(Canvas c) {
        scrim(c, 0.82f);
        boolean more = g.level.index + 1 < Level.MAP_COUNT;
        panelTitle(c, "VICTORY", "Cleared with " + g.lives + " lives left",
                Ui.GOLD);
        primary.label = more ? "NEXT MAP" : "MAIN MENU";
        secondary.label = "REPLAY";
        menu.label = "MAIN MENU";
        primary.draw(c, Ui.mix(Ui.GOLD, 0xFF0E1218, 0.55f), 0xFF1A1206, Ui.dp(18));
        secondary.draw(c, 0xFF222C3A, Ui.TEXT, Ui.dp(18));
        menu.draw(c, 0xFF222C3A, Ui.TEXT_DIM, Ui.dp(18));
    }

    private void drawMenu(Canvas c) {
        // animated backdrop
        for (int i = 0; i < 26; i++) {
            float fx = (i * 137 % 100) / 100f * w;
            float fy = ((i * 71 % 100) / 100f * h + blink * (8 + i % 5) * 3) % h;
            Ui.circle(c, fx, fy, Ui.dp(1.5f + (i % 3)), 0x11FFFFFF);
        }

        Ui.text(c, "TOWER", w / 2f, h * 0.14f, Ui.dp(52), Ui.TEXT);
        Ui.text(c, "DEFENSE", w / 2f, h * 0.14f + Ui.dp(46), Ui.dp(52), Ui.ACCENT);
        Ui.text(c, "build \u2022 upgrade \u2022 survive 20 waves",
                w / 2f, h * 0.14f + Ui.dp(84), Ui.dp(12), Ui.TEXT_DIM);

        for (int i = 0; i < mapCard.length; i++) {
            Ui.Btn b = mapCard[i];
            boolean open = i < g.save.unlocked();
            int best = g.save.best(i);
            int accent = i == 0 ? Ui.GREEN : (i == 1 ? Ui.ORANGE : Ui.PURPLE);

            Ui.rect(c, b.x, b.y, b.x + b.w, b.y + b.h, Ui.dp(16),
                    open ? Ui.PANEL : 0xCC121820);
            Ui.stroke(c, b.x, b.y, b.x + b.w, b.y + b.h, Ui.dp(16),
                    open ? Ui.alpha(accent, 0.8f) : Ui.PANEL_EDGE, Ui.dp(1.5f));

            Ui.textLeft(c, "MAP " + (i + 1), b.x + Ui.dp(18), b.y + Ui.dp(26),
                    Ui.dp(12), Ui.TEXT_DIM);
            Ui.textLeft(c, Level.nameOf(i), b.x + Ui.dp(18), b.y + Ui.dp(48),
                    Ui.dp(19), open ? Ui.TEXT : Ui.TEXT_DIM);

            for (int s = 0; s < 3; s++) {
                Ui.circle(c, b.x + Ui.dp(20) + s * Ui.dp(13), b.y + Ui.dp(68), Ui.dp(4f),
                        s <= i ? accent : 0xFF2B3849);
            }

            if (!open) {
                Ui.textRight(c, "LOCKED", b.x + b.w - Ui.dp(18), b.cy(), Ui.dp(14), Ui.TEXT_DIM);
                Ui.textRight(c, "clear map " + i, b.x + b.w - Ui.dp(18), b.cy() + Ui.dp(20),
                        Ui.dp(11), Ui.TEXT_DIM);
            } else if (g.save.cleared(i)) {
                Ui.textRight(c, "CLEARED", b.x + b.w - Ui.dp(18), b.cy() - Ui.dp(8),
                        Ui.dp(14), Ui.GOLD);
                Ui.textRight(c, "PLAY AGAIN", b.x + b.w - Ui.dp(18), b.cy() + Ui.dp(14),
                        Ui.dp(11), Ui.TEXT_DIM);
            } else {
                Ui.textRight(c, best > 0 ? ("BEST WAVE " + best) : "NEW",
                        b.x + b.w - Ui.dp(18), b.cy() - Ui.dp(8), Ui.dp(14), accent);
                Ui.textRight(c, "TAP TO PLAY", b.x + b.w - Ui.dp(18), b.cy() + Ui.dp(14),
                        Ui.dp(11), Ui.TEXT_DIM);
            }
        }

        Ui.text(c, "v1.0", w / 2f, h - Ui.dp(26), Ui.dp(11), Ui.TEXT_DIM);
    }

    // ----------------------------------------------------------------- icons

    private void heart(Canvas c, float x, float y, float s, int color) {
        Ui.circle(c, x - s * 0.42f, y - s * 0.25f, s * 0.52f, color);
        Ui.circle(c, x + s * 0.42f, y - s * 0.25f, s * 0.52f, color);
        tri.reset();
        tri.moveTo(x - s * 0.92f, y - s * 0.05f);
        tri.lineTo(x + s * 0.92f, y - s * 0.05f);
        tri.lineTo(x, y + s * 0.95f);
        tri.close();
        Ui.P.setShader(null);
        Ui.P.setColor(color);
        c.drawPath(tri, Ui.P);
    }

    private void coin(Canvas c, float x, float y, float r) {
        Ui.circle(c, x, y, r, Ui.GOLD);
        Ui.circle(c, x, y, r * 0.62f, Ui.mix(Ui.GOLD, 0xFF8A5A00, 0.45f));
    }
}
