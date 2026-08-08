package com.resonance.td;

/** Расширяющийся фронт от башни. Урона не наносит — только пересечения. */
public class Pulse {
    public float x, y;
    public float r;
    public float maxR;
    public float speed;
    public float energy;
    public int type;
    public int ownerId;
    public boolean alive;

    public void init(Tower t, int ownerId) {
        this.x = t.gx + 0.5f;
        this.y = t.gy + 0.5f;
        this.r = 0f;
        this.maxR = t.radius;
        this.speed = t.radius / t.travel;
        this.energy = t.energy;
        this.type = t.type;
        this.ownerId = ownerId;
        this.alive = true;
    }
}
