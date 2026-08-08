package android.graphics;

/**
 * Stub Paint. Holds enough state for the Java2D-backed Canvas to reproduce what
 * the game asks for; on a device this class does not exist, the real one does.
 */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }

    public static class FontMetrics {
        public float top, ascent, descent, bottom, leading;
    }

    public Style style = Style.FILL;
    public Cap cap = Cap.BUTT;
    public Join join = Join.MITER;
    public Align align = Align.LEFT;
    public int color = 0xFF000000;
    public float strokeWidth = 0;
    public float textSize = 12;
    public LinearGradient shader;

    private final FontMetrics fm = new FontMetrics();

    public Paint() { }
    public Paint(int flags) { }

    public void setStyle(Style s) { style = s; }
    public void setColor(int c) { color = c; }
    public void setAlpha(int a) { color = (a << 24) | (color & 0xFFFFFF); }
    public void setStrokeWidth(float w) { Harnesses.check(w, "strokeWidth"); strokeWidth = w; }
    public void setStrokeCap(Cap c) { cap = c; }
    public void setStrokeJoin(Join j) { join = j; }
    public void setShader(Shader s) { shader = (s instanceof LinearGradient) ? (LinearGradient) s : null; }
    public void setTextAlign(Align a) { align = a; }
    public void setTypeface(Typeface t) { }
    public void setTextSize(float s) { Harnesses.check(s, "textSize"); textSize = s; }
    public float getTextSize() { return textSize; }

    public FontMetrics getFontMetrics() {
        fm.ascent = -textSize * 0.8f;
        fm.descent = textSize * 0.2f;
        return fm;
    }

    public float measureText(String s) {
        return Canvas.measure(s, this);
    }
}
