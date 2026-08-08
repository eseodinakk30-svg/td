package com.resonance.td;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Мелкая графическая утварь. Вся картинка векторная, поэтому «толстая линия»
 * рисуется прямоугольниками: glLineWidth на GLES обычно игнорируется.
 */
public final class Draw {

    private Draw() {
    }

    private static final Color TMP = new Color();

    public static void ring(ShapeRenderer sr, float cx, float cy, float r, float w) {
        if (r <= 0.5f) return;
        int seg = (int) Math.max(18, Math.min(84, r * 0.55f));
        float step = 6.2831855f / seg;
        float px = cx + r, py = cy;
        for (int i = 1; i <= seg; i++) {
            float a = step * i;
            float nx = cx + (float) Math.cos(a) * r;
            float ny = cy + (float) Math.sin(a) * r;
            sr.rectLine(px, py, nx, ny, w);
            px = nx;
            py = ny;
        }
    }

    /** Кольцо со свечением: широкий тусклый след + яркая сердцевина. */
    public static void glowRing(ShapeRenderer sr, float cx, float cy, float r, float w, Color c, float a) {
        sr.setColor(TMP.set(c.r, c.g, c.b, a * 0.20f));
        ring(sr, cx, cy, r, w * 4.5f);
        sr.setColor(TMP.set(c.r, c.g, c.b, a * 0.45f));
        ring(sr, cx, cy, r, w * 2f);
        sr.setColor(TMP.set(c.r, c.g, c.b, a));
        ring(sr, cx, cy, r, w);
    }

    public static void disc(ShapeRenderer sr, float cx, float cy, float r) {
        sr.circle(cx, cy, r, (int) Math.max(10, Math.min(48, r * 0.9f)));
    }

    /** Многоугольник (для башен и врагов). */
    public static void poly(ShapeRenderer sr, float cx, float cy, float r, int sides, float rot, float w) {
        float step = 6.2831855f / sides;
        float px = cx + (float) Math.cos(rot) * r;
        float py = cy + (float) Math.sin(rot) * r;
        for (int i = 1; i <= sides; i++) {
            float a = rot + step * i;
            float nx = cx + (float) Math.cos(a) * r;
            float ny = cy + (float) Math.sin(a) * r;
            sr.rectLine(px, py, nx, ny, w);
            px = nx;
            py = ny;
        }
    }

    public static void polyFill(ShapeRenderer sr, float cx, float cy, float r, int sides, float rot) {
        float step = 6.2831855f / sides;
        for (int i = 0; i < sides; i++) {
            float a0 = rot + step * i, a1 = rot + step * (i + 1);
            sr.triangle(cx, cy,
                    cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r);
        }
    }

    /** Звёздочка-вспышка. */
    public static void spark(ShapeRenderer sr, float cx, float cy, float r, float w, int rays, float rot) {
        for (int i = 0; i < rays; i++) {
            float a = rot + i * 3.1415927f / rays;
            float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
            sr.rectLine(cx - dx, cy - dy, cx + dx, cy + dy, w);
        }
    }

    /** Прямоугольник со срезанными углами — «панель». */
    public static void panel(ShapeRenderer sr, float x, float y, float w, float h, float cut) {
        sr.triangle(x + cut, y, x + w - cut, y, x + w - cut, y + h);
        sr.triangle(x + cut, y, x + w - cut, y + h, x + cut, y + h);
        sr.triangle(x, y + cut, x + cut, y, x + cut, y + h);
        sr.triangle(x, y + cut, x + cut, y + h, x, y + h - cut);
        sr.triangle(x + w, y + h - cut, x + w - cut, y + h, x + w - cut, y);
        sr.triangle(x + w, y + h - cut, x + w - cut, y, x + w, y + cut);
    }

    public static void panelOutline(ShapeRenderer sr, float x, float y, float w, float h, float cut, float lw) {
        sr.rectLine(x + cut, y, x + w - cut, y, lw);
        sr.rectLine(x + w - cut, y, x + w, y + cut, lw);
        sr.rectLine(x + w, y + cut, x + w, y + h - cut, lw);
        sr.rectLine(x + w, y + h - cut, x + w - cut, y + h, lw);
        sr.rectLine(x + w - cut, y + h, x + cut, y + h, lw);
        sr.rectLine(x + cut, y + h, x, y + h - cut, lw);
        sr.rectLine(x, y + h - cut, x, y + cut, lw);
        sr.rectLine(x, y + cut, x + cut, y, lw);
    }
}
