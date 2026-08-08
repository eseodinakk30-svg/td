package com.multiverse.rift.model

/**
 * Особая способность бойца. Привязана к вселенной, из которой он пришёл.
 */
enum class Ability(
    val title: String,
    val description: String,
    val cost: Int
) {
    OVERCLOCK(
        title = "Разгон",
        description = "Два удара подряд, повышенный шанс крита",
        cost = 3
    ),
    BLOOD_OATH(
        title = "Клятва крови",
        description = "Тяжёлый удар ценой части своего здоровья",
        cost = 3
    ),
    BLOOM(
        title = "Цветение",
        description = "Лечит и снимает горение",
        cost = 2
    ),
    AEGIS(
        title = "Эгида",
        description = "Щит, поглощающий урон",
        cost = 2
    ),
    CINDER(
        title = "Взрыв углей",
        description = "Урон и поджог на 3 хода",
        cost = 3
    ),
    PHASE(
        title = "Фазовый сдвиг",
        description = "Уклонение от следующей атаки и контрудар",
        cost = 2
    )
}
