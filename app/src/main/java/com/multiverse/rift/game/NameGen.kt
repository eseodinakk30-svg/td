package com.multiverse.rift.game

import com.multiverse.rift.model.Universe
import kotlin.random.Random

/**
 * Процедурные имена. У каждой вселенной свой словарь, чтобы бойцы
 * читались как выходцы именно оттуда.
 */
object NameGen {

    private val given: Map<Universe, List<String>> = mapOf(
        Universe.NEON to listOf(
            "Кайра", "Вокс", "Нейт", "Сиб", "Ноль", "Дека", "Юки", "Тэн"
        ),
        Universe.ASH to listOf(
            "Кассий", "Мор", "Вэлен", "Драг", "Ирма", "Ковен", "Ульрих", "Сажа"
        ),
        Universe.VERDANT to listOf(
            "Лиан", "Мшава", "Орех", "Виса", "Тайга", "Корень", "Плющ", "Роса"
        ),
        Universe.CHROME to listOf(
            "Аксиом", "Вега", "Керн", "Орбита", "Сигма", "Тихо", "Парсек", "Иней"
        ),
        Universe.EMBER to listOf(
            "Жар", "Кинд", "Сольве", "Уголёк", "Пламень", "Ферро", "Заря", "Копоть"
        ),
        Universe.GLASS to listOf(
            "Призма", "Эхо", "Витраж", "Сколь", "Люмен", "Грань", "Мираж", "Осколок"
        )
    )

    private val epithet: Map<Universe, List<String>> = mapOf(
        Universe.NEON to listOf(
            "с Нижних Ярусов", "Без Лицензии", "из Квартала 9", "Разогнанный", "Полуночник"
        ),
        Universe.ASH to listOf(
            "Пепельный", "Последний из Стражи", "Клятвопреступник", "Обугленный", "Верный Золе"
        ),
        Universe.VERDANT to listOf(
            "Древорождённый", "Хранитель Троп", "Из-под Корней", "Тихоступ", "Носящий Мох"
        ),
        Universe.CHROME to listOf(
            "Безмолвный", "Дрейфующий", "С Дальней Станции", "Отражённый", "Вне Курса"
        ),
        Universe.EMBER to listOf(
            "Горящий", "Из Кузни", "Не Гаснущий", "Раскалённый", "Дитя Печи"
        ),
        Universe.GLASS to listOf(
            "Прозрачный", "Расколотый", "Из Зеркал", "Тонкий", "Сквозной"
        )
    )

    private val enemyRanks = listOf(
        "Эхо", "Двойник", "Отражение", "Тень", "Искажение", "Копия"
    )

    fun hero(universe: Universe, rnd: Random): String {
        val first = given[universe]?.random(rnd) ?: "Странник"
        val last = epithet[universe]?.random(rnd) ?: "из Ниоткуда"
        return "$first $last"
    }

    /** Враги — это искажённые версии обитателей вселенной. */
    fun enemy(universe: Universe, rnd: Random): String {
        val rank = enemyRanks.random(rnd)
        val first = given[universe]?.random(rnd) ?: "Странника"
        return "$rank: $first"
    }
}
