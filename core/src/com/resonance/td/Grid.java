package com.resonance.td;

/**
 * Клеточное поле и волновой алгоритм (BFS) от ядра.
 * Пути нет — есть поле направлений: враги всегда «стекают» к ядру,
 * поэтому игрок лепит лабиринт, а не ставит башни вдоль готовой дорожки.
 */
public class Grid {

    public final int cols, rows;
    /** Клетка занята постройкой. */
    public final boolean[] blocked;
    /** Расстояние до ядра в шагах; -1 — недостижимо. */
    public final int[] dist;
    /** Индекс следующей клетки на пути к ядру; -1 — нет. */
    public final int[] flow;

    private final int[] queue;

    public Grid(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        int n = cols * rows;
        blocked = new boolean[n];
        dist = new int[n];
        flow = new int[n];
        queue = new int[n];
        rebuild();
    }

    public int idx(int x, int y) {
        return y * cols + x;
    }

    public boolean in(int x, int y) {
        return x >= 0 && y >= 0 && x < cols && y < rows;
    }

    public boolean isBlocked(int x, int y) {
        return !in(x, y) || blocked[idx(x, y)];
    }

    /** Пересчитать волну от ядра. */
    public void rebuild() {
        int n = cols * rows;
        for (int i = 0; i < n; i++) {
            dist[i] = -1;
            flow[i] = -1;
        }
        int core = idx(Config.CORE_X, Config.CORE_Y);
        int head = 0, tail = 0;
        dist[core] = 0;
        queue[tail++] = core;

        while (head < tail) {
            int cur = queue[head++];
            int cx = cur % cols, cy = cur / cols;
            int d = dist[cur] + 1;
            for (int k = 0; k < 4; k++) {
                int nx = cx + DX[k], ny = cy + DY[k];
                if (!in(nx, ny)) continue;
                int ni = idx(nx, ny);
                if (blocked[ni] || dist[ni] >= 0) continue;
                dist[ni] = d;
                queue[tail++] = ni;
            }
        }

        // направление = сосед с минимальной дистанцией
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int i = idx(x, y);
                if (dist[i] <= 0) continue;
                int best = -1, bestD = Integer.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    int nx = x + DX[k], ny = y + DY[k];
                    if (!in(nx, ny)) continue;
                    int ni = idx(nx, ny);
                    if (dist[ni] < 0) continue;
                    if (dist[ni] < bestD) {
                        bestD = dist[ni];
                        best = ni;
                    }
                }
                flow[i] = best;
            }
        }
    }

    public boolean reachable(int x, int y) {
        return in(x, y) && dist[idx(x, y)] >= 0;
    }

    public static final int[] DX = {0, 0, 1, -1};
    public static final int[] DY = {1, -1, 0, 0};
}
