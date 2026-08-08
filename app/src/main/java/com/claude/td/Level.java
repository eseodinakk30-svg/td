package com.claude.td;

import android.graphics.Canvas;
import android.graphics.Path;

/** Map geometry: the creep road, the buildable grid and pixel layout. */
public class Level {

    public static final int COLS = 9;
    public static final int ROWS = 13;

    /** Waypoints in grid coordinates; may start/end outside the grid. */
    private static final int[][][] MAPS = {
            { {4, -1}, {4, 2}, {1, 2}, {1, 6}, {7, 6}, {7, 9}, {3, 9}, {3, 13} },
            { {-1, 2}, {6, 2}, {6, 5}, {2, 5}, {2, 8}, {7, 8}, {7, 11}, {-1, 11} },
            { {4, -1}, {4, 1}, {1, 1}, {1, 4}, {7, 4}, {7, 6}, {2, 6}, {2, 9},
              {6, 9}, {6, 11}, {1, 11}, {1, 13} },
    };

    private static final String[] NAMES = { "GREEN VALLEY", "IRON CANYON", "THE GAUNTLET" };
    private static final float[] DIFFICULTY = { 1.0f, 1.35f, 1.62f };
    private static final int[] START_GOLD = { 240, 220, 210 };
    private static final int[] START_LIVES = { 20, 18, 15 };
    public static final int MAP_COUNT = 3;
    public static final int WAVES = 20;

    public final int index;
    public final String name;
    public final float difficulty;
    public final int startGold;
    public final int startLives;

    private final int[][] wp;
    public final boolean[][] road = new boolean[COLS][ROWS];
    public final boolean[][] rock = new boolean[COLS][ROWS];

    /** Pixel layout. */
    public float ox, oy, cs;

    private final float[] px, py;
    private final float[] segLen;
    public float totalLen;

    private final Path roadPath = new Path();
    private final float[] ta = new float[3];
    private final float[] tb = new float[3];

    public static String nameOf(int i) { return NAMES[i]; }

    public Level(int index) {
        this.index = index;
        this.name = NAMES[index];
        this.difficulty = DIFFICULTY[index];
        this.startGold = START_GOLD[index];
        this.startLives = START_LIVES[index];
        this.wp = MAPS[index];
        px = new float[wp.length];
        py = new float[wp.length];
        segLen = new float[wp.length - 1];
        markRoad();
        scatterRocks();
    }

    private void markRoad() {
        for (int i = 0; i < wp.length - 1; i++) {
            int c0 = wp[i][0], r0 = wp[i][1], c1 = wp[i + 1][0], r1 = wp[i + 1][1];
            int dc = Integer.signum(c1 - c0), dr = Integer.signum(r1 - r0);
            int c = c0, r = r0;
            mark(c, r);
            while (c != c1 || r != r1) {
                c += dc; r += dr;
                mark(c, r);
            }
        }
    }

    private void mark(int c, int r) {
        if (c >= 0 && c < COLS && r >= 0 && r < ROWS) road[c][r] = true;
    }

    /** Deterministic decorative boulders that also block building. */
    private void scatterRocks() {
        long seed = 1337L * (index + 7);
        int want = 4 + index * 3;
        int guard = 0;
        while (want > 0 && guard++ < 500) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int c = (int) ((seed >>> 17) % COLS);
            int r = (int) ((seed >>> 33) % ROWS);
            if (c < 0 || r < 0) continue;
            if (road[c][r] || rock[c][r]) continue;
            if (neighboursRoad(c, r) && want > 1) continue;
            rock[c][r] = true;
            want--;
        }
    }

    private boolean neighboursRoad(int c, int r) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int cc = c + i, rr = r + j;
                if (cc >= 0 && cc < COLS && rr >= 0 && rr < ROWS && road[cc][rr]) return true;
            }
        }
        return false;
    }

    public boolean buildable(int c, int r) {
        return c >= 0 && c < COLS && r >= 0 && r < ROWS && !road[c][r] && !rock[c][r];
    }

    /** Fit the grid into the space between topInset and bottomInset. */
    public void layout(int w, int h, float topInset, float bottomInset) {
        float availW = w;
        float availH = h - topInset - bottomInset;
        cs = Math.min(availW / COLS, availH / ROWS);
        ox = (w - cs * COLS) * 0.5f;
        oy = topInset + (availH - cs * ROWS) * 0.5f;

        for (int i = 0; i < wp.length; i++) {
            px[i] = cellX(wp[i][0]);
            py[i] = cellY(wp[i][1]);
        }
        totalLen = 0;
        for (int i = 0; i < segLen.length; i++) {
            float dx = px[i + 1] - px[i], dy = py[i + 1] - py[i];
            segLen[i] = (float) Math.sqrt(dx * dx + dy * dy);
            totalLen += segLen[i];
        }
        roadPath.reset();
        roadPath.moveTo(px[0], py[0]);
        for (int i = 1; i < px.length; i++) roadPath.lineTo(px[i], py[i]);
    }

    public float cellX(int c) { return ox + (c + 0.5f) * cs; }
    public float cellY(int r) { return oy + (r + 0.5f) * cs; }

    public int colAt(float x) { return (int) Math.floor((x - ox) / cs); }
    public int rowAt(float y) { return (int) Math.floor((y - oy) / cs); }

    /** World position at a distance along the road. out[0]=x out[1]=y out[2]=angle */
    public void posAt(float d, float[] out) {
        if (d < 0) d = 0;
        float acc = 0;
        for (int i = 0; i < segLen.length; i++) {
            if (acc + segLen[i] >= d || i == segLen.length - 1) {
                float t = segLen[i] <= 0 ? 0 : (d - acc) / segLen[i];
                if (t > 1) t = 1;
                out[0] = px[i] + (px[i + 1] - px[i]) * t;
                out[1] = py[i] + (py[i + 1] - py[i]) * t;
                out[2] = (float) Math.atan2(py[i + 1] - py[i], px[i + 1] - px[i]);
                return;
            }
            acc += segLen[i];
        }
    }

    // ---------------------------------------------------------------- drawing

    public void draw(Canvas c, float time) {
        // ground tiles
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                if (road[i][j]) continue;
                float l = ox + i * cs, t = oy + j * cs;
                int col = ((i + j) & 1) == 0 ? Ui.GRASS_A : Ui.GRASS_B;
                Ui.rect(c, l + 1, t + 1, l + cs - 1, t + cs - 1, Ui.dp(3), col);
            }
        }
        // road: wide dark casing, warm fill, animated dashes
        Ui.path(c, roadPath, Ui.ROAD_EDGE, cs * 0.92f, true);
        Ui.path(c, roadPath, Ui.ROAD, cs * 0.76f, true);

        float dash = cs * 0.34f;
        float gap = cs * 0.34f;
        float off = (time * cs * 0.55f) % (dash + gap);
        float d = -off;
        float[] a = ta;
        float[] b = tb;
        while (d < totalLen) {
            float s = Math.max(0, d);
            float e = Math.min(totalLen, d + dash);
            if (e > s) {
                posAt(s, a);
                posAt(e, b);
                Ui.line(c, a[0], a[1], b[0], b[1], Ui.ROAD_DASH, cs * 0.06f);
            }
            d += dash + gap;
        }

        // rocks
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                if (!rock[i][j]) continue;
                float x = cellX(i), y = cellY(j), rr = cs * 0.3f;
                Ui.circle(c, x, y + cs * 0.06f, rr, 0xFF232E3C);
                Ui.circle(c, x - rr * 0.25f, y - rr * 0.2f, rr * 0.72f, 0xFF34404F);
                Ui.circle(c, x + rr * 0.3f, y + rr * 0.1f, rr * 0.5f, 0xFF2A3543);
            }
        }

        // entrance / exit markers
        float[] p = ta;
        posAt(0, p);
        Ui.ring(c, p[0], p[1], cs * 0.34f, Ui.alpha(Ui.RED, 0.55f), Ui.dp(2));
        posAt(totalLen, p);
        Ui.ring(c, p[0], p[1], cs * 0.34f, Ui.alpha(Ui.GREEN, 0.55f), Ui.dp(2));
    }

    /** Highlight every free cell while the player is choosing where to build. */
    public void drawBuildGrid(Canvas c, int hoverC, int hoverR, boolean affordable) {
        for (int i = 0; i < COLS; i++) {
            for (int j = 0; j < ROWS; j++) {
                if (!buildable(i, j)) continue;
                float l = ox + i * cs, t = oy + j * cs;
                Ui.stroke(c, l + cs * 0.12f, t + cs * 0.12f, l + cs * 0.88f, t + cs * 0.88f,
                        Ui.dp(4), 0x33FFFFFF, Ui.dp(1.2f));
            }
        }
        if (hoverC >= 0) {
            float l = ox + hoverC * cs, t = oy + hoverR * cs;
            int col = affordable && buildable(hoverC, hoverR) ? Ui.GREEN : Ui.RED;
            Ui.rect(c, l + 2, t + 2, l + cs - 2, t + cs - 2, Ui.dp(5), Ui.alpha(col, 0.25f));
            Ui.stroke(c, l + 2, t + 2, l + cs - 2, t + cs - 2, Ui.dp(5), col, Ui.dp(2));
        }
    }
}
