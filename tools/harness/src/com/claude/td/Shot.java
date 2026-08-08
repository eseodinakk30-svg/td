package com.claude.td;

import android.content.Context;
import android.graphics.Canvas;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Renders the game's own draw calls through Java2D so the layout can be
 * inspected as PNGs without a device. Output goes to build/shots/.
 */
public final class Shot {

    private static final int W = 1080;
    private static final int H = 2160;
    private static final float DT = 1f / 60f;

    public static void main(String[] args) throws Exception {
        Ui.DP = 3f;
        Game g = new Game(new Save(new Context()));
        Hud hud = new Hud(g);
        hud.layout(W, H);
        g.resize(W, H, hud.topInset(), hud.bottomInset());

        File dir = new File("build/shots");
        dir.mkdirs();

        // 1. main menu
        for (int i = 0; i < 120; i++) hud.draw(new Canvas(), DT);
        shot(g, hud, new File(dir, "1-menu.png"));

        // 2. early game with the build bar
        g.startLevel(0);
        run(g, hud, 6f, false);
        g.buildType = Tower.ARROW;
        g.hoverC = 2;
        g.hoverR = 3;
        shot(g, hud, new File(dir, "2-build.png"));

        // 3. a busy mid-game wave
        g.buildType = -1;
        g.hoverC = -1;
        run(g, hud, 150f, true);
        shot(g, hud, new File(dir, "3-battle.png"));

        // 4. a selected tower showing the upgrade panel
        if (!g.towers.isEmpty()) g.selected = g.towers.get(g.towers.size() / 2);
        shot(g, hud, new File(dir, "4-selected.png"));

        // 5. late game
        g.selected = null;
        run(g, hud, 420f, true);
        shot(g, hud, new File(dir, "5-late.png"));

        // 6. defeat overlay
        g.state = Game.GAME_OVER;
        shot(g, hud, new File(dir, "6-defeat.png"));

        System.out.println("shots written to " + dir.getAbsolutePath());
    }

    private static void run(Game g, Hud hud, float seconds, boolean bot) {
        Canvas blind = new Canvas();
        int frames = (int) (seconds * 60);
        for (int i = 0; i < frames && g.state == Game.PLAYING; i++) {
            g.update(DT);
            if (bot) Harness.playOneStep(g);
            hud.draw(blind, DT);
            g.drawWorld(blind);
        }
    }

    private static void shot(Game g, Hud hud, File out) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        Canvas c = new Canvas(g2);

        c.drawColor(Ui.BG);
        if (g.level != null && g.state != Game.MENU) g.drawWorld(c);
        hud.draw(c, DT);
        g2.dispose();

        // downscale: the raw 1080x2160 frame is bigger than it needs to be
        BufferedImage small = new BufferedImage(W / 2, H / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D s2 = small.createGraphics();
        s2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        s2.drawImage(img, 0, 0, W / 2, H / 2, null);
        s2.dispose();

        ImageIO.write(small, "png", out);
        System.out.println("wrote " + out);
    }
}
