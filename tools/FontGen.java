// Генератор растрового шрифта (AngelCode .fnt + .png) для libGDX.
// Запуск: java tools/FontGen.java <выходная-папка>
// Берёт системный DejaVu Sans (кириллица + латиница) и печёт атлас.
// Нужен только на машине сборки — в APK попадают уже готовые .fnt/.png.

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class FontGen {

    static final int SIZE = 34;          // кегль в пикселях
    static final int ATLAS = 512;
    static final int PAD = 2;

    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : ".");
        outDir.mkdirs();

        String[] candidates = {
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        };
        File ttf = null;
        for (String c : candidates) {
            if (new File(c).exists()) { ttf = new File(c); break; }
        }
        if (ttf == null) throw new IOException("не найден TTF со кириллицей");
        System.out.println("  шрифт: " + ttf);

        Font font = Font.createFont(Font.TRUETYPE_FONT, ttf).deriveFont((float) SIZE);

        StringBuilder chars = new StringBuilder();
        for (char c = 32; c <= 126; c++) chars.append(c);
        for (char c = 0x410; c <= 0x44F; c++) chars.append(c);   // А-я
        chars.append('Ё').append('ё');                 // Ё ё
        chars.append('×').append('→').append('↑') // × → ↑
             .append('●').append('—').append('·') // ● — ·
             .append('✦').append('▶').append('✖');// ✦ ▶ ✖

        BufferedImage atlas = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setFont(font);
        g.setColor(Color.WHITE);

        FontMetrics fm = g.getFontMetrics();
        int ascent = fm.getAscent();
        int lineHeight = fm.getHeight();

        List<String> lines = new ArrayList<String>();
        int penX = PAD, penY = PAD, rowH = 0;

        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), String.valueOf(c));
            Rectangle bounds = gv.getPixelBounds(g.getFontRenderContext(), 0, 0);
            Rectangle2D adv = gv.getLogicalBounds();
            int xadvance = (int) Math.round(adv.getWidth());

            int w = Math.max(bounds.width, 0);
            int h = Math.max(bounds.height, 0);

            if (w == 0 || h == 0) { // пробел и прочие невидимки
                lines.add(String.format(
                    "char id=%d x=0 y=0 width=0 height=0 xoffset=0 yoffset=0 xadvance=%d page=0 chnl=15",
                    (int) c, xadvance));
                continue;
            }
            if (penX + w + PAD > ATLAS) { penX = PAD; penY += rowH + PAD; rowH = 0; }
            if (penY + h + PAD > ATLAS) throw new IOException("атлас переполнен, увеличь ATLAS");

            g.drawGlyphVector(gv, penX - bounds.x, penY - bounds.y);

            lines.add(String.format(
                "char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15",
                (int) c, penX, penY, w, h, bounds.x, ascent + bounds.y, xadvance));

            penX += w + PAD;
            rowH = Math.max(rowH, h);
        }
        g.dispose();

        ImageIO.write(atlas, "png", new File(outDir, "font.png"));

        PrintWriter out = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(new File(outDir, "font.fnt")), "UTF-8"));
        out.printf("info face=\"DejaVuSans\" size=%d bold=1 italic=0 charset=\"\" unicode=1 "
                + "stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=%d,%d%n", SIZE, PAD, PAD);
        out.printf("common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0%n",
                lineHeight, ascent, ATLAS, ATLAS);
        out.printf("page id=0 file=\"font.png\"%n");
        out.printf("chars count=%d%n", lines.size());
        for (String l : lines) out.println(l);
        out.printf("kernings count=0%n");
        out.close();

        System.out.println("  ✓ font.png / font.fnt (" + lines.size() + " глифов, кегль " + SIZE + ")");
    }
}
