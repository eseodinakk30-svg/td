package com.claude.td;

import android.graphics.Canvas;
import android.graphics.Path;

/** A creep walking the road. */
public class Enemy {

    public static final int GRUNT = 0;
    public static final int RUNNER = 1;
    public static final int TANK = 2;
    public static final int SPLITTER = 3;
    public static final int MINI = 4;
    public static final int BOSS = 5;

    private static final float[] BASE_HP    = { 46, 30, 165, 78, 24, 1650 };
    private static final float[] BASE_SPEED = { 1.45f, 2.75f, 0.92f, 1.5f, 2.2f, 0.72f };
    private static final float[] ARMOR      = { 0, 0, 6, 1, 0, 9 };
    private static final int[]   GOLD       = { 8, 7, 20, 13, 3, 160 };
    private static final int[]   LIVES      = { 1, 1, 2, 1, 1, 6 };
    private static final float[] SIZE       = { 0.30f, 0.24f, 0.36f, 0.32f, 0.19f, 0.62f };
    private static final int[]   COLOR      = {
            0xFFE05C5C, 0xFFF2C744, 0xFF8FA3BF, 0xFF9C6BE0, 0xFFE07CA8, 0xFFFF3B5C };
    private static final String[] NAME = {
            "Grunt", "Runner", "Tank", "Splitter", "Spawn", "BOSS" };

    public final int type;
    public float maxHp, hp;
    public final float baseSpeed;
    public final float armor;
    public final int gold;
    public final int livesCost;

    /** Wave scaling applied at spawn; inherited by anything this creep spawns. */
    public float hpMul = 1f;

    public float dist;
    public float x, y, angle;
    public boolean alive = true;
    public boolean leaked = false;

    public float slowFactor = 1f;
    public float slowTime = 0f;
    public float hitFlash = 0f;
    public float phase;

    private static final Path TMP = new Path();

    public Enemy(int type, float hpMul, float dist) {
        this.type = type;
        this.maxHp = BASE_HP[type] * hpMul;
        this.hp = maxHp;
        this.baseSpeed = BASE_SPEED[type];
        this.armor = ARMOR[type];
        this.gold = GOLD[type];
        this.livesCost = LIVES[type];
        this.dist = dist;
        this.phase = (float) (Math.random() * 6.28);
    }

    public static String name(int type) { return NAME[type]; }
    public static int color(int type) { return COLOR[type]; }

    public float radius(float cs) { return SIZE[type] * cs; }

    public void update(float dt, Level level, float[] tmp) {
        if (slowTime > 0) {
            slowTime -= dt;
            if (slowTime <= 0) slowFactor = 1f;
        }
        if (hitFlash > 0) hitFlash -= dt * 4f;
        phase += dt * 6f;

        dist += baseSpeed * slowFactor * level.cs * dt;
        if (dist >= level.totalLen) {
            leaked = true;
            alive = false;
        }
        level.posAt(dist, tmp);
        x = tmp[0];
        y = tmp[1];
        angle = tmp[2];
    }

    /** @return damage actually dealt. */
    public float damage(float amount, boolean ignoreArmor) {
        float d = ignoreArmor ? amount : Math.max(amount * 0.15f, amount - armor);
        hp -= d;
        hitFlash = 1f;
        if (hp <= 0) {
            hp = 0;
            alive = false;
        }
        return d;
    }

    public void applySlow(float factor, float duration) {
        if (type == BOSS) {
            factor = Math.max(factor, 0.72f);
            duration *= 0.6f;
        }
        if (factor < slowFactor || slowTime <= 0) slowFactor = factor;
        slowTime = Math.max(slowTime, duration);
    }

    public void draw(Canvas c, float cs) {
        float r = radius(cs);
        float bob = (float) Math.sin(phase) * r * 0.08f;
        int col = COLOR[type];
        if (slowTime > 0) col = Ui.mix(col, 0xFF7FE6FF, 0.45f);
        if (hitFlash > 0) col = Ui.mix(col, 0xFFFFFFFF, Math.min(1f, hitFlash) * 0.7f);

        // shadow
        Ui.circle(c, x, y + r * 0.75f, r * 0.8f, 0x33000000);

        switch (type) {
            case RUNNER: {
                TMP.reset();
                float a = angle;
                float nx = (float) Math.cos(a), ny = (float) Math.sin(a);
                TMP.moveTo(x + nx * r * 1.5f, y + ny * r * 1.5f + bob);
                TMP.lineTo(x - nx * r + ny * r, y - ny * r - nx * r + bob);
                TMP.lineTo(x - nx * r * 0.4f, y - ny * r * 0.4f + bob);
                TMP.lineTo(x - nx * r - ny * r, y - ny * r + nx * r + bob);
                TMP.close();
                Ui.P.setShader(null);
                Ui.P.setColor(col);
                c.drawPath(TMP, Ui.P);
                break;
            }
            case TANK: {
                Ui.rect(c, x - r, y - r + bob, x + r, y + r + bob, r * 0.3f, col);
                Ui.rect(c, x - r * 0.55f, y - r * 0.55f + bob, x + r * 0.55f, y + r * 0.55f + bob,
                        r * 0.2f, Ui.mix(col, 0xFF000000, 0.35f));
                Ui.stroke(c, x - r, y - r + bob, x + r, y + r + bob, r * 0.3f,
                        Ui.mix(col, 0xFFFFFFFF, 0.4f), Ui.dp(1.5f));
                break;
            }
            case SPLITTER: {
                TMP.reset();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i + phase * 0.15;
                    float vx = x + (float) Math.cos(a) * r;
                    float vy = y + (float) Math.sin(a) * r + bob;
                    if (i == 0) TMP.moveTo(vx, vy); else TMP.lineTo(vx, vy);
                }
                TMP.close();
                Ui.P.setShader(null);
                Ui.P.setColor(col);
                c.drawPath(TMP, Ui.P);
                Ui.circle(c, x, y + bob, r * 0.35f, Ui.mix(col, 0xFFFFFFFF, 0.5f));
                break;
            }
            case BOSS: {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i + phase * 0.25;
                    Ui.circle(c, x + (float) Math.cos(a) * r * 0.95f,
                            y + (float) Math.sin(a) * r * 0.95f + bob, r * 0.22f,
                            Ui.mix(col, 0xFF000000, 0.25f));
                }
                Ui.circle(c, x, y + bob, r * 0.85f, col);
                Ui.ring(c, x, y + bob, r * 0.85f, Ui.mix(col, 0xFFFFFFFF, 0.55f), Ui.dp(2.5f));
                Ui.circle(c, x, y + bob, r * 0.34f, 0xFF2B0A11);
                break;
            }
            default: {
                Ui.circle(c, x, y + bob, r, col);
                Ui.circle(c, x, y - r * 0.15f + bob, r * 0.45f,
                        Ui.mix(col, 0xFF000000, 0.45f));
                break;
            }
        }

        if (slowTime > 0) {
            Ui.ring(c, x, y + bob, r * 1.25f, 0x8866E0FF, Ui.dp(1.5f));
        }

        // health bar
        if (hp < maxHp) {
            float bw = Math.max(r * 2f, cs * 0.5f);
            float bh = Math.max(Ui.dp(3f), cs * 0.045f);
            float by = y - r - bh * 2.2f;
            Ui.rect(c, x - bw / 2, by, x + bw / 2, by + bh, bh * 0.5f, 0xCC101820);
            float f = Math.max(0f, hp / maxHp);
            int hc = f > 0.5f ? Ui.GREEN : (f > 0.25f ? Ui.GOLD : Ui.RED);
            Ui.rect(c, x - bw / 2 + 1, by + 1, x - bw / 2 + 1 + (bw - 2) * f, by + bh - 1,
                    bh * 0.5f, hc);
        }
    }
}
