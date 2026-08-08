package com.claude.td;

import android.graphics.Canvas;

import java.util.ArrayList;

/** Lightweight particles, rings, beams and floating labels. */
public class Fx {

    private static final int SPARK = 0;
    private static final int RING = 1;
    private static final int TEXT = 2;
    private static final int BEAM = 3;
    private static final int FLASH = 4;

    private static class Item {
        int kind;
        float x, y, x2, y2, vx, vy, r, r2, size;
        float life, maxLife;
        int color;
        String label;
    }

    private final ArrayList<Item> items = new ArrayList<Item>();

    public void clear() { items.clear(); }

    public int size() { return items.size(); }

    private Item add(int kind, int color, float life) {
        Item i = new Item();
        i.kind = kind;
        i.color = color;
        i.life = life;
        i.maxLife = life;
        items.add(i);
        return i;
    }

    public void spark(float x, float y, int color, float life) {
        if (items.size() > 400) return;
        Item i = add(SPARK, color, life);
        i.x = x; i.y = y;
        i.vx = (float) (Math.random() - 0.5) * 40;
        i.vy = (float) (Math.random() - 0.5) * 40;
        i.size = Ui.dp(2f) + (float) Math.random() * Ui.dp(2f);
    }

    public void burst(float x, float y, int color, int count) {
        if (items.size() > 380) count = Math.min(count, 3);
        for (int n = 0; n < count; n++) {
            Item i = add(SPARK, color, 0.3f + (float) Math.random() * 0.35f);
            double a = Math.random() * Math.PI * 2;
            float sp = Ui.dp(60) + (float) Math.random() * Ui.dp(140);
            i.x = x; i.y = y;
            i.vx = (float) Math.cos(a) * sp;
            i.vy = (float) Math.sin(a) * sp;
            i.size = Ui.dp(1.6f) + (float) Math.random() * Ui.dp(2.4f);
        }
    }

    public void ring(float x, float y, float r, int color) {
        Item i = add(RING, color, 0.34f);
        i.x = x; i.y = y; i.r = r * 0.25f; i.r2 = r;
    }

    public void flash(float x, float y, float r, int color) {
        Item i = add(FLASH, color, 0.14f);
        i.x = x; i.y = y; i.r = r;
    }

    public void beam(float x1, float y1, float x2, float y2, int color) {
        Item i = add(BEAM, color, 0.18f);
        i.x = x1; i.y = y1; i.x2 = x2; i.y2 = y2;
    }

    public void label(float x, float y, String s, int color, float size) {
        Item i = add(TEXT, color, 0.9f);
        i.x = x; i.y = y; i.label = s; i.size = size;
        i.vy = -Ui.dp(38);
    }

    public void update(float dt) {
        for (int n = items.size() - 1; n >= 0; n--) {
            Item i = items.get(n);
            i.life -= dt;
            if (i.life <= 0) {
                items.remove(n);
                continue;
            }
            switch (i.kind) {
                case SPARK:
                    i.x += i.vx * dt;
                    i.y += i.vy * dt;
                    i.vy += Ui.dp(180) * dt;
                    i.vx *= 0.94f;
                    break;
                case TEXT:
                    i.y += i.vy * dt;
                    i.vy *= 0.93f;
                    break;
                case RING:
                    i.r += (i.r2 - i.r) * dt * 12f;
                    break;
                default:
                    break;
            }
        }
    }

    public void draw(Canvas c) {
        for (int n = 0; n < items.size(); n++) {
            Item i = items.get(n);
            float t = i.life / i.maxLife;
            switch (i.kind) {
                case SPARK:
                    Ui.circle(c, i.x, i.y, i.size * t, Ui.alpha(i.color, t));
                    break;
                case RING:
                    Ui.ring(c, i.x, i.y, i.r, Ui.alpha(i.color, t * 0.85f), Ui.dp(3) * t + 1);
                    break;
                case FLASH:
                    Ui.circle(c, i.x, i.y, i.r * (0.6f + t * 0.6f),
                            Ui.alpha(Ui.mix(i.color, 0xFFFFFFFF, 0.6f), t * 0.8f));
                    break;
                case BEAM: {
                    Ui.line(c, i.x, i.y, i.x2, i.y2, Ui.alpha(i.color, t * 0.5f), Ui.dp(7) * t);
                    Ui.line(c, i.x, i.y, i.x2, i.y2,
                            Ui.alpha(Ui.mix(i.color, 0xFFFFFFFF, 0.7f), t), Ui.dp(2.4f));
                    break;
                }
                case TEXT:
                    Ui.text(c, i.label, i.x, i.y, i.size, Ui.alpha(i.color, Math.min(1f, t * 1.6f)));
                    break;
                default:
                    break;
            }
        }
    }
}
