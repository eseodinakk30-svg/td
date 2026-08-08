package com.claude.td;

import android.graphics.Canvas;

import java.util.ArrayList;

/** Game state, simulation and wave scheduling. */
public class Game {

    public static final int MENU = 0;
    public static final int PLAYING = 1;
    public static final int PAUSED = 2;
    public static final int GAME_OVER = 3;
    public static final int VICTORY = 4;

    public int state = MENU;

    public Level level;
    public final ArrayList<Enemy> enemies = new ArrayList<Enemy>();
    public final ArrayList<Tower> towers = new ArrayList<Tower>();
    public final ArrayList<Projectile> projectiles = new ArrayList<Projectile>();
    public final Fx fx = new Fx();
    public final Save save;

    public int gold;
    public int lives;
    public int wave;
    public boolean waveActive;
    public float betweenWaves;          // countdown to the automatic next wave
    public static final float WAVE_PAUSE = 9f;

    public int speed = 1;               // 1x / 2x / 3x
    public float time;                  // seconds since the level started
    public float shake;

    /** Build/selection interaction state, shared with the HUD. */
    public int buildType = -1;
    public Tower selected;
    public int hoverC = -1, hoverR = -1;

    private final ArrayList<Spawn> queue = new ArrayList<Spawn>();
    private final float[] tmp = new float[3];
    private int screenW, screenH;
    private float topInset, bottomInset;

    private static class Spawn {
        int type;
        float at;
        float hpMul;
    }

    public Game(Save save) {
        this.save = save;
    }

    public void resize(int w, int h, float top, float bottom) {
        screenW = w;
        screenH = h;
        topInset = top;
        bottomInset = bottom;
        if (level != null) {
            level.layout(w, h, top, bottom);
            for (int i = 0; i < towers.size(); i++) towers.get(i).place(level);
        }
    }

    public int width() { return screenW; }
    public int height() { return screenH; }

    // ------------------------------------------------------------- lifecycle

    public void startLevel(int mapIndex) {
        level = new Level(mapIndex);
        level.layout(screenW, screenH, topInset, bottomInset);
        enemies.clear();
        towers.clear();
        projectiles.clear();
        queue.clear();
        fx.clear();
        gold = level.startGold;
        lives = level.startLives;
        wave = 0;
        waveActive = false;
        betweenWaves = 6f;
        speed = 1;
        time = 0;
        shake = 0;
        buildType = -1;
        selected = null;
        state = PLAYING;
    }

    public void restart() {
        if (level != null) startLevel(level.index);
    }

    public void toMenu() {
        state = MENU;
        buildType = -1;
        selected = null;
    }

    // ------------------------------------------------------------- the update

    public void update(float dtReal) {
        if (state != PLAYING) {
            fx.update(dtReal);
            return;
        }
        int steps = speed;
        float dt = dtReal;
        if (dt > 0.05f) dt = 0.05f;
        for (int s = 0; s < steps; s++) step(dt);
    }

    private void step(float dt) {
        time += dt;
        if (shake > 0) shake -= dt * 3.2f;

        if (!waveActive) {
            betweenWaves -= dt;
            if (betweenWaves <= 0) startNextWave(false);
        }

        // spawn scheduled creeps
        for (int i = queue.size() - 1; i >= 0; i--) {
            Spawn sp = queue.get(i);
            sp.at -= dt;
            if (sp.at <= 0) {
                Enemy e = new Enemy(sp.type, sp.hpMul, 0);
                e.hpMul = sp.hpMul;
                enemies.add(e);
                queue.remove(i);
            }
        }

        for (int i = 0; i < towers.size(); i++) towers.get(i).update(dt, this);

        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            p.update(dt, this);
            if (!p.alive) projectiles.remove(i);
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.update(dt, level, tmp);
            if (!e.alive) {
                if (e.leaked) leak(e);
                enemies.remove(i);
            }
        }

        fx.update(dt);

        if (waveActive && queue.isEmpty() && enemies.isEmpty()) finishWave();
        if (lives <= 0) gameOver();
    }

    private void leak(Enemy e) {
        lives -= e.livesCost;
        shake = 1f;
        fx.ring(e.x, e.y, level.cs * 0.9f, Ui.RED);
        fx.label(e.x, e.y, "-" + e.livesCost, Ui.RED, level.cs * 0.42f);
        if (lives < 0) lives = 0;
    }

    private void gameOver() {
        if (state == GAME_OVER) return;
        state = GAME_OVER;
        save.reportWave(level.index, Math.max(0, wave - 1));
    }

    /** Damage a creep and handle rewards / death effects. */
    public void dealDamage(Enemy e, float amount, boolean ignoreArmor) {
        if (!e.alive) return;
        e.damage(amount, ignoreArmor);
        if (!e.alive) kill(e);
    }

    private void kill(Enemy e) {
        gold += e.gold;
        fx.burst(e.x, e.y, Enemy.color(e.type), e.type == Enemy.BOSS ? 40 : 10);
        fx.label(e.x, e.y - e.radius(level.cs), "+" + e.gold, Ui.GOLD, level.cs * 0.34f);
        if (e.type == Enemy.BOSS) {
            shake = 1.2f;
            fx.ring(e.x, e.y, level.cs * 2.4f, Ui.RED);
        }
        if (e.type == Enemy.SPLITTER) {
            for (int n = 0; n < 2; n++) {
                Enemy m = new Enemy(Enemy.MINI, e.hpMul, Math.max(0, e.dist - n * level.cs * 0.35f));
                m.hpMul = e.hpMul;
                enemies.add(m);
            }
        }
    }

    // ----------------------------------------------------------------- waves

    public void requestNextWave() {
        if (state == PLAYING && !waveActive) startNextWave(true);
    }

    private void startNextWave(boolean early) {
        if (wave >= Level.WAVES) return;
        if (early) {
            int bonus = (int) Math.ceil(betweenWaves) * 4;
            if (bonus > 0) {
                gold += bonus;
                fx.label(screenW * 0.5f, topInset + Ui.dp(70), "EARLY BONUS +" + bonus,
                        Ui.GOLD, Ui.dp(20));
            }
        }
        wave++;
        waveActive = true;
        buildWave(wave);
        save.reportWave(level.index, wave - 1);
    }

    private void finishWave() {
        waveActive = false;
        int bonus = 25 + wave * 6;
        gold += bonus;
        fx.label(screenW * 0.5f, topInset + Ui.dp(70), "WAVE CLEAR  +" + bonus,
                Ui.GREEN, Ui.dp(22));
        if (wave >= Level.WAVES) {
            state = VICTORY;
            save.reportWave(level.index, Level.WAVES);
            save.unlock(level.index + 2);
        } else {
            betweenWaves = WAVE_PAUSE;
        }
    }

    private float hpMul(int w) {
        float t = w - 1;
        return level.difficulty * (1f + 0.17f * t + 0.0068f * t * t);
    }

    private void buildWave(int w) {
        float mul = hpMul(w);
        boolean boss = (w % 10) == 0;

        if (boss) {
            int bosses = (w >= 20) ? 2 : 1;
            float bossMul = level.difficulty * (1f + 0.095f * (w - 1));
            for (int i = 0; i < bosses; i++) group(Enemy.BOSS, 1, 0, 3f + i * 7f, bossMul);
            group(Enemy.GRUNT, 8 + w / 2, 0.55f, 1f, mul);
            group(Enemy.TANK, 2 + w / 10, 1.8f, 7f, mul);
            group(Enemy.RUNNER, 6 + w / 3, 0.4f, 12f, mul);
            return;
        }

        int grunts = 6 + (int) (w * 0.9f);
        int runners = w >= 3 ? 2 + w / 2 : 0;
        int tanks = w >= 4 ? w / 4 : 0;
        int splitters = w >= 6 ? 1 + w / 6 : 0;

        float gap = Math.max(0.42f, 0.8f - w * 0.015f);
        group(Enemy.GRUNT, grunts, gap, 0.6f, mul);
        if (runners > 0) group(Enemy.RUNNER, runners, 0.45f, 3.5f, mul);
        if (tanks > 0) group(Enemy.TANK, tanks, 1.7f, 5.5f, mul);
        if (splitters > 0) group(Enemy.SPLITTER, splitters, 1.2f, 8f, mul);
    }

    private void group(int type, int count, float gap, float delay, float mul) {
        for (int i = 0; i < count; i++) {
            Spawn s = new Spawn();
            s.type = type;
            s.at = delay + i * gap;
            s.hpMul = mul;
            queue.add(s);
        }
    }

    public int remainingEnemies() { return queue.size() + enemies.size(); }

    // ----------------------------------------------------------- build / sell

    public boolean canAfford(int type) { return gold >= Tower.COST[type]; }

    public boolean tryBuild(int type, int c, int r) {
        if (!level.buildable(c, r)) return false;
        if (towerAt(c, r) != null) return false;
        if (gold < Tower.COST[type]) return false;
        gold -= Tower.COST[type];
        Tower t = new Tower(type, c, r);
        t.place(level);
        towers.add(t);
        fx.ring(t.x, t.y, level.cs * 0.8f, Tower.COLOR[type]);
        fx.burst(t.x, t.y, Tower.COLOR[type], 12);
        selected = t;
        buildType = -1;
        return true;
    }

    public Tower towerAt(int c, int r) {
        for (int i = 0; i < towers.size(); i++) {
            Tower t = towers.get(i);
            if (t.col == c && t.row == r) return t;
        }
        return null;
    }

    public void upgradeSelected() {
        if (selected == null) return;
        int cost = selected.upgradeCost();
        if (cost < 0 || gold < cost) return;
        gold -= cost;
        selected.upgrade();
        fx.ring(selected.x, selected.y, level.cs * 0.9f, Tower.COLOR[selected.type]);
        fx.label(selected.x, selected.y - level.cs * 0.5f, "LV " + selected.level,
                Ui.GOLD, level.cs * 0.38f);
    }

    public void sellSelected() {
        if (selected == null) return;
        gold += selected.sellValue();
        fx.burst(selected.x, selected.y, Ui.GOLD, 14);
        fx.label(selected.x, selected.y - level.cs * 0.4f, "+" + selected.sellValue(),
                Ui.GOLD, level.cs * 0.36f);
        towers.remove(selected);
        selected = null;
    }

    // --------------------------------------------------------------- drawing

    public void drawWorld(Canvas c) {
        level.draw(c, time);

        if (buildType >= 0) {
            level.drawBuildGrid(c, hoverC, hoverR, canAfford(buildType));
            if (hoverC >= 0 && level.buildable(hoverC, hoverR) && towerAt(hoverC, hoverR) == null) {
                float rr = Tower.rangeFor(buildType, 1, level.cs);
                Ui.circle(c, level.cellX(hoverC), level.cellY(hoverR), rr,
                        Ui.alpha(Tower.COLOR[buildType], 0.10f));
                Ui.ring(c, level.cellX(hoverC), level.cellY(hoverR), rr,
                        Ui.alpha(Tower.COLOR[buildType], 0.55f), Ui.dp(1.6f));
            }
        }

        if (selected != null) {
            Ui.circle(c, selected.x, selected.y, selected.range(level.cs),
                    Ui.alpha(Tower.COLOR[selected.type], 0.10f));
            Ui.ring(c, selected.x, selected.y, selected.range(level.cs),
                    Ui.alpha(Tower.COLOR[selected.type], 0.6f), Ui.dp(1.8f));
            Ui.stroke(c, selected.x - level.cs * 0.5f, selected.y - level.cs * 0.5f,
                    selected.x + level.cs * 0.5f, selected.y + level.cs * 0.5f,
                    Ui.dp(6), Ui.alpha(0xFFFFFFFF, 0.7f), Ui.dp(2));
        }

        for (int i = 0; i < towers.size(); i++) towers.get(i).draw(c, level.cs);
        for (int i = 0; i < enemies.size(); i++) enemies.get(i).draw(c, level.cs);
        for (int i = 0; i < projectiles.size(); i++) projectiles.get(i).draw(c);
        fx.draw(c);
    }
}
