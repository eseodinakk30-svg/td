package com.claude.td;

import android.graphics.Canvas;

import java.util.ArrayList;

/** A defensive structure sitting on one grid cell. */
public class Tower {

    public static final int ARROW = 0;
    public static final int CANNON = 1;
    public static final int FROST = 2;
    public static final int TESLA = 3;
    public static final int TYPES = 4;

    public static final String[] NAME = { "ARROW", "CANNON", "FROST", "TESLA" };
    public static final String[] DESC = {
            "Rapid single shots",
            "Slow, heavy splash",
            "Chills and slows",
            "Chains between foes" };
    public static final int[] COST = { 60, 110, 85, 160 };
    public static final int[] COLOR = { 0xFF5CE08A, 0xFFFF9F45, 0xFF66E0FF, 0xFFB07CFF };

    private static final int[][] UPGRADE = { {55, 120}, {95, 200}, {75, 155}, {135, 275} };
    private static final float[][] DMG = {
            {12, 20, 33}, {30, 52, 84}, {6, 11, 17}, {18, 29, 44} };
    private static final float[][] CD = {
            {0.55f, 0.46f, 0.38f}, {1.50f, 1.35f, 1.18f},
            {1.00f, 0.90f, 0.80f}, {1.10f, 1.00f, 0.90f} };
    private static final float[][] RANGE = {
            {2.40f, 2.70f, 3.00f}, {2.20f, 2.40f, 2.60f},
            {2.20f, 2.40f, 2.65f}, {2.30f, 2.55f, 2.85f} };
    private static final float[] SPLASH = { 0.90f, 1.05f, 1.25f };
    private static final float[] SLOW_F = { 0.55f, 0.45f, 0.35f };
    private static final float[] SLOW_D = { 1.5f, 1.9f, 2.3f };
    private static final int[] CHAINS = { 3, 4, 5 };

    public final int type;
    public final int col, row;
    public float x, y;
    public int level = 1;
    public int invested;

    private float cd;
    private float turret;
    private float recoil;
    private float idleSpin;

    public Tower(int type, int col, int row) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.invested = COST[type];
        this.turret = -1.5707964f;
    }

    public static int upgradeCost(int type, int level) {
        return level >= 3 ? -1 : UPGRADE[type][level - 1];
    }

    public int upgradeCost() { return upgradeCost(type, level); }
    public int sellValue() { return (int) (invested * 0.65f); }
    public float damage() { return DMG[type][level - 1]; }
    public float cooldown() { return CD[type][level - 1]; }
    public float rangeCells() { return RANGE[type][level - 1]; }
    public float range(float cs) { return RANGE[type][level - 1] * cs; }

    public static float rangeFor(int type, int level, float cs) {
        return RANGE[type][level - 1] * cs;
    }

    public String statLine() {
        switch (type) {
            case CANNON:
                return "DMG " + (int) damage() + "  AREA";
            case FROST:
                return "SLOW " + (int) ((1 - SLOW_F[level - 1]) * 100) + "%  DMG "
                        + (int) damage();
            case TESLA:
                return "DMG " + (int) damage() + "  CHAIN " + CHAINS[level - 1];
            default:
                return "DMG " + (int) damage() + "  " + rate();
        }
    }

    private String rate() {
        float r = 1f / cooldown();
        return String.format("%.1f/s", r);
    }

    public void upgrade() {
        int c = upgradeCost();
        if (c > 0) {
            invested += c;
            level++;
        }
    }

    public void place(Level lv) {
        x = lv.cellX(col);
        y = lv.cellY(row);
    }

    public void update(float dt, Game g) {
        if (cd > 0) cd -= dt;
        if (recoil > 0) recoil -= dt * 6f;
        idleSpin += dt;

        Enemy target = pick(g);
        if (target == null) return;

        float want = (float) Math.atan2(target.y - y, target.x - x);
        turret = approachAngle(turret, want, dt * 9f);

        if (cd <= 0 && Math.abs(angleDiff(turret, want)) < 0.5f) {
            fire(g, target);
            cd = cooldown();
            recoil = 1f;
        }
    }

    /** Target the creep that has walked the furthest and is still in range. */
    private Enemy pick(Game g) {
        float r = range(g.level.cs);
        float r2 = r * r;
        Enemy best = null;
        for (int i = 0; i < g.enemies.size(); i++) {
            Enemy e = g.enemies.get(i);
            if (!e.alive) continue;
            float dx = e.x - x, dy = e.y - y;
            if (dx * dx + dy * dy > r2) continue;
            if (best == null || e.dist > best.dist) best = e;
        }
        return best;
    }

    private void fire(Game g, Enemy target) {
        float cs = g.level.cs;
        float muzzle = cs * 0.42f;
        float mx = x + (float) Math.cos(turret) * muzzle;
        float my = y + (float) Math.sin(turret) * muzzle;

        switch (type) {
            case CANNON: {
                Projectile p = new Projectile(mx, my, target, damage(), COLOR[type]);
                p.speed = cs * 7.5f;
                p.radius = cs * 0.12f;
                p.splash = SPLASH[level - 1] * cs;
                p.trail = true;
                g.projectiles.add(p);
                g.fx.flash(mx, my, cs * 0.28f, COLOR[type]);
                break;
            }
            case FROST: {
                Projectile p = new Projectile(mx, my, target, damage(), COLOR[type]);
                p.speed = cs * 9f;
                p.radius = cs * 0.09f;
                p.slowFactor = SLOW_F[level - 1];
                p.slowDuration = SLOW_D[level - 1];
                p.splash = cs * 0.55f;
                g.projectiles.add(p);
                break;
            }
            case TESLA: {
                chain(g, target);
                break;
            }
            default: {
                Projectile p = new Projectile(mx, my, target, damage(), COLOR[type]);
                p.speed = cs * 13f;
                p.radius = cs * 0.06f;
                p.elongated = true;
                g.projectiles.add(p);
                break;
            }
        }
    }

    private void chain(Game g, Enemy first) {
        float cs = g.level.cs;
        int jumps = CHAINS[level - 1];
        float dmg = damage();
        float hopRange = cs * 1.9f;

        ArrayList<Enemy> hit = new ArrayList<Enemy>();
        Enemy cur = first;
        float fx = x, fy = y;
        for (int n = 0; n < jumps && cur != null; n++) {
            g.fx.beam(fx, fy, cur.x, cur.y, COLOR[type]);
            g.dealDamage(cur, dmg, false);
            hit.add(cur);
            fx = cur.x;
            fy = cur.y;
            dmg *= 0.75f;

            Enemy next = null;
            float bd = hopRange * hopRange;
            for (int i = 0; i < g.enemies.size(); i++) {
                Enemy e = g.enemies.get(i);
                if (!e.alive || hit.contains(e)) continue;
                float dx = e.x - fx, dy = e.y - fy;
                float d2 = dx * dx + dy * dy;
                if (d2 < bd) { bd = d2; next = e; }
            }
            cur = next;
        }
    }

    private static float angleDiff(float a, float b) {
        float d = b - a;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private static float approachAngle(float cur, float want, float step) {
        float d = angleDiff(cur, want);
        if (Math.abs(d) <= step) return want;
        return cur + Math.signum(d) * step;
    }

    // ---------------------------------------------------------------- drawing

    public void draw(Canvas c, float cs) {
        int col = COLOR[type];
        float r = cs * 0.42f;

        Ui.circle(c, x, y + cs * 0.06f, r, 0x44000000);
        Ui.rect(c, x - r, y - r, x + r, y + r, cs * 0.16f, 0xFF222C3A);
        Ui.stroke(c, x - r, y - r, x + r, y + r, cs * 0.16f, Ui.alpha(col, 0.6f), Ui.dp(1.6f));
        Ui.circle(c, x, y, cs * 0.26f, 0xFF2E3A4B);

        float rec = Math.max(0, recoil) * cs * 0.09f;
        float dx = (float) Math.cos(turret), dy = (float) Math.sin(turret);
        float bx = x - dx * rec, by = y - dy * rec;

        drawTurret(c, cs, bx, by, dx, dy, col);

        // level pips
        for (int i = 0; i < level; i++) {
            float px = x - r + cs * 0.11f + i * cs * 0.11f;
            Ui.circle(c, px, y + r - cs * 0.09f, cs * 0.036f, Ui.GOLD);
        }
    }

    private void drawTurret(Canvas c, float cs, float bx, float by,
                            float dx, float dy, int col) {
        switch (type) {
            case CANNON: {
                float len = cs * 0.46f, w = cs * 0.15f;
                Ui.line(c, bx, by, bx + dx * len, by + dy * len, col, w * 2f);
                Ui.circle(c, bx, by, cs * 0.19f, Ui.mix(col, 0xFF000000, 0.25f));
                Ui.circle(c, bx + dx * len, by + dy * len, w, Ui.mix(col, 0xFFFFFFFF, 0.25f));
                break;
            }
            case FROST: {
                for (int i = 0; i < 3; i++) {
                    double a = idleSpin * 1.4 + i * Math.PI * 2 / 3;
                    float px = bx + (float) Math.cos(a) * cs * 0.2f;
                    float py = by + (float) Math.sin(a) * cs * 0.2f;
                    Ui.circle(c, px, py, cs * 0.075f, Ui.alpha(col, 0.85f));
                }
                Ui.circle(c, bx, by, cs * 0.13f, col);
                break;
            }
            case TESLA: {
                Ui.line(c, bx, by, bx + dx * cs * 0.3f, by + dy * cs * 0.3f, col, cs * 0.1f);
                float t = (float) Math.sin(idleSpin * 6) * 0.5f + 0.5f;
                Ui.circle(c, bx + dx * cs * 0.32f, by + dy * cs * 0.32f,
                        cs * (0.09f + 0.03f * t), Ui.mix(col, 0xFFFFFFFF, t));
                Ui.ring(c, bx, by, cs * 0.2f, Ui.alpha(col, 0.5f), Ui.dp(1.4f));
                break;
            }
            default: {
                float len = cs * 0.4f;
                Ui.line(c, bx - dx * cs * 0.05f, by - dy * cs * 0.05f,
                        bx + dx * len, by + dy * len, col, cs * 0.11f);
                Ui.line(c, bx - dy * cs * 0.16f, by + dx * cs * 0.16f,
                        bx + dy * cs * 0.16f, by - dx * cs * 0.16f,
                        Ui.mix(col, 0xFF000000, 0.3f), cs * 0.09f);
                break;
            }
        }
    }

    /** Small pictogram used by the build bar. */
    public static void drawIcon(Canvas c, int type, float x, float y, float s) {
        int col = COLOR[type];
        switch (type) {
            case CANNON:
                Ui.circle(c, x, y + s * 0.15f, s * 0.42f, Ui.mix(col, 0xFF000000, 0.3f));
                Ui.line(c, x, y + s * 0.15f, x, y - s * 0.6f, col, s * 0.3f);
                break;
            case FROST:
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i;
                    Ui.line(c, x, y, x + (float) Math.cos(a) * s * 0.6f,
                            y + (float) Math.sin(a) * s * 0.6f, col, s * 0.13f);
                }
                Ui.circle(c, x, y, s * 0.18f, Ui.mix(col, 0xFFFFFFFF, 0.4f));
                break;
            case TESLA:
                Ui.line(c, x - s * 0.25f, y - s * 0.6f, x + s * 0.1f, y - s * 0.05f, col, s * 0.16f);
                Ui.line(c, x + s * 0.1f, y - s * 0.05f, x - s * 0.12f, y + s * 0.05f, col, s * 0.16f);
                Ui.line(c, x - s * 0.12f, y + s * 0.05f, x + s * 0.22f, y + s * 0.6f, col, s * 0.16f);
                break;
            default:
                Ui.line(c, x, y + s * 0.5f, x, y - s * 0.5f, col, s * 0.18f);
                Ui.line(c, x - s * 0.3f, y - s * 0.12f, x, y - s * 0.55f, col, s * 0.16f);
                Ui.line(c, x + s * 0.3f, y - s * 0.12f, x, y - s * 0.55f, col, s * 0.16f);
                break;
        }
    }
}
