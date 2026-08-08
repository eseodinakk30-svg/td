package com.claude.td;

import android.graphics.Canvas;

/** A shot travelling towards a creep. */
public class Projectile {

    public float x, y;
    public float px, py;
    public Enemy target;
    public float lastTx, lastTy;
    public float damage;
    public float speed = 600;
    public float radius = 6;
    public float splash = 0;
    public float slowFactor = 1f;
    public float slowDuration = 0f;
    public boolean elongated = false;
    public boolean trail = false;
    public boolean alive = true;
    public int color;
    public float life = 3f;

    public Projectile(float x, float y, Enemy target, float damage, int color) {
        this.x = x;
        this.y = y;
        this.px = x;
        this.py = y;
        this.target = target;
        this.lastTx = target.x;
        this.lastTy = target.y;
        this.damage = damage;
        this.color = color;
    }

    public void update(float dt, Game g) {
        life -= dt;
        if (life <= 0) { alive = false; return; }

        if (target != null && target.alive) {
            lastTx = target.x;
            lastTy = target.y;
        }
        float dx = lastTx - x, dy = lastTy - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float step = speed * dt;

        px = x;
        py = y;

        if (d <= step || d < 1e-3f) {
            x = lastTx;
            y = lastTy;
            impact(g);
            return;
        }
        x += dx / d * step;
        y += dy / d * step;

        if (trail && Math.random() < 0.5) {
            g.fx.spark(x, y, color, 0.25f);
        }
    }

    private void impact(Game g) {
        alive = false;
        if (splash > 0) {
            g.fx.ring(x, y, splash, color);
            float s2 = splash * splash;
            for (int i = 0; i < g.enemies.size(); i++) {
                Enemy e = g.enemies.get(i);
                if (!e.alive) continue;
                float dx = e.x - x, dy = e.y - y;
                float d2 = dx * dx + dy * dy;
                if (d2 > s2) continue;
                float falloff = 1f - 0.45f * (float) Math.sqrt(d2) / splash;
                g.dealDamage(e, damage * falloff, false);
                if (slowDuration > 0) e.applySlow(slowFactor, slowDuration);
            }
        } else if (target != null && target.alive) {
            g.dealDamage(target, damage, false);
            if (slowDuration > 0) target.applySlow(slowFactor, slowDuration);
        }
        g.fx.burst(x, y, color, splash > 0 ? 10 : 4);
    }

    public void draw(Canvas c) {
        if (elongated) {
            Ui.line(c, px, py, x, y, color, radius * 2f);
            Ui.circle(c, x, y, radius, Ui.mix(color, 0xFFFFFFFF, 0.5f));
        } else {
            Ui.circle(c, x, y, radius * 1.7f, Ui.alpha(color, 0.28f));
            Ui.circle(c, x, y, radius, color);
            Ui.circle(c, x - radius * 0.25f, y - radius * 0.25f, radius * 0.45f,
                    Ui.mix(color, 0xFFFFFFFF, 0.6f));
        }
    }
}
