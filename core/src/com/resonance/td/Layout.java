package com.resonance.td;

/** Перевод «клетки поля» ↔ «мировые координаты» и раскладка интерфейса. */
public class Layout {

    public float worldW, worldH;
    public float topBarH, panelH;
    public float cell, originX, originY;

    public void resize(float w, float h) {
        worldW = w;
        worldH = h;
        topBarH = Math.max(96f, h * 0.075f);
        panelH = Math.max(250f, h * 0.215f);

        float availW = w - 24f;
        float availH = h - topBarH - panelH - 24f;
        cell = Math.min(availW / Config.COLS, availH / Config.ROWS);
        originX = (w - cell * Config.COLS) * 0.5f;
        originY = panelH + 12f + (availH - cell * Config.ROWS) * 0.5f;
    }

    public float x(float gx) {
        return originX + gx * cell;
    }

    public float y(float gy) {
        return originY + gy * cell;
    }

    public float r(float cells) {
        return cells * cell;
    }

    public int gridX(float sx) {
        return (int) Math.floor((sx - originX) / cell);
    }

    public int gridY(float sy) {
        return (int) Math.floor((sy - originY) / cell);
    }

    public boolean inField(float sx, float sy) {
        return sx >= originX && sy >= originY
                && sx < originX + cell * Config.COLS
                && sy < originY + cell * Config.ROWS;
    }
}
