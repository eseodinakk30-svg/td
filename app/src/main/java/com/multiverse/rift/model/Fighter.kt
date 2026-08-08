package com.multiverse.rift.model

import kotlin.math.max
import kotlin.math.min

/**
 * Боец: персонаж, вытащенный из схлопывающейся вселенной.
 */
class Fighter(
    val name: String,
    val universe: Universe,
    val role: String,
    val maxHp: Int,
    val atk: Int,
    val def: Int,
    val spd: Int,
    val seed: Int
) {
    var hp: Int = maxHp
        private set

    var energy: Int = START_ENERGY
        private set

    var shield: Int = 0
        private set

    /** Сколько ходов ещё горит. */
    var burn: Int = 0
        private set

    /** Уклоняется от следующей атаки. */
    var evading: Boolean = false
        private set

    val ability: Ability
        get() = universe.ability

    val alive: Boolean
        get() = hp > 0

    val hpFraction: Float
        get() = hp.toFloat() / maxHp.toFloat()

    /** Грубая оценка силы — для сортировки предложений и отображения. */
    val power: Int
        get() = maxHp / 4 + atk * 2 + def + spd

    fun canCast(): Boolean = energy >= ability.cost

    fun spendEnergy(amount: Int) {
        energy = max(0, energy - amount)
    }

    fun gainEnergy(amount: Int) {
        energy = min(MAX_ENERGY, energy + amount)
    }

    fun addShield(amount: Int) {
        shield += amount
    }

    fun setEvading(value: Boolean) {
        evading = value
    }

    fun igniteFor(turns: Int) {
        burn = max(burn, turns)
    }

    fun clearBurn() {
        burn = 0
    }

    fun tickBurn() {
        if (burn > 0) burn--
    }

    fun heal(amount: Int): Int {
        val before = hp
        hp = min(maxHp, hp + amount)
        return hp - before
    }

    /** Прямой урон в обход щита (горение, откат способностей). */
    fun drain(amount: Int) {
        hp = max(0, hp - amount)
    }

    /**
     * Урон с учётом щита. Возвращает, сколько дошло до здоровья.
     */
    fun takeDamage(amount: Int): Int {
        var remaining = amount
        if (shield > 0) {
            val absorbed = min(shield, remaining)
            shield -= absorbed
            remaining -= absorbed
        }
        hp = max(0, hp - remaining)
        return remaining
    }

    /** Полное восстановление — новый забег. */
    fun restore() {
        hp = maxHp
        energy = START_ENERGY
        shield = 0
        burn = 0
        evading = false
    }

    /** Между боями: выжившие подлечиваются, павшие возвращаются ослабленными. */
    fun recoverBetweenBattles() {
        hp = if (hp > 0) {
            min(maxHp, hp + maxHp / 4)
        } else {
            max(1, (maxHp * 35) / 100)
        }
        energy = START_ENERGY
        shield = 0
        burn = 0
        evading = false
    }

    companion object {
        const val START_ENERGY = 2
        const val MAX_ENERGY = 6
    }
}
