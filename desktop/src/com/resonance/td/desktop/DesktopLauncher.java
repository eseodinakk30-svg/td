package com.resonance.td.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.resonance.td.ResonanceGame;

/**
 * Запуск на десктопе — для разработки и снятия скриншотов.
 * В APK не попадает: build.sh компилирует только core/ и android/.
 */
public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Резонанс TD");
        cfg.setWindowedMode(540, 960);
        cfg.setForegroundFPS(60);
        cfg.useVsync(true);
        cfg.setBackBufferConfig(8, 8, 8, 8, 0, 0, 2);

        if (args.length >= 2 && "--shots".equals(args[0])) {
            new Lwjgl3Application(new com.resonance.td.DevShots(args[1]), cfg);
            return;
        }
        new Lwjgl3Application(new ResonanceGame(), cfg);
    }
}
