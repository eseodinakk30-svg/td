package com.claude.td;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted progress: unlocked maps and the best wave reached on each. */
public class Save {

    private final SharedPreferences prefs;

    public Save(Context ctx) {
        prefs = ctx.getSharedPreferences("towerdefense", Context.MODE_PRIVATE);
    }

    public int unlocked() {
        return Math.max(1, Math.min(Level.MAP_COUNT, prefs.getInt("unlocked", 1)));
    }

    public void unlock(int count) {
        if (count > unlocked()) prefs.edit().putInt("unlocked", count).apply();
    }

    public int best(int map) {
        return prefs.getInt("best" + map, 0);
    }

    public void reportWave(int map, int wave) {
        if (wave > best(map)) prefs.edit().putInt("best" + map, wave).apply();
    }

    public boolean cleared(int map) {
        return best(map) >= Level.WAVES;
    }
}
