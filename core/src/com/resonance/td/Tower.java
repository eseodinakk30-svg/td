package com.resonance.td;

/** Излучатель. Сам по себе не наносит урона — только гонит фронт. */
public class Tower {

    /** Устойчивый идентификатор — по нему импульсы понимают, что они «родня». */
    public int id;
    public int type;
    public int gx, gy;
    public int level = 1;
    /** Сдвиг в полутактах: 0 — «на такт», 1 — «между тактами». */
    public int phase;
    public int invested;

    public int interval;
    public float radius;
    public float travel;
    public float energy;

    /** Для анимации: 0..1 с момента последнего импульса. */
    public float flash;
    public float spin;

    public Tower(int type, int gx, int gy) {
        this.type = type;
        this.gx = gx;
        this.gy = gy;
        this.phase = Config.T_PHASE[type];
        this.invested = Config.T_COST[type];
        recompute();
    }

    public void recompute() {
        interval = Config.T_INTERVAL[type];
        travel = Config.T_TRAVEL[type];
        radius = Config.T_RADIUS[type] + (level - 1) * Config.UPGRADE_RADIUS;
        energy = Config.T_ENERGY[type] + (level - 1) * Config.UPGRADE_ENERGY;
    }

    public boolean emits() {
        return interval > 0;
    }

    /** Сколько мощности сети занимает башня с учётом уровня. */
    public int power() {
        return Config.T_POWER[type] + (level - 1) * Config.UPGRADE_POWER;
    }

    public int upgradeCost() {
        return (int) (Config.T_COST[type] * Config.UPGRADE_COST_K * level);
    }

    public int sellValue() {
        return (int) (invested * Config.SELL_REFUND);
    }

    /** Стреляет ли башня на этом полутакте. */
    public boolean firesAt(int halfBeat) {
        if (!emits()) return false;
        int period = interval * 2;
        int m = (halfBeat - phase) % period;
        if (m < 0) m += period;
        return m == 0;
    }
}
