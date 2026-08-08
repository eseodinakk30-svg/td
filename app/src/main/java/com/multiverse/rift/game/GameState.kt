package com.multiverse.rift.game

import com.multiverse.rift.model.Fighter
import com.multiverse.rift.model.Universe
import kotlin.math.max
import kotlin.random.Random

enum class Screen { TITLE, SUMMON, CAMP, BATTLE, DEFEAT }

/**
 * Поток игры: заголовок → призыв → лагерь → бой → снова призыв.
 * Забег заканчивается, когда падает весь отряд.
 */
class GameState(
    initialBest: Int = 0,
    private val onNewBest: (Int) -> Unit = {}
) {
    val rnd: Random = Random(System.nanoTime())

    var screen: Screen = Screen.TITLE
        private set

    val squad = ArrayList<Fighter>()

    var depth: Int = 1
        private set

    var bestDepth: Int = initialBest
        private set

    var offers: List<Fighter> = emptyList()
        private set

    var pendingOffer: Fighter? = null
        private set

    var battle: Battle? = null
        private set

    /** Три силуэта для витрины на заглавном экране. */
    val demoTrio: List<Fighter> by lazy {
        Universe.entries.shuffled(rnd).take(3).map { Roster.summon(1, rnd, it) }
    }

    val squadIsFull: Boolean
        get() = squad.size >= MAX_SQUAD

    /** Пропуск призыва доступен, только когда в отряде уже кто-то есть. */
    val canSkipSummon: Boolean
        get() = squad.isNotEmpty()

    // --- Забег ---

    fun startRun() {
        squad.clear()
        depth = 1
        battle = null
        pendingOffer = null
        offers = Roster.offers(depth, rnd)
        screen = Screen.SUMMON
    }

    fun backToTitle() {
        screen = Screen.TITLE
    }

    // --- Призыв ---

    fun selectOffer(index: Int) {
        if (index !in offers.indices) return
        pendingOffer = offers[index]
    }

    /** Взять выбранного бойца в свободный слот. */
    fun acceptOffer() {
        val pick = pendingOffer ?: return
        if (squadIsFull) return
        squad.add(pick)
        pendingOffer = null
        screen = Screen.CAMP
    }

    /** Отряд полон — выбранный боец занимает место [slot]. */
    fun replaceSlot(slot: Int) {
        val pick = pendingOffer ?: return
        if (slot !in squad.indices) return
        squad[slot] = pick
        pendingOffer = null
        screen = Screen.CAMP
    }

    fun skipSummon() {
        if (!canSkipSummon) return
        pendingOffer = null
        screen = Screen.CAMP
    }

    // --- Бой ---

    fun startBattle() {
        if (squad.isEmpty()) return
        battle = Battle(
            squad = squad,
            enemy = Roster.enemy(depth, rnd),
            depth = depth,
            rnd = rnd
        )
        screen = Screen.BATTLE
    }

    /** Вызывается после того, как игрок закрыл итоговую панель боя. */
    fun afterBattle() {
        val finished = battle ?: return
        if (!finished.over) return

        if (finished.victory) {
            bestDepth = max(bestDepth, depth)
            onNewBest(bestDepth)
            depth++
            squad.forEach { it.recoverBetweenBattles() }
            battle = null
            offers = Roster.offers(depth, rnd)
            pendingOffer = null
            screen = Screen.SUMMON
        } else {
            battle = null
            screen = Screen.DEFEAT
        }
    }

    companion object {
        const val MAX_SQUAD = 3
    }
}
