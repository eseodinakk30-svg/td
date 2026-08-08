package android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;

/**
 * Stub Canvas with two modes. With no Graphics2D attached it only validates the
 * geometry (fast, used by the headless play-through). Attach one and it renders
 * the very same draw calls with Java2D so the layout can be eyeballed.
 */
public class Canvas {

    private Graphics2D g2;
    private final ArrayDeque<AffineTransform> stack = new ArrayDeque<AffineTransform>();
    private int depth;

    private static final Graphics2D MEASURE = measureContext();
    private static final java.awt.geom.RoundRectangle2D.Float RR =
            new java.awt.geom.RoundRectangle2D.Float();
    private static final Ellipse2D.Float EL = new Ellipse2D.Float();
    private static final Line2D.Float LN = new Line2D.Float();

    public Canvas() { }

    public Canvas(Graphics2D g) {
        attach(g);
    }

    public void attach(Graphics2D g) {
        this.g2 = g;
        if (g != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    private static Graphics2D measureContext() {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    static float measure(String s, Paint p) {
        MEASURE.setFont(font(p));
        return MEASURE.getFontMetrics().stringWidth(s);
    }

    private static Font font(Paint p) {
        return new Font(Font.SANS_SERIF, Font.BOLD, Math.max(1, Math.round(p.textSize)));
    }

    private void apply(Paint p) {
        if (p.shader != null) {
            g2.setPaint(new GradientPaint(p.shader.x0, p.shader.y0, new Color(p.shader.c0, true),
                    p.shader.x1, p.shader.y1, new Color(p.shader.c1, true)));
        } else {
            g2.setPaint(new Color(p.color, true));
        }
        if (p.style == Paint.Style.STROKE) {
            int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND
                    : (p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT);
            int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND
                    : (p.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER);
            g2.setStroke(new BasicStroke(Math.max(0.1f, p.strokeWidth), cap, join));
        }
    }

    private void paint(java.awt.Shape s, Paint p) {
        apply(p);
        if (p.style == Paint.Style.STROKE) g2.draw(s); else g2.fill(s);
    }

    // ------------------------------------------------------------------ draws

    public void drawColor(int c) {
        Harnesses.drawCalls++;
        if (g2 == null) return;
        g2.setPaint(new Color(c, true));
        g2.fillRect(-4000, -4000, 12000, 12000);
    }

    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        Harnesses.check(r.left, "rect.left");
        Harnesses.check(r.top, "rect.top");
        Harnesses.check(r.right, "rect.right");
        Harnesses.check(r.bottom, "rect.bottom");
        Harnesses.check(rx, "rx");
        Harnesses.drawCalls++;
        if (g2 == null) return;
        RR.setRoundRect(Math.min(r.left, r.right), Math.min(r.top, r.bottom),
                Math.abs(r.right - r.left), Math.abs(r.bottom - r.top), rx * 2, ry * 2);
        paint(RR, p);
    }

    public void drawCircle(float x, float y, float r, Paint p) {
        Harnesses.check(x, "cx");
        Harnesses.check(y, "cy");
        Harnesses.check(r, "radius");
        Harnesses.drawCalls++;
        if (g2 == null) return;
        EL.setFrame(x - r, y - r, r * 2, r * 2);
        paint(EL, p);
    }

    public void drawLine(float x1, float y1, float x2, float y2, Paint p) {
        Harnesses.check(x1, "x1");
        Harnesses.check(y1, "y1");
        Harnesses.check(x2, "x2");
        Harnesses.check(y2, "y2");
        Harnesses.drawCalls++;
        if (g2 == null) return;
        LN.setLine(x1, y1, x2, y2);
        Paint.Style s = p.style;
        p.style = Paint.Style.STROKE;
        paint(LN, p);
        p.style = s;
    }

    public void drawPath(Path path, Paint p) {
        Harnesses.drawCalls++;
        if (g2 == null) return;
        paint(path.p, p);
    }

    public void drawText(String s, float x, float y, Paint p) {
        if (s == null) throw new IllegalStateException("null text");
        Harnesses.check(x, "text.x");
        Harnesses.check(y, "text.y");
        Harnesses.drawCalls++;
        if (g2 == null) return;
        g2.setFont(font(p));
        float w = g2.getFontMetrics().stringWidth(s);
        float ox = p.align == Paint.Align.CENTER ? -w / 2f
                : (p.align == Paint.Align.RIGHT ? -w : 0);
        g2.setPaint(new Color(p.color, true));
        g2.drawString(s, x + ox, y);
    }

    // ------------------------------------------------------------------ state

    public int save() {
        if (g2 != null) stack.push(g2.getTransform());
        return ++depth;
    }

    public void restoreToCount(int c) {
        while (depth >= c) {
            depth--;
            if (g2 != null && !stack.isEmpty()) g2.setTransform(stack.pop());
        }
    }

    public void translate(float x, float y) {
        Harnesses.check(x, "tx");
        Harnesses.check(y, "ty");
        if (g2 != null) g2.translate(x, y);
    }
}
