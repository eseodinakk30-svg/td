// Прогон симуляции без графики: проверяем, что мир не падает, путь не рвётся,
// числа не улетают в NaN, и заодно смотрим на баланс.
// Запуск: см. tools/simtest.sh

import com.resonance.td.Config;
import com.resonance.td.Tower;
import com.resonance.td.World;

import java.util.ArrayList;
import java.util.List;

public class SimTest {

    static final float DT = 1f / 60f;

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        for (int s = 0; s < seeds; s++) run(s);
    }

    static void run(int variant) {
        World w = new World();
        List<int[]> spots = new ArrayList<int[]>();
        if (variant == 0) {
            for (int i = 0; i < SMART.length; i++) spots.add(SMART[i]);
        }
        spots.addAll(candidates());

        System.out.println("=== прогон " + variant + " ===");
        System.out.printf("%-6s %-10s %-8s %-8s %-7s %-7s %-6s %-7s%n",
                "волна", "состав", "энергия", "прочн.", "убито", "утечки", "башен", "сеть");

        int lastWave = 0;
        float t = 0f;
        int guard = 0;
        int spotIdx = 0;
        int typeCycle = variant;

        while (w.state != World.ST_LOST && w.state != World.ST_WON && guard++ < 60 * 60 * 40) {
            w.update(DT);
            t += DT;

            // «игрок»: в паузе тратит энергию на решётку
            if (w.state == World.ST_PREP) {
                for (int tries = 0; tries < 40 && spotIdx < spots.size(); tries++) {
                    int[] p = spots.get(spotIdx);
                    int type = p[2] >= 0 ? p[2] : pickType(typeCycle);
                    if (w.energy < Config.T_COST[type] + 10) break;
                    if (w.powerUsed() + Config.T_POWER[type] > w.powerCap()) break;
                    if (w.build(type, p[0], p[1]) != null) {
                        spotIdx++;
                        typeCycle++;
                    } else {
                        spotIdx++;
                    }
                }
                // лишнюю энергию — в генераторы и улучшения
                while (w.energy > w.genCost() * 2 && w.gensBought < 14) w.buyGenerator();
                for (int i = 0; i < w.towers.size && w.energy > 160; i++) {
                    w.upgrade(w.towers.get(i));
                }
                if (w.energy > 400) w.callWaveNow();
            }

            if (w.wave != lastWave) {
                lastWave = w.wave;
                System.out.printf("%-6d %-10s %-8d %-8d %-7d %-7d %-6d %d/%d%n",
                        w.wave, com.resonance.td.Waves.label(w.wave), w.energy, w.integrity,
                        w.killed, w.leaked, w.towers.size, w.powerUsed(), w.powerCap());
            }
            check(w, t);
        }

        String verdict = w.state == World.ST_WON ? "ПОБЕДА" : (w.state == World.ST_LOST ? "ПОРАЖЕНИЕ" : "ТАЙМАУТ");
        System.out.printf("итог: %s | волна %d | прочность %d | убито %d | утечек %d | башен %d | %.0f сек игры%n%n",
                verdict, w.wave, w.integrity, w.killed, w.leaked, w.towers.size, t);
    }

    static int pickType(int i) {
        int m = Math.abs(i) % 5;
        if (m == 0 || m == 2) return Config.T_PULSAR;
        if (m == 1 || m == 3) return Config.T_PHASER;
        return Config.T_RESONATOR;
    }

    /**
     * «Осмысленный» порядок стройки: пары башен по разные стороны коридора,
     * чтобы линия резонанса шла вдоль маршрута, а не поперёк.
     */
    static final int[][] SMART = {
            {4, 3, Config.T_PULSAR}, {6, 3, Config.T_PHASER},
            {4, 5, Config.T_PULSAR}, {6, 5, Config.T_PHASER},
            {3, 9, Config.T_PULSAR}, {7, 9, Config.T_PHASER},
            {4, 12, Config.T_PULSAR}, {6, 12, Config.T_PHASER},
            {3, 5, Config.T_PULSAR}, {7, 5, Config.T_PHASER},
            {4, 9, Config.T_RESONATOR}, {6, 9, Config.T_RESONATOR},
            {2, 3, Config.T_PULSAR}, {8, 3, Config.T_PHASER},
            {3, 12, Config.T_RESONATOR}, {7, 12, Config.T_RESONATOR},
    };

    /** Клетки вокруг ядра, от ближних к дальним. */
    static List<int[]> candidates() {
        List<int[]> all = new ArrayList<int[]>();
        for (int y = 0; y < Config.ROWS; y++) {
            for (int x = 0; x < Config.COLS; x++) {
                int dx = x - Config.CORE_X, dy = y - Config.CORE_Y;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < 1.4 || d > 8.5) continue;
                all.add(new int[]{x, y, (int) (d * 100)});
            }
        }
        java.util.Collections.sort(all, new java.util.Comparator<int[]>() {
            public int compare(int[] a, int[] b) {
                return a[2] - b[2];
            }
        });
        List<int[]> out = new ArrayList<int[]>();
        for (int[] a : all) out.add(new int[]{a[0], a[1], -1});
        return out;
    }

    static void check(World w, float t) {
        if (Float.isNaN(w.dpsMeter)) fail("NaN в dpsMeter", t);
        for (int i = 0; i < w.enemies.size; i++) {
            com.resonance.td.Enemy e = w.enemies.get(i);
            if (Float.isNaN(e.x) || Float.isNaN(e.y)) fail("NaN в координатах врага", t);
            if (e.x < -2 || e.y < -2 || e.x > Config.COLS + 2 || e.y > Config.ROWS + 2)
                fail("враг вне поля: " + e.x + "," + e.y, t);
        }
        if (w.nodes.size > World.MAX_NODES) fail("переполнение узлов", t);
        if (w.energy < 0) fail("энергия ушла в минус", t);
    }

    static void fail(String msg, float t) {
        throw new IllegalStateException("ОШИБКА на " + t + " сек: " + msg);
    }
}
