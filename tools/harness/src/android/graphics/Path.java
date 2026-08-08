package android.graphics;

import java.awt.geom.Path2D;

public class Path {
    public final Path2D.Float p = new Path2D.Float();
    private boolean started;

    public void reset() { p.reset(); started = false; }

    public void moveTo(float x, float y) {
        Harnesses.check(x, "moveTo.x");
        Harnesses.check(y, "moveTo.y");
        p.moveTo(x, y);
        started = true;
    }

    public void lineTo(float x, float y) {
        Harnesses.check(x, "lineTo.x");
        Harnesses.check(y, "lineTo.y");
        if (!started) { p.moveTo(x, y); started = true; } else { p.lineTo(x, y); }
    }

    public void close() { if (started) p.closePath(); }
}
