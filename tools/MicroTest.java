// Карта урона: расставляем сетку неподвижных «манекенов» и смотрим,
// куда на самом деле бьёт резонанс. Нужен для настройки баланса.

import com.resonance.td.Config;
import com.resonance.td.Enemy;
import com.resonance.td.World;

public class MicroTest {

    static final float DT = 1f / 60f;
    static final float SECONDS = 8f;
    static final int SUB = 2;                 // манекенов на клетку по каждой оси

    public static void main(String[] args) {
        World w = new World();
        w.energy = 100000;
        w.build(Config.T_PULSAR, 4, 8);
        w.build(Config.T_PHASER, 7, 8);
        heat(w, "ПУЛЬСАР(4,8) + ФАЗЕР(7,8)");

        World w2 = new World();
        w2.energy = 100000;
        w2.build(Config.T_PULSAR, 4, 8);
        w2.build(Config.T_PULSAR, 7, 8);
        heat(w2, "ПУЛЬСАР + ПУЛЬСАР (одна фаза)");

        World w3 = new World();
        w3.energy = 100000;
        w3.build(Config.T_PULSAR, 4, 8);
        w3.build(Config.T_PHASER, 7, 8);
        w3.build(Config.T_RESONATOR, 5, 10);
        heat(w3, "ПУЛЬСАР + ФАЗЕР + РЕЗОНАТОР (гармоники)");
    }

    static void heat(World w, String title) {
        int x0 = 1, x1 = 10, y0 = 4, y1 = 13;
        int nx = (x1 - x0) * SUB, ny = (y1 - y0) * SUB;
        Enemy[] dummies = new Enemy[nx * ny];
        float[] px = new float[nx * ny], py = new float[nx * ny];

        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                float x = x0 + (i + 0.5f) / SUB;
                float y = y0 + (j + 0.5f) / SUB;
                w.spawnAt(Config.E_DRONE, x, y);
                Enemy e = w.enemies.peek();
                e.maxHp = 100000f;
                e.hp = 100000f;
                e.speed = 0f;
                e.jx = 0;
                e.jy = 0;
                dummies[j * nx + i] = e;
                px[j * nx + i] = x;
                py[j * nx + i] = y;
            }
        }

        int maxOrder = 0;
        float sumNodes = 0;
        int steps = 0;
        for (float t = 0; t < SECONDS; t += DT) {
            for (int k = 0; k < dummies.length; k++) {   // держим на месте
                dummies[k].x = px[k];
                dummies[k].y = py[k];
            }
            w.update(DT);
            sumNodes += w.nodes.size;
            for (int i = 0; i < w.nodes.size; i++) maxOrder = Math.max(maxOrder, w.nodes.get(i).order);
            steps++;
        }

        float best = 0f;
        float[] dmg = new float[dummies.length];
        for (int k = 0; k < dummies.length; k++) {
            dmg[k] = (100000f - dummies[k].hp) / SECONDS;
            best = Math.max(best, dmg[k]);
        }

        System.out.println();
        System.out.println("== " + title + " ==");
        System.out.printf("узлов в кадре: %.1f | макс порядок: %d | пик урона: %.1f/сек%n",
                sumNodes / steps, maxOrder, best);
        String ramp = " .:-=+*#%@";
        for (int j = ny - 1; j >= 0; j--) {
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 0; i < nx; i++) {
                float v = best > 0 ? dmg[j * nx + i] / best : 0;
                int c = (int) (v * (ramp.length() - 1) + 0.5f);
                sb.append(ramp.charAt(c)).append(ramp.charAt(c));
            }
            System.out.println(sb);
        }
    }
}
