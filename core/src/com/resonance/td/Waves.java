package com.resonance.td;

import com.badlogic.gdx.utils.Array;

/** Процедурная нарезка волн: состав растёт и меняет характер каждые 5 волн. */
public final class Waves {

    public static class Spawn {
        public float time;
        public int type;
        public int portal;
    }

    private Waves() {
    }

    public static int portalsFor(int wave) {
        if (wave <= 3) return 1;
        if (wave <= 9) return 2;
        return 3;
    }

    public static float hpScale(int wave) {
        int w = wave - 1;
        return 1f + 0.19f * w + 0.085f * w * w;
    }

    /** Короткая подпись состава волны для интерфейса. */
    public static String label(int wave) {
        if (wave % 5 == 0) return "ГАРМОНИКА";
        if (wave >= 10) return "СМЕШАННАЯ";
        if (wave >= 7) return "БРОНЯ";
        if (wave >= 5) return "РОИ";
        if (wave >= 3) return "СКОРОСТЬ";
        return "РАЗВЕДКА";
    }

    public static Array<Spawn> build(int wave, java.util.Random rnd) {
        Array<Spawn> out = new Array<Spawn>();
        int portals = portalsFor(wave);
        int count = (int) (6 + wave * 2.4f);
        float gap = Math.max(0.26f, 0.95f - wave * 0.022f);
        boolean harmonic = (wave % 5 == 0);

        // веса типов
        float[] w = new float[Config.E_COUNT];
        w[Config.E_DRONE] = 1.0f;
        w[Config.E_SWIFT] = wave >= 3 ? 0.55f + 0.03f * wave : 0f;
        w[Config.E_SWARM] = wave >= 5 ? 0.40f + 0.02f * wave : 0f;
        w[Config.E_ARMOR] = wave >= 7 ? 0.28f + 0.02f * wave : 0f;
        w[Config.E_PHANTOM] = wave >= 10 ? 0.30f + 0.02f * wave : 0f;
        w[Config.E_MOTE] = 0f;
        if (harmonic) {
            w[Config.E_ARMOR] = Math.max(w[Config.E_ARMOR], 0.9f) + 0.35f;
            w[Config.E_DRONE] *= 0.4f;
            count = (int) (count * 0.75f);
        }

        float total = 0f;
        for (int i = 0; i < w.length; i++) total += w[i];

        float t = 0f;
        for (int i = 0; i < count; i++) {
            float r = rnd.nextFloat() * total;
            int type = Config.E_DRONE;
            for (int k = 0; k < w.length; k++) {
                r -= w[k];
                if (r <= 0f && w[k] > 0f) {
                    type = k;
                    break;
                }
            }
            Spawn s = new Spawn();
            s.type = type;
            s.portal = rnd.nextInt(portals);
            s.time = t;
            out.add(s);
            t += gap * (0.65f + rnd.nextFloat() * 0.7f);
            // редкие «пачки»
            if (wave >= 6 && rnd.nextFloat() < 0.18f) t += gap * 1.6f;
        }
        return out;
    }
}
