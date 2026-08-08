package com.resonance.td;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;

/** Панель управления: кнопки стройки, действия с башней, верхняя строка. */
public class Hud {

    public static final int B_BUILD0 = 0;
    public static final int B_UPGRADE = 10;
    public static final int B_PHASE = 11;
    public static final int B_SELL = 12;
    public static final int B_GEN = 20;
    public static final int B_WAVE = 21;
    public static final int B_SPEED = 30;
    public static final int B_PAUSE = 31;
    public static final int B_HELP = 32;

    public static class Btn {
        public int id;
        public float x, y, w, h;
        public String top = "", mid = "", bot = "";
        public Color color = new Color(Config.TEXT);
        public boolean on = true;
        public boolean active;
    }

    public int buildType = -1;
    public Tower selected;
    public int speed = 1;
    public boolean paused;
    public boolean showHelp;

    private String toast = "";
    private float toastT;

    public final Array<Btn> btns = new Array<Btn>();
    private final GlyphLayout gl = new GlyphLayout();
    private final Color tmp = new Color();

    // ------------------------------------------------------------- раскладка

    public void layout(Layout L, World w) {
        btns.clear();
        float pad = L.worldW * 0.014f;
        float bw = (L.worldW - pad * 5f) / 4f;
        float bh = L.panelH * 0.40f;
        float by = L.panelH - L.panelH * 0.125f - bh;

        for (int i = 0; i < Config.T_COUNT; i++) {
            Btn b = new Btn();
            b.id = B_BUILD0 + i;
            b.x = pad + i * (bw + pad);
            b.y = by;
            b.w = bw;
            b.h = bh;
            b.top = Config.T_NAME[i];
            b.mid = String.valueOf(Config.T_COST[i]);
            b.bot = Config.T_POWER[i] > 0 ? ("сеть " + Config.T_POWER[i]) : "—";
            b.color.set(Config.T_COLOR[i]);
            b.on = w.energy >= Config.T_COST[i]
                    && w.powerUsed() + Config.T_POWER[i] <= w.powerCap();
            b.active = buildType == i;
            btns.add(b);
        }

        float ay = pad;
        float ah = by - pad * 2f;
        if (selected != null) {
            float aw = (L.worldW - pad * 4f) / 3f;
            Btn up = mk(B_UPGRADE, pad, ay, aw, ah, "УЛУЧШИТЬ",
                    selected.level >= Config.MAX_LEVEL ? "макс" : String.valueOf(selected.upgradeCost()),
                    selected.level >= Config.MAX_LEVEL ? "" : ("ур. " + (selected.level + 1)), Config.OK_C);
            up.on = selected.emits() && selected.level < Config.MAX_LEVEL
                    && w.energy >= selected.upgradeCost()
                    && w.powerUsed() + Config.UPGRADE_POWER <= w.powerCap();
            Btn ph = mk(B_PHASE, pad * 2 + aw, ay, aw, ah, "ФАЗА",
                    String.valueOf(Config.PHASE_COST),
                    selected.phase == 0 ? "на такт" : "полтакта", Config.T_COLOR[Config.T_PHASER]);
            ph.on = selected.emits() && w.energy >= Config.PHASE_COST;
            Btn sl = mk(B_SELL, pad * 3 + aw * 2, ay, aw, ah, "ПРОДАТЬ",
                    "+" + selected.sellValue(), "", Config.BAD_C);
            btns.add(up);
            btns.add(ph);
            btns.add(sl);
        } else {
            float aw = (L.worldW - pad * 3f) / 2f;
            Btn gen = mk(B_GEN, pad, ay, aw, ah, "ГЕНЕРАТОР",
                    String.valueOf(w.genCost()), "+" + Config.POWER_PER_NODE + " к сети", Config.OK_C);
            gen.on = w.energy >= w.genCost();
            Btn wv = mk(B_WAVE, pad * 2 + aw, ay, aw, ah,
                    w.state == World.ST_PREP ? "ВОЛНА " + (w.wave + 1) : "ИДЁТ ВОЛНА " + w.wave,
                    w.state == World.ST_PREP ? "ЗАПУСК" : (w.enemiesLeft() + " целей"),
                    w.state == World.ST_PREP ? ("+" + (int) (w.prepLeft * Config.EARLY_BONUS) + " энергии") : "",
                    Config.PORTAL_C);
            wv.on = w.state == World.ST_PREP;
            btns.add(gen);
            btns.add(wv);
        }

        // верхняя строка
        float tb = L.topBarH * 0.55f;
        float ty = L.worldH - L.topBarH * 0.5f - tb * 0.5f;
        Btn sp = mk(B_SPEED, L.worldW - pad * 2f - tb * 2.9f, ty, tb * 1.9f, tb,
                "", "×" + speed, "", Config.T_COLOR[Config.T_PULSAR]);
        Btn ps = mk(B_PAUSE, L.worldW - pad - tb, ty, tb, tb, "", paused ? "▶" : "II", "", Config.TEXT_DIM);
        Btn hp = mk(B_HELP, pad, ty, tb, tb, "", "?", "", Config.TEXT_DIM);
        btns.add(sp);
        btns.add(ps);
        btns.add(hp);
    }

    private Btn mk(int id, float x, float y, float w, float h, String top, String mid, String bot, Color c) {
        Btn b = new Btn();
        b.id = id;
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = h;
        b.top = top;
        b.mid = mid;
        b.bot = bot;
        b.color.set(c);
        return b;
    }

    // ---------------------------------------------------------------- ввод

    public Btn hit(float x, float y) {
        for (int i = 0; i < btns.size; i++) {
            Btn b = btns.get(i);
            if (x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h) return b;
        }
        return null;
    }

    /** Возвращает true, если касание съедено интерфейсом. */
    public boolean tap(float x, float y, World w, Layout L) {
        if (showHelp) {
            showHelp = false;
            return true;
        }
        Btn b = hit(x, y);
        if (b == null) return y < L.panelH || y > L.worldH - L.topBarH;

        switch (b.id) {
            case B_PAUSE:
                paused = !paused;
                return true;
            case B_SPEED:
                speed = speed >= 3 ? 1 : speed + 1;
                return true;
            case B_HELP:
                showHelp = true;
                return true;
            case B_GEN:
                if (!w.buyGenerator()) say("Не хватает энергии");
                else say("Мощность сети +" + Config.POWER_PER_NODE);
                return true;
            case B_WAVE:
                if (w.state == World.ST_PREP) w.callWaveNow();
                return true;
            case B_UPGRADE:
                if (selected != null && !w.upgrade(selected)) say(reasonUpgrade(w, selected));
                return true;
            case B_PHASE:
                if (selected != null && !w.togglePhase(selected)) say("Не хватает энергии");
                return true;
            case B_SELL:
                if (selected != null) {
                    w.sell(selected);
                    selected = null;
                }
                return true;
            default:
                if (b.id >= B_BUILD0 && b.id < B_BUILD0 + Config.T_COUNT) {
                    int t = b.id - B_BUILD0;
                    buildType = (buildType == t) ? -1 : t;
                    selected = null;
                    return true;
                }
        }
        return true;
    }

    private String reasonUpgrade(World w, Tower t) {
        if (t.level >= Config.MAX_LEVEL) return "Уже максимальный уровень";
        if (w.energy < t.upgradeCost()) return "Не хватает энергии";
        return "Не хватает мощности сети";
    }

    public void say(String s) {
        toast = s;
        toastT = 2.2f;
    }

    public void update(float dt) {
        if (toastT > 0f) toastT -= dt;
    }

    /** Сообщение об отказе в стройке. */
    public void sayBuild(int verdict) {
        switch (verdict) {
            case World.BUILD_MONEY: say("Не хватает энергии"); break;
            case World.BUILD_POWER: say("Сеть перегружена — нужен генератор"); break;
            case World.BUILD_PATH: say("Так путь к ядру перекрыт полностью"); break;
            case World.BUILD_ENEMY: say("Здесь враг"); break;
            default: say("Здесь нельзя строить");
        }
    }

    // -------------------------------------------------------------- отрисовка

    public void drawShapes(ShapeRenderer sr, World w, Layout L) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // нижняя панель
        sr.setColor(Config.PANEL);
        sr.rect(0, 0, L.worldW, L.panelH);
        sr.setColor(0.20f, 0.42f, 0.60f, 0.5f);
        sr.rect(0, L.panelH - 2f, L.worldW, 2f);

        // верхняя строка
        sr.setColor(Config.PANEL);
        sr.rect(0, L.worldH - L.topBarH, L.worldW, L.topBarH);
        sr.setColor(0.20f, 0.42f, 0.60f, 0.5f);
        sr.rect(0, L.worldH - L.topBarH, L.worldW, 2f);

        // подложка под всплывающее сообщение
        if (toastT > 0f) {
            float a = Math.min(1f, toastT);
            sr.setColor(0.02f, 0.03f, 0.07f, 0.80f * a);
            sr.rect(0, L.panelH + 24f, L.worldW, 42f);
        }

        // шкала мощности сети
        float pu = w.powerUsed(), pc = Math.max(1, w.powerCap());
        float barW = L.worldW * 0.42f, barH = 5f;
        float bx = L.worldW * 0.29f, byy = L.worldH - L.topBarH + 5f;
        sr.setColor(0.15f, 0.20f, 0.28f, 1f);
        sr.rect(bx, byy, barW, barH);
        sr.setColor(pu / pc > 0.92f ? Config.BAD_C : Config.T_COLOR[Config.T_PULSAR]);
        sr.rect(bx, byy, barW * Math.min(1f, pu / pc), barH);

        for (int i = 0; i < btns.size; i++) {
            Btn b = btns.get(i);
            float a = b.on ? 1f : 0.35f;
            sr.setColor(tmp.set(b.color).mul(0.16f, 0.16f, 0.16f, b.active ? 0.55f : 0.30f));
            Draw.panel(sr, b.x, b.y, b.w, b.h, Math.min(b.w, b.h) * 0.18f);
            sr.setColor(tmp.set(b.color).mul(1f, 1f, 1f, b.active ? 1f : 0.55f * a));
            Draw.panelOutline(sr, b.x, b.y, b.w, b.h, Math.min(b.w, b.h) * 0.18f, b.active ? 3.4f : 2f);
        }

        // индикатор такта
        float beat = 1f - w.beatPhase;
        float cxp = L.worldW * 0.5f;
        sr.setColor(tmp.set(Config.NODE_C).mul(1f, 1f, 1f, 0.15f + 0.45f * beat * beat));
        sr.rect(cxp - L.worldW * 0.5f, L.panelH - 2f, L.worldW, 2f);

        sr.end();
    }

    public void drawText(SpriteBatch batch, BitmapFont font, World w, Layout L) {
        float s = L.worldH / 1280f;

        // верхняя строка
        float ly = L.worldH - L.topBarH * 0.16f;
        stat(batch, font, "ЯДРО", String.valueOf(w.integrity),
                w.integrity > 6 ? Config.CORE_C : Config.BAD_C, L.worldW * 0.135f, ly, s, L);
        stat(batch, font, "ЭНЕРГИЯ", String.valueOf(w.energy),
                Config.NODE_C, L.worldW * 0.30f, ly, s, L);
        stat(batch, font, "СЕТЬ", w.powerUsed() + "/" + w.powerCap(),
                Config.T_COLOR[Config.T_PULSAR], L.worldW * 0.455f, ly, s, L);
        stat(batch, font, "ВОЛНА", w.wave + "/" + Config.WAVES_TOTAL,
                Config.PORTAL_C, L.worldW * 0.60f, ly, s, L);

        for (int i = 0; i < btns.size; i++) {
            Btn b = btns.get(i);
            float a = b.on ? 1f : 0.4f;
            float cy = b.y + b.h * 0.5f;
            if (b.top.length() > 0) {
                center(batch, font, b.top, b.x + b.w * 0.5f, b.y + b.h - 8f * s,
                        0.52f * s, tmp.set(b.color).mul(1f, 1f, 1f, a));
            }
            if (b.mid.length() > 0) {
                center(batch, font, b.mid, b.x + b.w * 0.5f,
                        b.top.length() > 0 ? cy + 6f * s : cy + 14f * s,
                        0.78f * s, tmp.set(Config.TEXT).mul(1f, 1f, 1f, a));
            }
            if (b.bot.length() > 0) {
                center(batch, font, b.bot, b.x + b.w * 0.5f, b.y + 26f * s,
                        0.46f * s, tmp.set(Config.TEXT_DIM).mul(1f, 1f, 1f, a));
            }
        }

        // строка состояния — внутри панели, под верхним краем
        float statusY = L.panelH - 8f;
        if (buildType >= 0) {
            center(batch, font, Config.T_DESC[buildType], L.worldW * 0.5f, statusY, 0.5f * s,
                    tmp.set(Config.T_COLOR[buildType]).mul(1f, 1f, 1f, 0.9f));
        } else if (selected != null) {
            String line = Config.T_NAME[selected.type] + " ур." + selected.level
                    + "   энергия " + fmt(selected.energy)
                    + "   радиус " + fmt(selected.radius)
                    + "   " + (selected.phase == 0 ? "фаза: на такт" : "фаза: полтакта");
            center(batch, font, line, L.worldW * 0.5f, statusY, 0.48f * s, Config.TEXT_DIM);
        } else if (w.state == World.ST_PREP) {
            String line = "Волна " + (w.wave + 1) + " — " + Waves.label(w.wave + 1)
                    + "   ·   старт через " + (int) Math.ceil(w.prepLeft) + " с";
            center(batch, font, line, L.worldW * 0.5f, statusY, 0.5f * s, Config.TEXT_DIM);
        } else {
            center(batch, font, "Целей осталось: " + w.enemiesLeft(),
                    L.worldW * 0.5f, statusY, 0.5f * s, Config.TEXT_DIM);
        }

        if (toastT > 0f) {
            float a = Math.min(1f, toastT);
            center(batch, font, toast, L.worldW * 0.5f, L.panelH + 58f * s, 0.62f * s,
                    tmp.set(Config.BAD_C).mul(1f, 1f, 1f, a));
        }
    }

    /** Есть ли сейчас всплывающее сообщение (для подложки). */
    public float toastAlpha() {
        return toastT > 0f ? Math.min(1f, toastT) : 0f;
    }

    private String fmt(float v) {
        return String.valueOf(Math.round(v * 10f) / 10f);
    }

    private void stat(SpriteBatch b, BitmapFont f, String label, String value, Color c,
                      float x, float y, float s, Layout L) {
        f.getData().setScale(0.40f * s);
        f.setColor(Config.TEXT_DIM);
        f.draw(b, label, x, y);
        f.getData().setScale(0.76f * s);
        f.setColor(c);
        f.draw(b, value, x, y - L.topBarH * 0.34f);
    }

    public void center(SpriteBatch b, BitmapFont f, String t, float cx, float topY, float scale, Color c) {
        f.getData().setScale(scale);
        f.setColor(c);
        gl.setText(f, t);
        f.draw(b, t, cx - gl.width * 0.5f, topY);
    }
}
