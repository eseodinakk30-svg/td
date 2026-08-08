package com.resonance.td;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

/**
 * РЕЗОНАНС — башенная защита, где башни не стреляют.
 *
 * Каждый излучатель на своём такте выпускает расширяющееся кольцо.
 * Урон возникает только там, где кольца ПЕРЕСЕКАЮТСЯ. Поэтому важна
 * геометрия: пара башен создаёт «линию резонанса» — серединный
 * перпендикуляр между ними. Задача — уложить эти линии вдоль маршрута
 * врага, который вы сами и лепите, ставя постройки.
 */
public class ResonanceGame extends ApplicationAdapter {

    static final int SC_MENU = 0;
    static final int SC_PLAY = 1;

    private OrthographicCamera cam;
    private ExtendViewport viewport;
    private ShapeRenderer sr;
    private SpriteBatch batch;
    private BitmapFont font;

    private final Layout L = new Layout();
    private final Renderer renderer = new Renderer();
    final Hud hud = new Hud();
    private final Sfx sfx = new Sfx();
    World world;

    int screen = SC_MENU;
    private float menuTime;
    private final Vector3 touch = new Vector3();
    private final Color tmp = new Color();

    int hoverX = -1, hoverY = -1;
    private boolean dragging;

    private int lastBeat, lastKill, lastLeak, lastHarm;
    private float harmCooldown;

    @Override
    public void create() {
        cam = new OrthographicCamera();
        viewport = new ExtendViewport(720f, 1280f, cam);
        sr = new ShapeRenderer(12000);
        batch = new SpriteBatch(1000);

        font = new BitmapFont(Gdx.files.internal("font.fnt"), false);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);

        world = new World();
        sfx.start();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                return onDown(x, y);
            }

            @Override
            public boolean touchDragged(int x, int y, int pointer) {
                return onDrag(x, y);
            }

            @Override
            public boolean touchUp(int x, int y, int pointer, int button) {
                return onUp(x, y);
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    if (screen == SC_PLAY) hud.paused = !hud.paused;
                    return true;
                }
                return false;
            }
        });
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        L.resize(viewport.getWorldWidth(), viewport.getWorldHeight());
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
        Gdx.gl.glClearColor(0.012f, 0.016f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        L.resize(viewport.getWorldWidth(), viewport.getWorldHeight());

        if (screen == SC_MENU) {
            menuTime += dt;
            drawMenu();
            return;
        }

        boolean running = !hud.paused && !hud.showHelp
                && world.state != World.ST_LOST && world.state != World.ST_WON;
        if (running) {
            for (int i = 0; i < hud.speed; i++) world.update(dt);
        }
        hud.update(dt);
        pumpSound();

        // тряска экрана
        float sh = world.shake;
        cam.position.set(L.worldW * 0.5f + MathUtils.random(-1f, 1f) * sh * 9f,
                L.worldH * 0.5f + MathUtils.random(-1f, 1f) * sh * 9f, 0f);
        cam.update();
        sr.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        renderer.draw(sr, world, L, hud.buildType, hoverX, hoverY, hud.selected);

        hud.layout(L, world);
        hud.drawShapes(sr, world, L);

        batch.begin();
        hud.drawText(batch, font, world, L);
        batch.end();

        if (hud.showHelp) drawHelp();
        else if (world.state == World.ST_LOST) drawEnd(false);
        else if (world.state == World.ST_WON) drawEnd(true);
        else if (hud.paused) drawPause();
    }

    // ---------------------------------------------------------------- ввод

    private void unproject(int x, int y) {
        touch.set(x, y, 0f);
        viewport.unproject(touch);
    }

    private boolean onDown(int sx, int sy) {
        unproject(sx, sy);
        float x = touch.x, y = touch.y;

        if (screen == SC_MENU) {
            screen = SC_PLAY;
            sfx.trigger(Sfx.CLICK);
            return true;
        }
        if (world.state == World.ST_LOST || world.state == World.ST_WON) {
            restart();
            return true;
        }
        if (hud.showHelp) {
            hud.showHelp = false;
            return true;
        }
        if (hud.paused) {
            Hud.Btn b = hud.hit(x, y);
            if (b != null && b.id == Hud.B_PAUSE) hud.paused = false;
            return true;
        }

        if (y < L.panelH || y > L.worldH - L.topBarH) {
            hud.tap(x, y, world, L);
            sfx.trigger(Sfx.CLICK);
            return true;
        }

        if (L.inField(x, y)) {
            int gx = L.gridX(x), gy = L.gridY(y);
            if (hud.buildType >= 0) {
                dragging = true;
                hoverX = gx;
                hoverY = gy;
            } else {
                Tower t = world.towerAt(gx, gy);
                hud.selected = t;
                sfx.trigger(Sfx.CLICK);
            }
        }
        return true;
    }

    private boolean onDrag(int sx, int sy) {
        if (screen != SC_PLAY || !dragging) return false;
        unproject(sx, sy);
        if (L.inField(touch.x, touch.y)) {
            hoverX = L.gridX(touch.x);
            hoverY = L.gridY(touch.y);
        }
        return true;
    }

    private boolean onUp(int sx, int sy) {
        if (screen != SC_PLAY || !dragging) return false;
        dragging = false;
        int gx = hoverX, gy = hoverY;
        hoverX = hoverY = -1;
        if (hud.buildType < 0 || !world.grid.in(gx, gy)) return true;

        int verdict = world.canBuild(hud.buildType, gx, gy);
        if (verdict == World.BUILD_OK) {
            world.build(hud.buildType, gx, gy);
            sfx.trigger(Sfx.CLICK);
            if (world.energy < Config.T_COST[hud.buildType]) hud.buildType = -1;
        } else {
            hud.sayBuild(verdict);
        }
        return true;
    }

    private void restart() {
        world = new World();
        hud.selected = null;
        hud.buildType = -1;
        hud.paused = false;
        hud.speed = 1;
        lastBeat = lastKill = lastLeak = lastHarm = 0;
    }

    // ---------------------------------------------------------------- звук

    private void pumpSound() {
        while (lastBeat < world.evBeat) {
            lastBeat++;
            sfx.trigger((lastBeat % 2 == 0) ? Sfx.KICK : Sfx.HAT);
        }
        while (lastKill < world.evKill) {
            lastKill++;
            sfx.trigger(Sfx.BOOM);
        }
        while (lastLeak < world.evLeak) {
            lastLeak++;
            sfx.trigger(Sfx.ALARM);
        }
        harmCooldown -= Gdx.graphics.getDeltaTime();
        if (world.evHarmonic > lastHarm) {
            if (harmCooldown <= 0f) {
                sfx.trigger(Sfx.CHIME);
                harmCooldown = 0.22f;
            }
            lastHarm = world.evHarmonic;
        }
    }

    // ------------------------------------------------------------- экраны

    private void drawMenu() {
        cam.position.set(L.worldW * 0.5f, L.worldH * 0.5f, 0f);
        cam.update();
        sr.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.rect(0, 0, L.worldW, L.worldH, Config.BG_BOTTOM, Config.BG_BOTTOM, Config.BG_TOP, Config.BG_TOP);

        sr.flush();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        // живая заставка: две «башни» и их резонанс
        float cx = L.worldW * 0.5f, cy = L.worldH * 0.62f;
        float d = L.worldW * 0.20f;
        float ax = cx - d, bx = cx + d;
        float maxR = L.worldW * 0.42f;
        for (int k = 0; k < 3; k++) {
            float t = (menuTime * 0.45f + k / 3f) % 1f;
            float r = t * maxR;
            float a = (1f - t) * 0.8f;
            Draw.glowRing(sr, ax, cy, r, 3f, Config.T_COLOR[Config.T_PULSAR], a);
            Draw.glowRing(sr, bx, cy, r * 0.92f, 3f, Config.T_COLOR[Config.T_PHASER], a);
            float rr = r, r2 = r * 0.92f;
            float h2 = rr * rr - (float) Math.pow((rr * rr - r2 * r2 + 4 * d * d) / (4 * d), 2);
            if (h2 > 0) {
                float xm = ((rr * rr - r2 * r2 + 4 * d * d) / (4 * d)) + ax;
                float h = (float) Math.sqrt(h2);
                sr.setColor(tmp.set(Config.NODE_C).mul(1f, 1f, 1f, a));
                Draw.disc(sr, xm, cy + h, L.worldW * 0.012f);
                Draw.disc(sr, xm, cy - h, L.worldW * 0.012f);
            }
        }
        sr.setColor(Config.T_COLOR[Config.T_PULSAR]);
        Draw.poly(sr, ax, cy, L.worldW * 0.035f, 6, menuTime * 0.4f, 3f);
        sr.setColor(Config.T_COLOR[Config.T_PHASER]);
        Draw.poly(sr, bx, cy, L.worldW * 0.035f, 4, 0.78f - menuTime * 0.4f, 3f);

        sr.flush();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float bw = L.worldW * 0.55f, bh = L.worldH * 0.075f;
        float bxp = (L.worldW - bw) * 0.5f, byp = L.worldH * 0.115f;
        sr.setColor(0.10f, 0.30f, 0.34f, 0.75f);
        Draw.panel(sr, bxp, byp, bw, bh, bh * 0.3f);
        sr.setColor(Config.CORE_C);
        Draw.panelOutline(sr, bxp, byp, bw, bh, bh * 0.3f, 3f);
        sr.end();

        float s = L.worldH / 1280f;
        batch.begin();
        hud.center(batch, font, "РЕЗОНАНС", L.worldW * 0.5f, L.worldH * 0.93f, 1.9f * s, Config.CORE_C);
        hud.center(batch, font, "башенная защита, где никто не стреляет",
                L.worldW * 0.5f, L.worldH * 0.862f, 0.62f * s, Config.TEXT_DIM);

        float y = L.worldH * 0.45f;
        String[] rules = {
                "Излучатели не бьют по врагу.",
                "Каждый на своём такте пускает кольцо.",
                "Урон рождается ТОЛЬКО там, где кольца",
                "пересекаются — в узлах резонанса.",
                "",
                "Пара башен даёт линию узлов между ними.",
                "Три кольца в одной точке пробивают броню.",
                "",
                "Дороги нет: враг сам ищет путь к ядру,",
                "а вы этот путь лепите постройками.",
        };
        for (int i = 0; i < rules.length; i++) {
            hud.center(batch, font, rules[i], L.worldW * 0.5f, y - i * 34f * s, 0.6f * s,
                    rules[i].startsWith("Урон") || rules[i].startsWith("пересек")
                            ? Config.NODE_C : Config.TEXT);
        }
        hud.center(batch, font, "ИГРАТЬ", L.worldW * 0.5f, byp + bh * 0.78f, 1.0f * s, Config.CORE_C);
        hud.center(batch, font, "коснитесь экрана", L.worldW * 0.5f, byp - 14f * s, 0.48f * s, Config.TEXT_DIM);
        batch.end();
    }

    private void dim(float a) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.01f, 0.02f, 0.05f, a);
        sr.rect(0, 0, L.worldW, L.worldH);
        sr.end();
    }

    private void drawPause() {
        dim(0.62f);
        float s = L.worldH / 1280f;
        batch.begin();
        hud.center(batch, font, "ПАУЗА", L.worldW * 0.5f, L.worldH * 0.58f, 1.5f * s, Config.TEXT);
        hud.center(batch, font, "нажмите ▶ в правом верхнем углу",
                L.worldW * 0.5f, L.worldH * 0.52f, 0.58f * s, Config.TEXT_DIM);
        batch.end();
    }

    private void drawHelp() {
        dim(0.85f);
        float s = L.worldH / 1280f;
        String[] lines = {
                "КАК ЭТО РАБОТАЕТ",
                "",
                "· ПУЛЬСАР и ФАЗЕР дают кольца каждый такт,",
                "  но ФАЗЕР — со сдвигом на полтакта.",
                "· РЕЗОНАТОР — редкое, но широкое и мощное кольцо.",
                "· БАРЬЕР не излучает: он только гнёт маршрут.",
                "",
                "· Урон есть лишь в точках пересечения колец.",
                "  Две башни дают линию узлов ровно посередине",
                "  между собой — ставьте их так, чтобы эта линия",
                "  легла ВДОЛЬ коридора, по которому идёт враг.",
                "",
                "· Узел, где сошлись три кольца, светится зелёным",
                "  и только он пробивает ПАНЦИРЬ.",
                "· ФАНТОМ уязвим лишь через такт — держите узлы",
                "  над ним подольше.",
                "",
                "· СЕТЬ ограничивает число башен. Расширяется",
                "  волнами и покупкой ГЕНЕРАТОРА.",
                "",
                "коснитесь, чтобы закрыть",
        };
        batch.begin();
        float y = L.worldH * 0.86f;
        for (int i = 0; i < lines.length; i++) {
            Color c = i == 0 ? Config.CORE_C : (i == lines.length - 1 ? Config.TEXT_DIM : Config.TEXT);
            hud.center(batch, font, lines[i], L.worldW * 0.5f, y - i * 36f * s,
                    (i == 0 ? 0.95f : 0.55f) * s, c);
        }
        batch.end();
    }

    private void drawEnd(boolean won) {
        dim(0.75f);
        float s = L.worldH / 1280f;
        batch.begin();
        hud.center(batch, font, won ? "СЕТЬ УСТОЯЛА" : "ЯДРО ПОГАСЛО",
                L.worldW * 0.5f, L.worldH * 0.62f, 1.5f * s, won ? Config.OK_C : Config.BAD_C);
        hud.center(batch, font, "волна " + world.wave + " из " + Config.WAVES_TOTAL,
                L.worldW * 0.5f, L.worldH * 0.555f, 0.7f * s, Config.TEXT);
        hud.center(batch, font, "уничтожено " + world.killed + "   ·   прорвалось " + world.leaked,
                L.worldW * 0.5f, L.worldH * 0.51f, 0.55f * s, Config.TEXT_DIM);
        hud.center(batch, font, "коснитесь, чтобы начать заново",
                L.worldW * 0.5f, L.worldH * 0.42f, 0.62f * s, Config.CORE_C);
        batch.end();
    }

    @Override
    public void dispose() {
        sfx.dispose();
        if (sr != null) sr.dispose();
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
    }

    @Override
    public void pause() {
        if (screen == SC_PLAY) hud.paused = true;
    }
}
