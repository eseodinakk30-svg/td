package com.resonance.td;

/** Враг. Координаты — в клетках (float), движение по полю направлений. */
public class Enemy {

    public int type;
    public float x, y;
    public float hp, maxHp;
    public float speed;
    public int bounty, mass;
    public boolean armored;
    public boolean alive = true;
    /** Фантом: >0 — сейчас вне фазы, неуязвим. */
    public float phaseOut;
    public float hitFlash;
    public float wobble;
    public float jx, jy;
    /** Фантом: чётность полутакта, на котором он уходит из фазы. */
    public int parity;
    /** Куда идём сейчас (центр клетки + смещение). */
    public float tx, ty;
    public boolean reachedCore;

    public void init(int type, float x, float y, float hpScale, float rnd1, float rnd2) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.maxHp = Config.E_HP[type] * hpScale;
        this.hp = maxHp;
        this.speed = Config.E_SPEED[type];
        this.bounty = Config.E_BOUNTY[type];
        this.mass = Config.E_MASS[type];
        this.armored = (type == Config.E_ARMOR);
        this.alive = true;
        this.phaseOut = 0f;
        this.hitFlash = 0f;
        this.reachedCore = false;
        this.wobble = rnd1 * 6.2831855f;
        this.parity = rnd2 > 0.5f ? 1 : 0;
        this.jx = (rnd1 - 0.5f) * 0.36f;
        this.jy = (rnd2 - 0.5f) * 0.36f;
        this.tx = x;
        this.ty = y;
    }

    public boolean vulnerable() {
        return phaseOut <= 0f;
    }
}
