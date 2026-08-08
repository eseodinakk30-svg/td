package android.graphics;

/** Shared validation used by the headless stubs. */
public final class Harnesses {
    public static long drawCalls;

    private Harnesses() { }

    public static void check(float v, String what) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            throw new IllegalStateException("non-finite " + what + ": " + v);
        }
    }
}
