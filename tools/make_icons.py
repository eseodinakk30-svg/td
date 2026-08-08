#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Рисует иконку приложения (два пересекающихся кольца + узел резонанса)
без единой внешней библиотеки: свой PNG-энкодер на zlib."""

import math
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "android", "res")
SIZES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
SS = 4  # суперсэмплинг для сглаживания


def write_png(path, w, h, pixels):
    """pixels: bytearray RGBA размером w*h*4"""
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter type None
        raw += pixels[y * w * 4:(y + 1) * w * 4]

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def blend(dst, i, r, g, b, a):
    if a <= 0:
        return
    ia = 1.0 - a
    dst[i] = int(r * a + dst[i] * ia)
    dst[i + 1] = int(g * a + dst[i + 1] * ia)
    dst[i + 2] = int(b * a + dst[i + 2] * ia)
    dst[i + 3] = int(255 * a + dst[i + 3] * ia)


def render(size):
    n = size * SS
    buf = bytearray(n * n * 4)
    cx = cy = n / 2.0
    corner = n * 0.22

    # два излучателя и радиусы их фронтов
    ax, ay = n * 0.34, n * 0.60
    bx, by = n * 0.68, n * 0.44
    ra, rb = n * 0.30, n * 0.26

    for y in range(n):
        for x in range(n):
            i = (y * n + x) * 4
            px, py = x + 0.5, y + 0.5

            # скруглённый квадрат-подложка
            dx = abs(px - cx) - (n / 2.0 - corner)
            dy = abs(py - cy) - (n / 2.0 - corner)
            dx = max(dx, 0.0)
            dy = max(dy, 0.0)
            d = math.hypot(dx, dy) - corner
            if d > 1.0:
                continue
            inside = min(1.0, max(0.0, 0.5 - d))
            # фон: тёмно-синий градиент
            t = py / n
            blend(buf, i, int(8 + 6 * t), int(12 + 10 * t), int(24 + 18 * t), inside)

            # сетка
            if inside > 0.4 and (x % (n // 8) < SS or y % (n // 8) < SS):
                blend(buf, i, 40, 90, 130, 0.16 * inside)

            # кольца
            da = abs(math.hypot(px - ax, py - ay) - ra)
            db = abs(math.hypot(px - bx, py - by) - rb)
            wdt = n * 0.016
            if da < wdt * 3:
                a = max(0.0, 1.0 - da / (wdt * 3)) ** 2
                blend(buf, i, 60, 220, 255, a * 0.95 * inside)
            if db < wdt * 3:
                a = max(0.0, 1.0 - db / (wdt * 3)) ** 2
                blend(buf, i, 255, 90, 200, a * 0.95 * inside)

            # узлы резонанса — там, где окружности пересекаются
            for (nx, ny) in intersections(ax, ay, ra, bx, by, rb):
                dd = math.hypot(px - nx, py - ny)
                rr = n * 0.075
                if dd < rr:
                    a = (1.0 - dd / rr) ** 2
                    blend(buf, i, 255, 250, 210, min(1.0, a * 1.6) * inside)
    return buf


def intersections(x0, y0, r0, x1, y1, r1):
    d = math.hypot(x1 - x0, y1 - y0)
    if d > r0 + r1 or d < abs(r0 - r1) or d == 0:
        return []
    a = (r0 * r0 - r1 * r1 + d * d) / (2 * d)
    h2 = r0 * r0 - a * a
    if h2 < 0:
        return []
    h = math.sqrt(h2)
    xm = x0 + a * (x1 - x0) / d
    ym = y0 + a * (y1 - y0) / d
    rx = -(y1 - y0) * (h / d)
    ry = (x1 - x0) * (h / d)
    return [(xm + rx, ym + ry), (xm - rx, ym - ry)]


def downsample(buf, n, size):
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            r = g = b = a = 0
            for sy in range(SS):
                for sx in range(SS):
                    i = ((y * SS + sy) * n + (x * SS + sx)) * 4
                    r += buf[i]
                    g += buf[i + 1]
                    b += buf[i + 2]
                    a += buf[i + 3]
            k = SS * SS
            o = (y * size + x) * 4
            out[o] = r // k
            out[o + 1] = g // k
            out[o + 2] = b // k
            out[o + 3] = a // k
    return out


def main():
    for dpi, size in SIZES:
        d = os.path.join(OUT, "mipmap-" + dpi)
        os.makedirs(d, exist_ok=True)
        buf = render(size)
        px = downsample(buf, size * SS, size)
        write_png(os.path.join(d, "ic_launcher.png"), size, size, px)
        print("  ✓ mipmap-%s/ic_launcher.png (%dx%d)" % (dpi, size, size))


if __name__ == "__main__":
    main()
