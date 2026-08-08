package com.resonance.td;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

/**
 * Служебный прогон для снятия скриншотов без участия человека:
 * сам расставляет башни, запускает волну и сохраняет кадры в PNG.
 * Нужен только при разработке, в APK не попадает.
 */
public class DevShots implements ApplicationListener {

    private final ResonanceGame game = new ResonanceGame();
    private final String dir;
    private float t;
    private int step;

    public DevShots(String dir) {
        this.dir = dir;
    }

    @Override
    public void create() {
        game.create();
    }

    @Override
    public void resize(int w, int h) {
        game.resize(w, h);
    }

    @Override
    public void render() {
        t += 1f / 60f;
        script();
        game.render();
        shots();
    }

    private void script() {
        if (step == 0 && t > 0.6f) {                    // заставку сняли — в бой
            step = 1;
            game.screen = ResonanceGame.SC_PLAY;
            World w = game.world;
            w.energy = 2000;
            w.gensBought = 8;
            w.build(Config.T_PULSAR, 4, 3);
            w.build(Config.T_PHASER, 6, 3);
            w.build(Config.T_PULSAR, 3, 8);
            w.build(Config.T_PHASER, 7, 8);
            w.build(Config.T_RESONATOR, 5, 6);
            w.build(Config.T_WALL, 4, 10);
            w.build(Config.T_WALL, 6, 10);
            w.build(Config.T_PULSAR, 4, 12);
            w.build(Config.T_PHASER, 6, 12);
            w.callWaveNow();
        }
        if (step == 2 && t > 5.2f) {                    // режим стройки с превью
            step = 3;
            game.hud.buildType = Config.T_PULSAR;
            game.hoverX = 5;
            game.hoverY = 9;
        }
        if (step == 4 && t > 8.4f) {
            step = 5;
            game.hud.buildType = -1;
            game.hoverX = game.hoverY = -1;
            game.hud.selected = game.world.towers.get(0);
        }
        if (step == 6 && t > 11.2f) {
            step = 7;
            game.hud.selected = null;
            game.hud.showHelp = true;
        }
    }

    private void shots() {
        if (step == 0 && t > 0.45f) shot("01-menu");
        if (step == 1 && t > 4.6f) { shot("02-battle"); step = 2; }
        if (step == 3 && t > 6.2f) { shot("03-build-preview"); step = 4; }
        if (step == 5 && t > 9.6f) { shot("04-tower-selected"); step = 6; }
        if (step == 7 && t > 11.6f) {
            shot("05-help");
            Gdx.app.exit();
        }
    }

    private void shot(String name) {
        Pixmap p = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(Gdx.files.absolute(dir + "/" + name + ".png"), p, -1, true);
        p.dispose();
        System.out.println("shot: " + name);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        game.dispose();
    }
}
