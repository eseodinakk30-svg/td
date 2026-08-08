package com.multiverse.rift.game

import com.multiverse.rift.model.Fighter
import com.multiverse.rift.model.Universe
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Генерация бойцов: и тех, кого призывает игрок, и искажённых эхо,
 * которые встречают его в глубине разлома.
 */
object Roster {

    private class Archetype(
        val role: String,
        val hp: Float,
        val atk: Float,
        val def: Float,
        val spd: Float
    )

    private val archetypes = listOf(
        Archetype("Штурм", hp = 1.15f, atk = 1.15f, def = 0.90f, spd = 0.90f),
        Archetype("Клинок", hp = 0.90f, atk = 1.12f, def = 0.85f, spd = 1.30f),
        Archetype("Бастион", hp = 1.35f, atk = 0.85f, def = 1.40f, spd = 0.80f),
        Archetype("Ловчий", hp = 0.95f, atk = 1.00f, def = 0.95f, spd = 1.20f),
        Archetype("Столп", hp = 1.05f, atk = 1.00f, def = 1.10f, spd = 1.00f)
    )

    private const val BASE_HP = 62f
    private const val BASE_ATK = 12f
    private const val BASE_DEF = 6f
    private const val BASE_SPD = 8f

    /** Разброс ±12 %, чтобы два бойца одного архетипа не были близнецами. */
    private fun jitter(value: Float, rnd: Random): Int {
        val factor = 0.88f + rnd.nextFloat() * 0.24f
        return (value * factor).roundToInt().coerceAtLeast(1)
    }

    private fun build(
        universe: Universe,
        depth: Int,
        rnd: Random,
        hostile: Boolean,
        growth: Float
    ): Fighter {
        val a = archetypes.random(rnd)
        val scale = 1f + growth * (depth - 1)
        val name = if (hostile) NameGen.enemy(universe, rnd) else NameGen.hero(universe, rnd)
        return Fighter(
            name = name,
            universe = universe,
            role = a.role,
            maxHp = jitter(BASE_HP * a.hp * scale, rnd),
            atk = jitter(BASE_ATK * a.atk * scale, rnd),
            def = jitter(BASE_DEF * a.def * scale, rnd),
            spd = jitter(BASE_SPD * a.spd * scale, rnd),
            seed = rnd.nextInt(1, 1_000_000)
        )
    }

    /** Боец, которого предлагают игроку на глубине [depth]. */
    fun summon(depth: Int, rnd: Random, universe: Universe? = null): Fighter =
        build(
            universe = universe ?: Universe.entries.random(rnd),
            depth = depth,
            rnd = rnd,
            hostile = false,
            growth = 0.16f
        )

    /** Три варианта на выбор — всегда из разных вселенных. */
    fun offers(depth: Int, rnd: Random, count: Int = 3): List<Fighter> {
        val pool = Universe.entries.shuffled(rnd).take(count)
        return pool.map { summon(depth, rnd, it) }
    }

    /**
     * Противник. Растёт чуть быстрее игрока — призывы должны оставаться нужными.
     */
    fun enemy(depth: Int, rnd: Random): Fighter {
        val foe = build(
            universe = Universe.entries.random(rnd),
            depth = depth,
            rnd = rnd,
            hostile = true,
            growth = 0.19f
        )
        return foe
    }
}
