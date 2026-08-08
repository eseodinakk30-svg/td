package com.resonance.td;

import com.badlogic.gdx.graphics.Color;

/** Искра. Координаты в клетках. */
public class Particle {
    public float x, y, vx, vy, life, maxLife, size;
    public final Color color = new Color();
    public boolean alive;

    public void init(float x, float y, float vx, float vy, float life, float size, Color c) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.color.set(c);
        this.alive = true;
    }
}
