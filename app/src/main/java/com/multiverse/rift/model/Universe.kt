package com.multiverse.rift.model

import android.graphics.Color

/**
 * Вселенная, из которой приходит боец. Задаёт палитру, силуэт и способность.
 */
enum class Universe(
    val title: String,
    val motto: String,
    val trait: String,
    val ability: Ability,
    val primary: Int,
    val secondary: Int,
    val deep: Int
) {
    NEON(
        title = "Неоновый Спрол",
        motto = "Город не спал уже сто лет",
        trait = "Скорость",
        ability = Ability.OVERCLOCK,
        primary = Color.rgb(0x22, 0xE8, 0xE2),
        secondary = Color.rgb(0xFF, 0x3D, 0xBD),
        deep = Color.rgb(0x07, 0x1B, 0x2C)
    ),
    ASH(
        title = "Пепельная Империя",
        motto = "Троны стоят на золе",
        trait = "Ярость",
        ability = Ability.BLOOD_OATH,
        primary = Color.rgb(0xE8, 0x4A, 0x44),
        secondary = Color.rgb(0xF2, 0xC4, 0x5A),
        deep = Color.rgb(0x26, 0x0D, 0x0D)
    ),
    VERDANT(
        title = "Изумрудная Глубь",
        motto = "Корни помнят каждую войну",
        trait = "Регенерация",
        ability = Ability.BLOOM,
        primary = Color.rgb(0x4A, 0xD9, 0x91),
        secondary = Color.rgb(0xC8, 0xF2, 0x6E),
        deep = Color.rgb(0x08, 0x24, 0x1D)
    ),
    CHROME(
        title = "Хромовая Пустота",
        motto = "Тишина между звёздами",
        trait = "Щиты",
        ability = Ability.AEGIS,
        primary = Color.rgb(0x8F, 0xB6, 0xFF),
        secondary = Color.rgb(0xDD, 0xE7, 0xF7),
        deep = Color.rgb(0x0C, 0x15, 0x2B)
    ),
    EMBER(
        title = "Двор Углей",
        motto = "Огонь не просит разрешения",
        trait = "Горение",
        ability = Ability.CINDER,
        primary = Color.rgb(0xFF, 0x8A, 0x2B),
        secondary = Color.rgb(0xFF, 0xD5, 0x4A),
        deep = Color.rgb(0x2C, 0x12, 0x05)
    ),
    GLASS(
        title = "Стеклянный Реквием",
        motto = "Каждое отражение — это дверь",
        trait = "Фазы",
        ability = Ability.PHASE,
        primary = Color.rgb(0xB9, 0x8A, 0xFF),
        secondary = Color.rgb(0xEC, 0xE2, 0xFF),
        deep = Color.rgb(0x1B, 0x11, 0x30)
    )
}
