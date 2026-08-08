package com.multiverse.rift.render

import android.graphics.Color
import android.graphics.Typeface

/** Палитра и шрифты интерфейса. */
object Theme {
    val BG = Color.rgb(0x06, 0x07, 0x0F)
    val BG_HI = Color.rgb(0x11, 0x0D, 0x22)
    val PANEL = Color.rgb(0x13, 0x15, 0x24)
    val PANEL_HI = Color.rgb(0x1D, 0x21, 0x36)
    val EDGE = Color.rgb(0x2C, 0x31, 0x4A)

    val TEXT = Color.rgb(0xF1, 0xF3, 0xF9)
    val TEXT_DIM = Color.rgb(0x8D, 0x96, 0xB2)
    val TEXT_FAINT = Color.rgb(0x5A, 0x62, 0x7C)

    val HP = Color.rgb(0x54, 0xD1, 0x7B)
    val HP_MID = Color.rgb(0xE8, 0xC4, 0x4A)
    val HP_LOW = Color.rgb(0xE5, 0x53, 0x4A)
    val ENERGY = Color.rgb(0x62, 0xC4, 0xFF)
    val SHIELD = Color.rgb(0xC6, 0xDD, 0xF2)
    val CRIT = Color.rgb(0xFF, 0xD5, 0x4A)
    val BURN = Color.rgb(0xFF, 0x84, 0x2B)
    val HEAL = Color.rgb(0x74, 0xE7, 0x97)
    val DANGER = Color.rgb(0xE0, 0x4A, 0x4A)

    val display: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val bodyBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    /** Цвет полосы здоровья по остатку. */
    fun hpColor(fraction: Float): Int = when {
        fraction > 0.55f -> HP
        fraction > 0.25f -> HP_MID
        else -> HP_LOW
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    /** Линейная интерполяция между двумя цветами. */
    fun mix(a: Int, b: Int, k: Float): Int {
        val f = k.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }
}
