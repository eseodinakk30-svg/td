#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Докладывает в APK, собранный aapt2, всё остальное:
   classes*.dex, lib/<abi>/libgdx.so и не-class ресурсы из jar-ов libGDX
   (там лежит дефолтный шрифт и шейдеры, которые libGDX читает как classpath)."""

import argparse
import os
import shutil
import zipfile

ABIS = ["armeabi-v7a", "arm64-v8a", "x86", "x86_64"]
JAR_RESOURCE_SOURCES = ["gdx.jar", "gdx-backend-android.jar"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--dex-dir", required=True)
    ap.add_argument("--libs", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    if os.path.exists(a.out):
        os.remove(a.out)
    shutil.copyfile(a.base, a.out)

    added = []
    with zipfile.ZipFile(a.out, "a", zipfile.ZIP_DEFLATED) as z:
        existing = set(z.namelist())

        # --- dex ---
        for name in sorted(os.listdir(a.dex_dir)):
            if name.endswith(".dex"):
                z.write(os.path.join(a.dex_dir, name), name)
                added.append(name)

        # --- нативные библиотеки ---
        for abi in ABIS:
            jar = os.path.join(a.libs, "natives-%s.jar" % abi)
            if not os.path.exists(jar):
                continue
            with zipfile.ZipFile(jar) as nz:
                for n in nz.namelist():
                    if n.endswith(".so"):
                        target = "lib/%s/%s" % (abi, os.path.basename(n))
                        z.writestr(target, nz.read(n))
                        added.append(target)

        # --- ресурсы из jar-ов (шрифт по умолчанию, шейдеры) ---
        for jar_name in JAR_RESOURCE_SOURCES:
            jar = os.path.join(a.libs, jar_name)
            if not os.path.exists(jar):
                continue
            with zipfile.ZipFile(jar) as jz:
                for n in jz.namelist():
                    if n.endswith("/") or n.endswith(".class"):
                        continue
                    if n.startswith("META-INF/") or n.endswith(".rl") or n.endswith(".gwt.xml"):
                        continue
                    if n in existing:
                        continue
                    existing.add(n)
                    z.writestr(n, jz.read(n))
                    added.append(n)

    print("  · добавлено записей: %d" % len(added))
    for n in added:
        if n.endswith(".dex") or n.endswith(".so"):
            print("      %s" % n)


if __name__ == "__main__":
    main()
