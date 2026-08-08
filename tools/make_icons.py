#!/usr/bin/env python3
"""Generate the launcher icons without any image library.

Everything is rasterised from simple analytic shapes with 3x3 supersampling and
written out as PNG through zlib. Run from the repository root:

    python3 tools/make_icons.py
"""

import math
import os
import struct
import zlib

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res")

SS = 3  # supersampling factor


# --------------------------------------------------------------------- canvas

class Img:
    def __init__(self, size):
        self.n = size
        # premultiplied-free straight RGBA floats
        self.px = [[0.0, 0.0, 0.0, 0.0] for _ in range(size * size)]

    def blend(self, x, y, rgb, a):
        if a <= 0:
            return
        p = self.px[y * self.n + x]
        na = a + p[3] * (1 - a)
        if na <= 0:
            return
        for i in range(3):
            p[i] = (rgb[i] * a + p[i] * p[3] * (1 - a)) / na
        p[3] = na

    def fill(self, shape, color, bbox=None, alpha=1.0):
        n = self.n
        x0, y0, x1, y1 = bbox if bbox else (0, 0, 1, 1)
        px0 = max(0, int(x0 * n) - 2)
        py0 = max(0, int(y0 * n) - 2)
        px1 = min(n, int(x1 * n) + 2)
        py1 = min(n, int(y1 * n) + 2)
        step = 1.0 / (n * SS)
        for py in range(py0, py1):
            for px in range(px0, px1):
                hits = 0
                for sy in range(SS):
                    v = (py + (sy + 0.5) / SS) / n
                    for sx in range(SS):
                        u = (px + (sx + 0.5) / SS) / n
                        if shape(u, v):
                            hits += 1
                if hits:
                    cov = hits / float(SS * SS)
                    col = color(px / float(n), py / float(n)) if callable(color) else color
                    self.blend(px, py, col, cov * alpha)
        del step

    def to_png(self, path):
        n = self.n
        raw = bytearray()
        for y in range(n):
            raw.append(0)
            for x in range(n):
                r, g, b, a = self.px[y * n + x]
                raw += bytes((clamp8(r), clamp8(g), clamp8(b), clamp8(a)))
        write_png(path, n, n, bytes(raw))


def clamp8(v):
    i = int(round(v * 255))
    return 0 if i < 0 else (255 if i > 255 else i)


def write_png(path, w, h, raw):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    hdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", hdr)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# --------------------------------------------------------------------- shapes

def rgb(h):
    return ((h >> 16 & 255) / 255.0, (h >> 8 & 255) / 255.0, (h & 255) / 255.0)


def round_rect(x0, y0, x1, y1, r):
    def f(u, v):
        if u < x0 or u > x1 or v < y0 or v > y1:
            return False
        cx = min(max(u, x0 + r), x1 - r)
        cy = min(max(v, y0 + r), y1 - r)
        return (u - cx) ** 2 + (v - cy) ** 2 <= r * r
    return f


def circle(cx, cy, r):
    return lambda u, v: (u - cx) ** 2 + (v - cy) ** 2 <= r * r


def poly(points):
    def f(u, v):
        inside = False
        j = len(points) - 1
        for i in range(len(points)):
            xi, yi = points[i]
            xj, yj = points[j]
            if (yi > v) != (yj > v):
                if u < (xj - xi) * (v - yi) / (yj - yi) + xi:
                    inside = not inside
            j = i
        return inside
    return f


def thick_line(x0, y0, x1, y1, w):
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    nx, ny = -dy / ln * w / 2, dx / ln * w / 2
    return poly([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny),
                 (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])


def vgrad(top, bottom):
    a, b = rgb(top), rgb(bottom)
    return lambda u, v: tuple(a[i] + (b[i] - a[i]) * v for i in range(3))


# ---------------------------------------------------------------- composition

def draw_background(img, rounded):
    if rounded:
        shape = round_rect(0.02, 0.02, 0.98, 0.98, 0.22)
    else:
        shape = lambda u, v: True  # noqa: E731  (adaptive icons get a full bleed)
    img.fill(shape, vgrad(0x1D3E5E, 0x0A1220))
    # soft cyan glow
    img.fill(circle(0.5, 0.42, 0.36), rgb(0x2E7FA8), alpha=0.35,
             bbox=(0.14, 0.06, 0.86, 0.78))
    img.fill(circle(0.5, 0.42, 0.24), rgb(0x3FD0FF), alpha=0.18,
             bbox=(0.26, 0.18, 0.74, 0.66))


def draw_tower(img, s=1.0, oy=0.0):
    """Tower artwork in unit space, optionally scaled about the centre."""
    def T(p):
        return (0.5 + (p[0] - 0.5) * s, 0.5 + (p[1] - 0.5) * s + oy)

    def tp(points):
        return poly([T(p) for p in points])

    def tc(cx, cy, r):
        c = T((cx, cy))
        return circle(c[0], c[1], r * s)

    def tl(x0, y0, x1, y1, w):
        a, b = T((x0, y0)), T((x1, y1))
        return thick_line(a[0], a[1], b[0], b[1], w * s)

    shadow = rgb(0x0A1018)
    body = rgb(0x3FD0FF)
    body_dark = rgb(0x2494C0)
    gold = rgb(0xFFC857)

    # ground shadow
    img.fill(tc(0.5, 0.845, 0.245), shadow, alpha=0.45)

    # base plinth
    img.fill(tp([(0.26, 0.86), (0.74, 0.86), (0.68, 0.79), (0.32, 0.79)]), body_dark)
    # main body (tapered)
    img.fill(tp([(0.34, 0.80), (0.66, 0.80), (0.615, 0.45), (0.385, 0.45)]), body)
    # shading on the right flank
    img.fill(tp([(0.53, 0.80), (0.66, 0.80), (0.615, 0.45), (0.53, 0.45)]), body_dark,
             alpha=0.45)
    # crenellated top
    img.fill(tp([(0.32, 0.47), (0.68, 0.47), (0.68, 0.39), (0.32, 0.39)]), body)
    for x in (0.32, 0.44, 0.56):
        img.fill(tp([(x, 0.39), (x + 0.12, 0.39), (x + 0.12, 0.32), (x, 0.32)]), body)
        img.fill(tp([(x + 0.085, 0.39), (x + 0.12, 0.39),
                     (x + 0.12, 0.32), (x + 0.085, 0.32)]), body_dark, alpha=0.5)
    # window
    img.fill(tp([(0.455, 0.72), (0.545, 0.72), (0.545, 0.58), (0.455, 0.58)]),
             rgb(0x0C1A26))
    img.fill(tc(0.5, 0.58, 0.045), rgb(0x0C1A26))

    # cannon barrel aiming up-right
    img.fill(tl(0.50, 0.55, 0.80, 0.28, 0.115), gold)
    img.fill(tc(0.80, 0.28, 0.072), gold)
    img.fill(tc(0.80, 0.28, 0.032), rgb(0xFFF0C0))
    img.fill(tc(0.50, 0.55, 0.085), rgb(0xE0A93C))


def render(size, rounded=True, foreground_only=False, background_only=False):
    img = Img(size)
    if not foreground_only:
        draw_background(img, rounded)
    if not background_only:
        # adaptive foregrounds must stay inside the 66/108 safe zone
        draw_tower(img, 0.62 if foreground_only else 1.0, 0.0)
    return img


def main():
    legacy = [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
              ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]
    for folder, size in legacy:
        path = os.path.join(RES, folder, "ic_launcher.png")
        render(size, rounded=True).to_png(path)
        print("wrote", path, size)

    fg = os.path.join(RES, "drawable-nodpi", "ic_launcher_fg.png")
    bg = os.path.join(RES, "drawable-nodpi", "ic_launcher_bg.png")
    render(216, rounded=False, foreground_only=True).to_png(fg)
    render(216, rounded=False, background_only=True).to_png(bg)
    print("wrote adaptive layers")


if __name__ == "__main__":
    main()
