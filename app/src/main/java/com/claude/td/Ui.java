package com.claude.td;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Palette, shared paints and small drawing helpers used by the whole game. */
public final class Ui {

    public static final int BG          = 0xFF0E1218;
    public static final int GRASS_A     = 0xFF1A2430;
    public static final int GRASS_B     = 0xFF16202B;
    public static final int GRID_LINE   = 0x14FFFFFF;
    public static final int ROAD        = 0xFF4B4230;
    public static final int ROAD_EDGE   = 0xFF615539;
    public static final int ROAD_DASH   = 0x66FFE0A8;

    public static final int PANEL       = 0xF2141B25;
    public static final int PANEL_EDGE  = 0xFF2B3849;
    public static final int TEXT        = 0xFFE8EEF6;
    public static final int TEXT_DIM    = 0xFF8FA0B5;

    public static final int ACCENT      = 0xFF3FD0FF;
    public static final int GOLD        = 0xFFFFC857;
    public static final int RED         = 0xFFFF5C6E;
    public static final int GREEN       = 0xFF5CE08A;
    public static final int ORANGE      = 0xFFFF9F45;
    public static final int PURPLE      = 0xFFB07CFF;

    /** Density scale: 1 unit == 1dp. Set once from the view. */
    public static float DP = 3f;

    public static float dp(float v) { return v * DP; }

    public static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint T = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    static {
        T.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        T.setTextAlign(Paint.Align.CENTER);
    }

    private Ui() { }

    public static void rect(Canvas c, float l, float t, float r, float b, float rad, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        R.set(l, t, r, b);
        c.drawRoundRect(R, rad, rad, P);
    }

    public static void rectGrad(Canvas c, float l, float t, float r, float b, float rad,
                                int top, int bottom) {
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(l, t, l, b, top, bottom, Shader.TileMode.CLAMP));
        R.set(l, t, r, b);
        c.drawRoundRect(R, rad, rad, P);
        P.setShader(null);
    }

    public static void stroke(Canvas c, float l, float t, float r, float b, float rad,
                              int color, float w) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(color);
        R.set(l, t, r, b);
        c.drawRoundRect(R, rad, rad, P);
        P.setStyle(Paint.Style.FILL);
    }

    public static void circle(Canvas c, float x, float y, float r, int color) {
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(color);
        c.drawCircle(x, y, r, P);
    }

    public static void ring(Canvas c, float x, float y, float r, int color, float w) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setColor(color);
        c.drawCircle(x, y, r, P);
        P.setStyle(Paint.Style.FILL);
    }

    public static void line(Canvas c, float x1, float y1, float x2, float y2, int color, float w) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeWidth(w);
        P.setColor(color);
        c.drawLine(x1, y1, x2, y2, P);
        P.setStyle(Paint.Style.FILL);
    }

    public static void path(Canvas c, Path p, int color, float w, boolean round) {
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setStrokeCap(round ? Paint.Cap.ROUND : Paint.Cap.BUTT);
        P.setStrokeJoin(Paint.Join.ROUND);
        P.setColor(color);
        c.drawPath(p, P);
        P.setStyle(Paint.Style.FILL);
    }

    /** Centred text; y is the vertical centre of the glyphs. */
    public static void text(Canvas c, String s, float x, float y, float size, int color) {
        T.setTextAlign(Paint.Align.CENTER);
        T.setTextSize(size);
        T.setColor(color);
        Paint.FontMetrics fm = T.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) * 0.5f, T);
    }

    public static void textLeft(Canvas c, String s, float x, float y, float size, int color) {
        T.setTextAlign(Paint.Align.LEFT);
        T.setTextSize(size);
        T.setColor(color);
        Paint.FontMetrics fm = T.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) * 0.5f, T);
        T.setTextAlign(Paint.Align.CENTER);
    }

    public static void textRight(Canvas c, String s, float x, float y, float size, int color) {
        T.setTextAlign(Paint.Align.RIGHT);
        T.setTextSize(size);
        T.setColor(color);
        Paint.FontMetrics fm = T.getFontMetrics();
        c.drawText(s, x, y - (fm.ascent + fm.descent) * 0.5f, T);
        T.setTextAlign(Paint.Align.CENTER);
    }

    public static float measure(String s, float size) {
        T.setTextSize(size);
        return T.measureText(s);
    }

    public static int alpha(int color, float a) {
        int al = (int) (((color >>> 24) & 0xFF) * a);
        if (al < 0) al = 0;
        if (al > 255) al = 255;
        return (al << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /** A tappable rectangle with a label. */
    public static class Btn {
        public float x, y, w, h;
        public String label = "";
        public boolean enabled = true;
        public boolean visible = true;
        public float press = 0f;

        public void set(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        public boolean hit(float px, float py) {
            return visible && enabled
                    && px >= x && px <= x + w && py >= y && py <= y + h;
        }

        public float cx() { return x + w * 0.5f; }
        public float cy() { return y + h * 0.5f; }

        public void draw(Canvas c, int fill, int textColor, float textSize) {
            if (!visible) return;
            float k = press * dp(2);
            float a = enabled ? 1f : 0.35f;
            rect(c, x, y + k, x + w, y + h, dp(10), alpha(fill, a));
            stroke(c, x, y + k, x + w, y + h, dp(10),
                    alpha(mix(fill, 0xFFFFFFFF, 0.35f), a * 0.8f), dp(1.4f));
            text(c, label, cx(), cy() + k, textSize, alpha(textColor, a));
        }
    }
}
