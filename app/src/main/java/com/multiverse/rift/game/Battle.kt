package com.multiverse.rift.game

import com.multiverse.rift.model.Ability
import com.multiverse.rift.model.Fighter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class PopupKind { DAMAGE, CRIT, HEAL, SHIELD, MISS, BURN }

/** Всплывающее число над бойцом. Рендер сам решает, каким цветом его рисовать. */
class Popup(val onEnemy: Boolean, val text: String, val kind: PopupKind)

enum class PlayerAction { ATTACK, CAST, FOCUS }

private class Hit(val damage: Int, val crit: Boolean, val missed: Boolean)

/**
 * Пошаговый бой: активный боец отряда против эхо из разлома.
 * Раунд — это действие игрока, ответ врага и тик эффектов.
 */
class Battle(
    val squad: List<Fighter>,
    val enemy: Fighter,
    val depth: Int,
    private val rnd: Random
) {
    var activeIndex: Int = max(0, squad.indexOfFirst { it.alive })
        private set

    var over: Boolean = false
        private set

    var victory: Boolean = false
        private set

    var round: Int = 1
        private set

    val log = ArrayList<String>()
    val popups = ArrayList<Popup>()

    val active: Fighter
        get() = squad[activeIndex]

    init {
        say("Глубина $depth. Навстречу выходит ${enemy.name}.")
    }

    private fun say(line: String) {
        log.add(line)
        while (log.size > LOG_LIMIT) log.removeAt(0)
    }

    private fun pop(onEnemy: Boolean, text: String, kind: PopupKind) {
        popups.add(Popup(onEnemy, text, kind))
    }

    /** Рендер забирает накопившиеся всплывашки и очищает очередь. */
    fun drainPopups(): List<Popup> {
        if (popups.isEmpty()) return emptyList()
        val out = ArrayList(popups)
        popups.clear()
        return out
    }

    // --- Математика ---

    private fun roll(attacker: Fighter, defender: Fighter, power: Float): Hit {
        if (defender.evading) {
            defender.setEvading(false)
            return Hit(0, crit = false, missed = true)
        }
        val base = attacker.atk * power
        val spread = base * 0.18f
        var dmg = base - spread + rnd.nextFloat() * spread * 2f
        dmg -= defender.def * 0.55f
        val critChance = 0.06f + attacker.spd * 0.006f
        val crit = rnd.nextFloat() < critChance
        if (crit) dmg *= 1.7f
        return Hit(max(1, dmg.roundToInt()), crit, missed = false)
    }

    private fun strike(
        attacker: Fighter,
        defender: Fighter,
        power: Float,
        attackerIsPlayer: Boolean
    ) {
        val hit = roll(attacker, defender, power)
        val onEnemy = attackerIsPlayer
        if (hit.missed) {
            pop(onEnemy, "мимо", PopupKind.MISS)
            say("${defender.name} уходит в фазу — атака проходит сквозь.")
            if (defender.universe.ability == Ability.PHASE) {
                val counter = roll(defender, attacker, 0.6f)
                if (!counter.missed) {
                    attacker.takeDamage(counter.damage)
                    pop(!onEnemy, "-${counter.damage}", PopupKind.DAMAGE)
                    say("Контрудар: ${counter.damage}.")
                }
            }
            return
        }
        defender.takeDamage(hit.damage)
        pop(onEnemy, "-${hit.damage}", if (hit.crit) PopupKind.CRIT else PopupKind.DAMAGE)
        val suffix = if (hit.crit) " Критично!" else ""
        say("${attacker.name} бьёт на ${hit.damage}.$suffix")
    }

    private fun cast(caster: Fighter, target: Fighter, casterIsPlayer: Boolean) {
        // Всплывашки о самом кастере рисуются на его стороне экрана.
        val casterSide = !casterIsPlayer
        caster.spendEnergy(caster.ability.cost)
        when (caster.ability) {
            Ability.OVERCLOCK -> {
                say("${caster.name}: Разгон.")
                strike(caster, target, 0.62f, casterIsPlayer)
                if (target.alive) strike(caster, target, 0.62f, casterIsPlayer)
            }

            Ability.BLOOD_OATH -> {
                val toll = max(1, caster.maxHp / 12)
                caster.drain(toll)
                pop(casterSide, "-$toll", PopupKind.BURN)
                say("${caster.name}: Клятва крови (-$toll себе).")
                strike(caster, target, 1.75f, casterIsPlayer)
            }

            Ability.BLOOM -> {
                val healed = caster.heal(max(6, caster.maxHp / 4))
                caster.clearBurn()
                pop(casterSide, "+$healed", PopupKind.HEAL)
                say("${caster.name}: Цветение (+$healed).")
            }

            Ability.AEGIS -> {
                val amount = max(6, caster.def * 3)
                caster.addShield(amount)
                pop(casterSide, "+$amount", PopupKind.SHIELD)
                say("${caster.name}: Эгида (щит $amount).")
            }

            Ability.CINDER -> {
                say("${caster.name}: Взрыв углей.")
                strike(caster, target, 0.95f, casterIsPlayer)
                if (target.alive) {
                    target.igniteFor(3)
                    say("${target.name} горит.")
                }
            }

            Ability.PHASE -> {
                caster.setEvading(true)
                pop(casterSide, "фаза", PopupKind.SHIELD)
                say("${caster.name}: Фазовый сдвиг.")
            }
        }
    }

    // --- Ход игрока ---

    fun canSwapTo(index: Int): Boolean =
        !over && index != activeIndex && index in squad.indices && squad[index].alive

    fun swapTo(index: Int) {
        if (!canSwapTo(index)) return
        activeIndex = index
        say("В разлом шагает ${active.name}.")
        enemyTurn()
        endRound()
    }

    fun act(action: PlayerAction) {
        if (over) return
        val me = active
        when (action) {
            PlayerAction.ATTACK -> strike(me, enemy, 1f, attackerIsPlayer = true)

            PlayerAction.CAST -> {
                if (!me.canCast()) {
                    say("Не хватает энергии.")
                    return
                }
                cast(me, enemy, casterIsPlayer = true)
            }

            PlayerAction.FOCUS -> {
                me.gainEnergy(2)
                val guard = max(3, me.def * 2)
                me.addShield(guard)
                pop(false, "+$guard", PopupKind.SHIELD)
                say("${me.name} сосредотачивается: +2 энергии, щит $guard.")
            }
        }

        if (!enemy.alive) {
            finish(playerWon = true)
            return
        }
        enemyTurn()
        endRound()
    }

    // --- Ход врага ---

    private fun enemyTurn() {
        if (over || !enemy.alive) return
        val target = active
        if (!target.alive) return

        if (enemy.canCast() && wantsToCast()) {
            cast(enemy, target, casterIsPlayer = false)
        } else {
            strike(enemy, target, 1f, attackerIsPlayer = false)
        }

        if (!target.alive) {
            say("${target.name} падает.")
            val next = squad.indexOfFirst { it.alive }
            if (next < 0) {
                finish(playerWon = false)
            } else {
                activeIndex = next
                say("Место занимает ${active.name}.")
            }
        }
    }

    private fun wantsToCast(): Boolean = when (enemy.ability) {
        Ability.BLOOM -> enemy.hpFraction < 0.55f
        Ability.AEGIS -> enemy.shield == 0 && enemy.hpFraction < 0.85f
        Ability.PHASE -> !enemy.evading && rnd.nextFloat() < 0.5f
        else -> rnd.nextFloat() < 0.65f
    }

    // --- Конец раунда ---

    private fun endRound() {
        if (over) return

        applyBurn(enemy, onEnemy = true)
        if (!enemy.alive) {
            finish(playerWon = true)
            return
        }

        val me = active
        applyBurn(me, onEnemy = false)
        if (!me.alive) {
            say("${me.name} падает.")
            val next = squad.indexOfFirst { it.alive }
            if (next < 0) {
                finish(playerWon = false)
                return
            }
            activeIndex = next
        }

        active.gainEnergy(1)
        enemy.gainEnergy(1)
        round++
    }

    private fun applyBurn(f: Fighter, onEnemy: Boolean) {
        if (f.burn <= 0) return
        val tick = max(2, f.maxHp / 22)
        f.drain(tick)
        f.tickBurn()
        pop(onEnemy, "-$tick", PopupKind.BURN)
    }

    private fun finish(playerWon: Boolean) {
        over = true
        victory = playerWon
        say(if (playerWon) "Эхо рассыпается. Разлом уходит глубже." else "Отряд не выстоял.")
    }

    companion object {
        const val LOG_LIMIT = 6
    }
}
