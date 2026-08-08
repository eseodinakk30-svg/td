package com.resonance.td;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Рисует поле. Ничего не решает — только читает мир. */
public class Renderer {

    private final Color tmp = new Color();

    public void draw(ShapeRenderer sr, World w, Layout L, int buildType, int hoverX, int hoverY,
                     Tower selected) {
        normalBlend();
        sr.begin(ShapeRenderer.ShapeType.Filled);

        background(sr, L);
        grid(sr, w, L, buildType);
        rocks(sr, w, L);
        towers(sr, w, L, selected);

        addBlend(sr);
        pulses(sr, w, L);
        nodes(sr, w, L);
        particles(sr, w, L);
        coreGlow(sr, w, L);
        portals(sr, w, L);

        normalBlend(sr);
        core(sr, w, L);
        enemies(sr, w, L);
        selection(sr, w, L, selected);
        preview(sr, w, L, buildType, hoverX, hoverY);

        sr.end();
    }

    // ------------------------------------------------------------------ фон

    private void background(ShapeRenderer sr, Layout L) {
        sr.rect(0, 0, L.worldW, L.worldH,
                Config.BG_BOTTOM, Config.BG_BOTTOM, Config.BG_TOP, Config.BG_TOP);
    }

    private void grid(ShapeRenderer sr, World w, Layout L, int buildType) {
        float c = L.cell;
        sr.setColor(Config.GRID);
        for (int x = 0; x <= Config.COLS; x++) {
            sr.rectLine(L.x(x), L.y(0), L.x(x), L.y(Config.ROWS), 1.6f);
        }
        for (int y = 0; y <= Config.ROWS; y++) {
            sr.rectLine(L.x(0), L.y(y), L.x(Config.COLS), L.y(y), 1.6f);
        }

        // подсветка свободных клеток и направление потока — только в режиме стройки
        if (buildType < 0) return;
        for (int y = 0; y < Config.ROWS; y++) {
            for (int x = 0; x < Config.COLS; x++) {
                int gi = w.grid.idx(x, y);
                if (w.grid.blocked[gi]) continue;
                int nf = w.grid.flow[gi];
                if (nf < 0) continue;
                int nx = nf % Config.COLS, ny = nf / Config.COLS;
                float cx = L.x(x + 0.5f), cy = L.y(y + 0.5f);
                float dx = (nx - x) * c * 0.18f, dy = (ny - y) * c * 0.18f;
                sr.setColor(0.32f, 0.55f, 0.78f, 0.12f);
                sr.rectLine(cx - dx, cy - dy, cx + dx, cy + dy, c * 0.03f);
                sr.rectLine(cx + dx, cy + dy, cx + dx * 0.2f - dy * 0.4f, cy + dy * 0.2f + dx * 0.4f, c * 0.03f);
                sr.rectLine(cx + dx, cy + dy, cx + dx * 0.2f + dy * 0.4f, cy + dy * 0.2f - dx * 0.4f, c * 0.03f);
            }
        }
    }

    private void rocks(ShapeRenderer sr, World w, Layout L) {
        for (int y = 0; y < Config.ROWS; y++) {
            for (int x = 0; x < Config.COLS; x++) {
                if (!w.isRock(x, y)) continue;
                float cx = L.x(x + 0.5f), cy = L.y(y + 0.5f);
                sr.setColor(0.10f, 0.12f, 0.17f, 1f);
                sr.rect(L.x(x) + L.cell * 0.06f, L.y(y) + L.cell * 0.06f,
                        L.cell * 0.88f, L.cell * 0.88f);
                sr.setColor(0.20f, 0.25f, 0.34f, 1f);
                for (int k = -1; k <= 1; k++) {
                    float o = k * L.cell * 0.3f;
                    sr.rectLine(L.x(x) + L.cell * 0.1f + Math.max(0, o), L.y(y) + L.cell * 0.1f - Math.min(0, o),
                            L.x(x) + L.cell * 0.9f + Math.min(0, o), L.y(y) + L.cell * 0.9f - Math.max(0, o), 2f);
                }
            }
        }
    }

    // --------------------------------------------------------------- ядро

    private void coreGlow(ShapeRenderer sr, World w, Layout L) {
        float cx = L.x(Config.CORE_X + 0.5f), cy = L.y(Config.CORE_Y + 0.5f);
        float p = 0.5f + 0.5f * (float) Math.sin(w.time * 3.0f);
        float r = L.cell * (0.55f + 0.09f * p);
        sr.setColor(tmp.set(Config.CORE_C).mul(1f, 1f, 1f, 0.25f + 0.12f * p));
        Draw.disc(sr, cx, cy, r * 1.5f);
    }

    private void core(ShapeRenderer sr, World w, Layout L) {
        float cx = L.x(Config.CORE_X + 0.5f), cy = L.y(Config.CORE_Y + 0.5f);
        float p = 0.5f + 0.5f * (float) Math.sin(w.time * 3.0f);
        float r = L.cell * 0.42f;

        sr.setColor(0.05f, 0.10f, 0.12f, 1f);
        Draw.polyFill(sr, cx, cy, r, 6, w.time * 0.25f);
        sr.setColor(Config.CORE_C);
        Draw.poly(sr, cx, cy, r, 6, w.time * 0.25f, 3.2f);
        sr.setColor(tmp.set(Config.CORE_C).mul(1f, 1f, 1f, 0.55f + 0.35f * p));
        Draw.poly(sr, cx, cy, r * 0.5f, 3, -w.time * 0.7f, 2.6f);

        // кольцо прочности
        float frac = Math.max(0f, w.integrity / (float) Config.START_INTEGRITY);
        int seg = 40;
        int lit = (int) (seg * frac + 0.5f);
        for (int i = 0; i < seg; i++) {
            float a0 = i * 6.2831855f / seg + 0.06f;
            float a1 = (i + 1) * 6.2831855f / seg - 0.06f;
            boolean on = i < lit;
            if (on) sr.setColor(Config.CORE_C);
            else sr.setColor(0.30f, 0.15f, 0.20f, 0.8f);
            float rr = r * 1.45f;
            sr.rectLine(cx + (float) Math.cos(a0) * rr, cy + (float) Math.sin(a0) * rr,
                    cx + (float) Math.cos(a1) * rr, cy + (float) Math.sin(a1) * rr, 3f);
        }
    }

    private void portals(ShapeRenderer sr, World w, Layout L) {
        int active = Waves.portalsFor(Math.max(1, w.wave));
        for (int i = 0; i < Config.PORTAL_X.length; i++) {
            float cx = L.x(Config.PORTAL_X[i] + 0.5f), cy = L.y(Config.PORTAL_Y[i] + 0.5f);
            boolean on = i < active;
            float a = on ? 0.9f : 0.22f;
            float ph = (w.time * 1.6f + i * 0.7f) % 1f;
            sr.setColor(tmp.set(Config.PORTAL_C).mul(1f, 1f, 1f, a * (1f - ph)));
            Draw.ring(sr, cx, cy, L.cell * (0.18f + ph * 0.4f), 3f);
            sr.setColor(tmp.set(Config.PORTAL_C).mul(1f, 1f, 1f, a));
            Draw.poly(sr, cx, cy, L.cell * 0.34f, 3, 3.14159f + w.time * 0.5f, 3f);
        }
    }

    // -------------------------------------------------------------- башни

    private void towers(ShapeRenderer sr, World w, Layout L, Tower selected) {
        for (int i = 0; i < w.towers.size; i++) {
            Tower t = w.towers.get(i);
            float cx = L.x(t.gx + 0.5f), cy = L.y(t.gy + 0.5f);
            Color c = Config.T_COLOR[t.type];
            float r = L.cell * 0.36f;

            sr.setColor(0.05f, 0.07f, 0.12f, 1f);
            Draw.polyFill(sr, cx, cy, L.cell * 0.44f, sides(t.type), rot(t, w));

            if (t.type == Config.T_WALL) {
                sr.setColor(c);
                Draw.poly(sr, cx, cy, r, 4, 0.7853f, 3f);
                sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.45f));
                Draw.polyFill(sr, cx, cy, r * 0.55f, 4, 0.7853f);
                continue;
            }

            float f = t.flash;
            sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.85f + 0.15f * f));
            Draw.poly(sr, cx, cy, r, sides(t.type), rot(t, w), 3f + 2f * f);
            sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.35f + 0.55f * f));
            Draw.polyFill(sr, cx, cy, r * (0.34f + 0.22f * f), 6, -rot(t, w) * 1.7f);

            // отметка фазы: точка сверху (такт) или сбоку (полтакта)
            sr.setColor(t.phase == 0 ? Config.NODE_C : Config.T_COLOR[Config.T_PHASER]);
            float pa = t.phase == 0 ? 1.5708f : 0f;
            Draw.disc(sr, cx + (float) Math.cos(pa) * r * 1.05f,
                    cy + (float) Math.sin(pa) * r * 1.05f, L.cell * 0.055f);

            // уровень — засечки снизу
            for (int k = 1; k < t.level; k++) {
                sr.setColor(Config.OK_C);
                float ox = (k - 1) * L.cell * 0.13f - L.cell * 0.065f;
                sr.rect(cx + ox - L.cell * 0.03f, cy - r * 1.25f, L.cell * 0.06f, L.cell * 0.05f);
            }
        }
    }

    /** Подсветка выбранной башни: её радиус и рамка. */
    private void selection(ShapeRenderer sr, World w, Layout L, Tower t) {
        if (t == null) return;
        float cx = L.x(t.gx + 0.5f), cy = L.y(t.gy + 0.5f);
        Color c = Config.T_COLOR[t.type];
        sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.85f));
        Draw.poly(sr, cx, cy, L.cell * 0.52f, 4, 0.7853f + w.time * 0.6f, 2.6f);
        if (t.emits()) {
            sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.30f));
            Draw.ring(sr, cx, cy, L.r(t.radius), 1.8f);
        }
    }

    private int sides(int type) {
        if (type == Config.T_PULSAR) return 6;
        if (type == Config.T_PHASER) return 4;
        if (type == Config.T_RESONATOR) return 8;
        return 4;
    }

    private float rot(Tower t, World w) {
        if (t.type == Config.T_PHASER) return 0.7853f + t.spin * 0.3f;
        if (t.type == Config.T_RESONATOR) return t.spin * 0.18f;
        return t.spin * 0.12f;
    }

    // ------------------------------------------------------------ фронты

    private void pulses(ShapeRenderer sr, World w, Layout L) {
        for (int i = 0; i < w.pulses.size; i++) {
            Pulse p = w.pulses.get(i);
            float k = p.r / p.maxR;
            float a = (1f - k) * 0.85f + 0.05f;
            Draw.glowRing(sr, L.x(p.x), L.y(p.y), L.r(p.r), L.cell * 0.035f,
                    Config.T_COLOR[p.type], a);
        }
    }

    private void nodes(ShapeRenderer sr, World w, Layout L) {
        for (int i = 0; i < w.nodes.size; i++) {
            ResNode n = w.nodes.get(i);
            float x = L.x(n.x), y = L.y(n.y);
            float s = L.cell * (0.16f + 0.05f * Math.min(4, n.order)) * (0.7f + 0.3f * n.energy);
            boolean harmonic = n.order >= Config.HARMONIC_PIERCE;
            Color c = harmonic ? Config.OK_C : Config.NODE_C;
            sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.35f));
            Draw.disc(sr, x, y, s * 1.5f);
            sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.95f));
            Draw.disc(sr, x, y, s * 0.55f);
            Draw.spark(sr, x, y, s * (harmonic ? 2.6f : 1.8f), L.cell * 0.022f,
                    harmonic ? 3 : 2, w.time * 2f);
        }
    }

    private void particles(ShapeRenderer sr, World w, Layout L) {
        for (int i = 0; i < w.particles.size; i++) {
            Particle p = w.particles.get(i);
            float a = p.life / p.maxLife;
            sr.setColor(p.color.r, p.color.g, p.color.b, a * 0.9f);
            Draw.disc(sr, L.x(p.x), L.y(p.y), L.r(p.size) * (0.4f + a * 0.9f));
        }
    }

    // -------------------------------------------------------------- враги

    private void enemies(ShapeRenderer sr, World w, Layout L) {
        for (int i = 0; i < w.enemies.size; i++) {
            Enemy e = w.enemies.get(i);
            float cx = L.x(e.x), cy = L.y(e.y);
            float r = L.cell * size(e.type);
            Color c = Config.E_COLOR[e.type];
            boolean ghost = !e.vulnerable();

            float alpha = ghost ? 0.28f : 1f;
            float ang = w.time * spin(e.type) + e.wobble;

            sr.setColor(0.04f, 0.05f, 0.09f, alpha);
            Draw.polyFill(sr, cx, cy, r, shape(e.type), ang);

            float fl = e.hitFlash;
            sr.setColor(tmp.set(c).lerp(1f, 1f, 1f, 1f, fl).mul(1f, 1f, 1f, alpha));
            Draw.poly(sr, cx, cy, r, shape(e.type), ang, L.cell * 0.05f);

            if (e.armored) {
                sr.setColor(tmp.set(c).mul(1f, 1f, 1f, alpha * 0.7f));
                Draw.poly(sr, cx, cy, r * 0.6f, 6, -ang, L.cell * 0.035f);
            }

            // полоска здоровья
            float hf = Math.max(0f, e.hp / e.maxHp);
            if (hf < 0.999f) {
                float bw = L.cell * 0.62f, bh = L.cell * 0.075f;
                sr.setColor(0.1f, 0.12f, 0.16f, 0.85f);
                sr.rect(cx - bw * 0.5f, cy + r + bh * 0.9f, bw, bh);
                sr.setColor(hf > 0.5f ? Config.OK_C : Config.BAD_C);
                sr.rect(cx - bw * 0.5f, cy + r + bh * 0.9f, bw * hf, bh);
            }
        }
    }

    private int shape(int type) {
        switch (type) {
            case Config.E_DRONE: return 3;
            case Config.E_SWIFT: return 3;
            case Config.E_ARMOR: return 6;
            case Config.E_SWARM: return 5;
            case Config.E_MOTE: return 4;
            default: return 4;
        }
    }

    private float size(int type) {
        switch (type) {
            case Config.E_SWIFT: return 0.20f;
            case Config.E_ARMOR: return 0.36f;
            case Config.E_SWARM: return 0.28f;
            case Config.E_MOTE: return 0.15f;
            case Config.E_PHANTOM: return 0.28f;
            default: return 0.25f;
        }
    }

    private float spin(int type) {
        switch (type) {
            case Config.E_SWIFT: return 4.5f;
            case Config.E_ARMOR: return 0.6f;
            case Config.E_MOTE: return 6f;
            default: return 1.6f;
        }
    }

    // ----------------------------------------------------------- превью

    /** Призрак постройки: радиус фронта и линии резонанса с соседями. */
    private void preview(ShapeRenderer sr, World w, Layout L, int type, int gx, int gy) {
        if (type < 0 || !w.grid.in(gx, gy)) return;
        int verdict = w.canBuild(type, gx, gy);
        boolean ok = verdict == World.BUILD_OK;
        float cx = L.x(gx + 0.5f), cy = L.y(gy + 0.5f);
        Color c = ok ? Config.T_COLOR[type] : Config.BAD_C;

        sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.18f));
        sr.rect(L.x(gx), L.y(gy), L.cell, L.cell);
        sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.9f));
        Draw.poly(sr, cx, cy, L.cell * 0.36f, sides(type), 0.3f, 2.5f);

        if (!ok || Config.T_INTERVAL[type] == 0) return;

        float rad = Config.T_RADIUS[type];
        sr.setColor(tmp.set(c).mul(1f, 1f, 1f, 0.35f));
        Draw.ring(sr, cx, cy, L.r(rad), 1.8f);

        // линии резонанса с уже стоящими излучателями
        for (int i = 0; i < w.towers.size; i++) {
            Tower t = w.towers.get(i);
            if (!t.emits()) continue;
            float tx = t.gx + 0.5f, ty = t.gy + 0.5f;
            float dx = tx - (gx + 0.5f), dy = ty - (gy + 0.5f);
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float reach = (rad + t.radius) * 0.5f;
            if (d < 0.1f || d > reach * 1.6f) continue;

            float mx = (gx + 0.5f + tx) * 0.5f, my = (gy + 0.5f + ty) * 0.5f;
            float half = Math.min(rad, t.radius);
            float h2 = half * half - (d * 0.5f) * (d * 0.5f);
            if (h2 <= 0.02f) continue;
            float h = (float) Math.sqrt(h2);
            float ox = -dy / d * h, oy = dx / d * h;

            sr.setColor(tmp.set(Config.NODE_C).mul(1f, 1f, 1f, 0.55f));
            dashed(sr, L.x(mx - ox), L.y(my - oy), L.x(mx + ox), L.y(my + oy), L.cell * 0.035f, L.cell * 0.22f);
        }
    }

    private void dashed(ShapeRenderer sr, float x1, float y1, float x2, float y2, float w, float dash) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        int n = Math.max(1, (int) (len / dash));
        for (int i = 0; i < n; i += 2) {
            float t0 = i / (float) n, t1 = Math.min(1f, (i + 1) / (float) n);
            sr.rectLine(x1 + dx * t0, y1 + dy * t0, x1 + dx * t1, y1 + dy * t1, w);
        }
    }

    // ------------------------------------------------------------ блендинг

    private void normalBlend() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void normalBlend(ShapeRenderer sr) {
        sr.flush();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void addBlend(ShapeRenderer sr) {
        sr.flush();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
    }
}
