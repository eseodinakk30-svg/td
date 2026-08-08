package com.resonance.td;

/** Точка пересечения двух фронтов — единственное место, где живёт урон. */
public class ResNode {
    public float x, y;
    public float energy;
    /** Порядок: сколько узлов слиплись в одной точке (2 фронта = 1, 3 фронта = 3). */
    public int order;
    public int typeA, typeB;
}
