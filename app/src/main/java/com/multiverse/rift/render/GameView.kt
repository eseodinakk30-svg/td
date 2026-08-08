package com.multiverse.rift.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.multiverse.rift.game.Battle
import com.multiverse.rift.game.GameState
import com.multiverse.rift.game.PlayerAction
import com.multiverse.rift.game.PopupKind
import com.multiverse.rift.game.Screen
import com.multiverse.rift.model.Fighter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Весь интерфейс игры: отрисовка в режиме immediate mode и обработка нажатий.
 * Кнопки регистрируются как «горячие зоны» прямо во время отрисовки кадра.
 */
class GameView(context: Context, private val game: GameState) : View(context) {

    private class Hotspot(val id: String, val rect: RectF, val enabled: Boolean)

    private class Floater(
        var x: Float,
        var y: Float,
        val text: String,
        val color: Int,
        var life: Float
    )

    private class Mote(var x: Float, var y: Float, val r: Float, val speed: Float, val phase: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val hotspots = ArrayList<Hotspot>()
    private val floaters = ArrayList<Floater>()
    private val motes = ArrayList<Mote>()
    private val rnd = Random(1234)

    private var pressedId: String? = null
    private var lastFrame = System.nanoTime()
    private var clock = 0f
    private var shake = 0f

    private var w = 0f
    private var h = 0f
    private var u = 1f
    private var safeTop = 0f

    private var enemyAnchorX = 0f
    private var enemyAnchorY = 0f
    private var playerAnchorX = 0f
    private var playerAnchorY = 0f

    init {
        isClickable = true
        repeat(46) {
            motes.add(
                Mote(
                    x = rnd.nextFloat(),
                    y = rnd.nextFloat(),
                    r = 0.4f + rnd.nextFloat() * 1.6f,
                    speed = 0.008f + rnd.nextFloat() * 0.03f,
                    phase = rnd.nextFloat() * 6.28f
                )
            )
        }
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        w = nw.toFloat()
        h = nh.toFloat()
        u = min(w, h) / 100f
        safeTop = h * 0.045f
    }

    // ------------------------------------------------------------------
    // Кадр
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = min(0.05f, (now - lastFrame) / 1_000_000_000f)
        lastFrame = now
        clock += dt

        hotspots.clear()
        advance(dt)

        canvas.save()
        if (shake > 0.01f) {
            val amp = shake * u * 1.1f
            canvas.translate(
                (rnd.nextFloat() - 0.5f) * amp,
                (rnd.nextFloat() - 0.5f) * amp
            )
        }

        drawBackground(canvas)

        when (game.screen) {
            Screen.TITLE -> drawTitle(canvas)
            Screen.SUMMON -> drawSummon(canvas)
            Screen.CAMP -> drawCamp(canvas)
            Screen.BATTLE -> drawBattle(canvas)
            Screen.DEFEAT -> drawDefeat(canvas)
        }

        drawFloaters(canvas)
        canvas.restore()

        postInvalidateOnAnimation()
    }

    private fun advance(dt: Float) {
        shake = max(0f, shake - dt * 3.2f)
        val it = floaters.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.life -= dt
            f.y -= dt * u * 9f
            if (f.life <= 0f) it.remove()
        }
        for (m in motes) {
            m.y -= m.speed * dt
            if (m.y < -0.05f) {
                m.y = 1.05f
                m.x = rnd.nextFloat()
            }
        }
    }

    // ------------------------------------------------------------------
    // Общие элементы
    // ------------------------------------------------------------------

    private fun accentColor(): Int = when (game.screen) {
        Screen.BATTLE -> game.battle?.enemy?.universe?.primary ?: Theme.ENERGY
        Screen.SUMMON -> game.pendingOffer?.universe?.primary ?: Theme.ENERGY
        Screen.CAMP -> game.squad.firstOrNull()?.universe?.primary ?: Theme.ENERGY
        else -> Theme.mix(Theme.ENERGY, Theme.CRIT, 0.35f)
    }

    private fun drawBackground(canvas: Canvas) {
        val accent = accentColor()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Theme.mix(Theme.BG, accent, 0.10f),
            Theme.BG,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Дальний отсвет разлома
        paint.shader = RadialGradient(
            w * 0.5f, h * 0.32f, w * 0.75f,
            Theme.withAlpha(accent, 0.16f),
            Theme.withAlpha(accent, 0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        for (m in motes) {
            val twinkle = 0.35f + 0.65f * abs(sin(clock * 0.8f + m.phase))
            paint.color = Theme.withAlpha(accent, 0.30f * twinkle)
            canvas.drawCircle(m.x * w, m.y * h, m.r * u * 0.5f, paint)
        }
    }

    private fun text(
        canvas: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        tf: Typeface = Theme.body,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.typeface = tf
        paint.textAlign = align
        canvas.drawText(s, x, y, paint)
    }

    /** Обрезает строку многоточием, если она не влезает в [maxWidth]. */
    private fun fit(s: String, maxWidth: Float, size: Float, tf: Typeface): String {
        paint.textSize = size
        paint.typeface = tf
        if (paint.measureText(s) <= maxWidth) return s
        var cut = s
        while (cut.length > 1 && paint.measureText("$cut…") > maxWidth) {
            cut = cut.substring(0, cut.length - 1)
        }
        return "$cut…"
    }

    private fun panel(canvas: Canvas, r: RectF, fill: Int, edge: Int, edgeWidth: Float = u * 0.35f) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRoundRect(r, u * 2.2f, u * 2.2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edgeWidth
        paint.color = edge
        canvas.drawRoundRect(r, u * 2.2f, u * 2.2f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun bar(canvas: Canvas, r: RectF, fraction: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Theme.withAlpha(Theme.BG, 0.75f)
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, paint)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            val filled = RectF(r.left, r.top, r.left + r.width() * f, r.bottom)
            paint.color = color
            canvas.drawRoundRect(filled, filled.height() / 2f, filled.height() / 2f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.22f
        paint.color = Theme.withAlpha(Theme.EDGE, 0.9f)
        canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun button(
        canvas: Canvas,
        id: String,
        r: RectF,
        label: String,
        sub: String? = null,
        accent: Int = Theme.ENERGY,
        enabled: Boolean = true
    ) {
        hotspots.add(Hotspot(id, RectF(r), enabled))
        val held = pressedId == id && enabled
        val alpha = if (enabled) 1f else 0.4f
        val fill = if (held) Theme.mix(Theme.PANEL_HI, accent, 0.35f) else Theme.PANEL_HI
        panel(canvas, r, Theme.withAlpha(fill, alpha), Theme.withAlpha(accent, alpha * 0.85f))

        val cx = r.centerX()
        if (sub == null) {
            text(
                canvas, fit(label, r.width() * 0.9f, u * 4.2f, Theme.display),
                cx, r.centerY() + u * 1.5f, u * 4.2f,
                Theme.withAlpha(Theme.TEXT, alpha), Theme.display, Paint.Align.CENTER
            )
        } else {
            text(
                canvas, fit(label, r.width() * 0.9f, u * 3.9f, Theme.display),
                cx, r.centerY() - u * 0.2f, u * 3.9f,
                Theme.withAlpha(Theme.TEXT, alpha), Theme.display, Paint.Align.CENTER
            )
            text(
                canvas, fit(sub, r.width() * 0.9f, u * 2.7f, Theme.body),
                cx, r.centerY() + u * 3.4f, u * 2.7f,
                Theme.withAlpha(accent, alpha), Theme.body, Paint.Align.CENTER
            )
        }
    }

    private fun header(canvas: Canvas, title: String, subtitle: String) {
        text(
            canvas, title, w * 0.5f, safeTop + u * 6f, u * 6.2f,
            Theme.TEXT, Theme.display, Paint.Align.CENTER
        )
        text(
            canvas, subtitle, w * 0.5f, safeTop + u * 10.4f, u * 3.1f,
            Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
        )
    }

    /** Строка характеристик: ЗД / АТ / ЗЩ / СК. */
    private fun stats(canvas: Canvas, f: Fighter, cx: Float, y: Float, size: Float) {
        val line = "ЗД ${f.maxHp}   АТ ${f.atk}   ЗЩ ${f.def}   СК ${f.spd}"
        text(canvas, line, cx, y, size, Theme.TEXT_DIM, Theme.mono, Paint.Align.CENTER)
    }

    // ------------------------------------------------------------------
    // Экран: заголовок
    // ------------------------------------------------------------------

    private fun drawTitle(canvas: Canvas) {
        text(
            canvas, "РАЗЛОМ", w * 0.5f, h * 0.22f, u * 14f,
            Theme.TEXT, Theme.display, Paint.Align.CENTER
        )
        text(
            canvas, "мультивселенная не прощает", w * 0.5f, h * 0.27f, u * 3.4f,
            Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
        )

        // Витрина: три силуэта из разных вселенных
        val demo = game.demoTrio
        val slotW = w / 3f
        for (i in demo.indices) {
            Avatar.draw(
                canvas, paint, demo[i],
                cx = slotW * (i + 0.5f),
                cy = h * 0.50f,
                size = h * 0.24f,
                time = clock + i * 1.3f,
                faceRight = i != 2
            )
        }

        val bw = w * 0.66f
        rect.set((w - bw) / 2f, h * 0.70f, (w + bw) / 2f, h * 0.70f + u * 13f)
        button(canvas, "start", rect, "ВОЙТИ В РАЗЛОМ", "собрать отряд и идти вглубь")

        if (game.bestDepth > 0) {
            text(
                canvas, "Лучшая глубина: ${game.bestDepth}", w * 0.5f, h * 0.89f, u * 3.4f,
                Theme.CRIT, Theme.bodyBold, Paint.Align.CENTER
            )
        }
    }

    // ------------------------------------------------------------------
    // Экран: призыв
    // ------------------------------------------------------------------

    private fun drawSummon(canvas: Canvas) {
        header(canvas, "ПРИЗЫВ", "глубина ${game.depth} · в отряде ${game.squad.size}/${GameState.MAX_SQUAD}")

        val offers = game.offers
        if (offers.isEmpty()) return

        val gap = u * 2f
        val cardW = (w - gap * (offers.size + 1)) / offers.size
        val top = safeTop + u * 14f
        val cardH = h * 0.34f

        for (i in offers.indices) {
            val f = offers[i]
            val left = gap + i * (cardW + gap)
            rect.set(left, top, left + cardW, top + cardH)
            val selected = game.pendingOffer === f
            val accent = f.universe.primary
            panel(
                canvas, rect,
                if (selected) Theme.mix(Theme.PANEL, accent, 0.22f) else Theme.PANEL,
                if (selected) accent else Theme.EDGE,
                if (selected) u * 0.6f else u * 0.3f
            )
            hotspots.add(Hotspot("offer:$i", RectF(rect), true))

            val cx = rect.centerX()
            Avatar.drawBust(canvas, paint, f, cx, top + cardH * 0.30f, cardW * 0.30f, clock + i)

            text(
                canvas, fit(f.name, cardW * 0.92f, u * 2.9f, Theme.bodyBold),
                cx, top + cardH * 0.62f, u * 2.9f, Theme.TEXT, Theme.bodyBold, Paint.Align.CENTER
            )
            text(
                canvas, fit("${f.role} · ${f.universe.trait}", cardW * 0.92f, u * 2.5f, Theme.body),
                cx, top + cardH * 0.72f, u * 2.5f, Theme.withAlpha(accent, 1f), Theme.body,
                Paint.Align.CENTER
            )
            text(
                canvas, fit(f.universe.title, cardW * 0.92f, u * 2.3f, Theme.body),
                cx, top + cardH * 0.81f, u * 2.3f, Theme.TEXT_FAINT, Theme.body, Paint.Align.CENTER
            )
            text(
                canvas, "сила ${f.power}", cx, top + cardH * 0.93f, u * 2.6f,
                Theme.TEXT_DIM, Theme.mono, Paint.Align.CENTER
            )
        }

        // Подробности выбранного
        val pick = game.pendingOffer
        val detailTop = top + cardH + u * 3f
        rect.set(u * 2f, detailTop, w - u * 2f, detailTop + u * 20f)
        panel(canvas, rect, Theme.PANEL, Theme.EDGE)

        if (pick == null) {
            text(
                canvas, "Выбери, кого вытащить из разлома", w * 0.5f,
                detailTop + u * 11f, u * 3.2f, Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
            )
        } else {
            text(
                canvas, fit(pick.name, w * 0.9f, u * 4f, Theme.display), w * 0.5f,
                detailTop + u * 5.5f, u * 4f, Theme.TEXT, Theme.display, Paint.Align.CENTER
            )
            stats(canvas, pick, w * 0.5f, detailTop + u * 10f, u * 3f)
            text(
                canvas, "${pick.ability.title} · ${pick.ability.cost} эн.", w * 0.5f,
                detailTop + u * 14.5f, u * 3.2f, pick.universe.primary, Theme.bodyBold,
                Paint.Align.CENTER
            )
            text(
                canvas, fit(pick.ability.description, w * 0.9f, u * 2.7f, Theme.body), w * 0.5f,
                detailTop + u * 18f, u * 2.7f, Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
            )
        }

        val actionTop = detailTop + u * 23f

        if (game.squadIsFull && pick != null) {
            text(
                canvas, "Отряд полон — кого заменить?", w * 0.5f, actionTop + u * 3f,
                u * 3.1f, Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
            )
            val slotGap = u * 2f
            val slotW = (w - slotGap * 4) / 3f
            val slotY = actionTop + u * 6f
            val slotH = u * 16f
            for (i in game.squad.indices) {
                val member = game.squad[i]
                val left = slotGap + i * (slotW + slotGap)
                rect.set(left, slotY, left + slotW, slotY + slotH)
                panel(canvas, rect, Theme.PANEL, Theme.withAlpha(Theme.DANGER, 0.6f))
                hotspots.add(Hotspot("slot:$i", RectF(rect), true))
                Avatar.drawBust(
                    canvas, paint, member, rect.centerX(), slotY + slotH * 0.36f,
                    slotH * 0.28f, clock + i, dimmed = true
                )
                text(
                    canvas, fit(member.name, slotW * 0.9f, u * 2.4f, Theme.body),
                    rect.centerX(), slotY + slotH * 0.82f, u * 2.4f,
                    Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
                )
            }
            // Замена не обязательна — от призыва всегда можно отказаться.
            val skipW = w * 0.5f
            rect.set((w - skipW) / 2f, slotY + slotH + u * 3f, (w + skipW) / 2f, slotY + slotH + u * 14f)
            button(canvas, "skip", rect, "ОСТАВИТЬ ОТРЯД", accent = Theme.TEXT_FAINT)
        } else {
            val bw = if (game.canSkipSummon) w * 0.44f else w * 0.7f
            val acceptLeft = if (game.canSkipSummon) u * 3f else (w - bw) / 2f
            rect.set(acceptLeft, actionTop, acceptLeft + bw, actionTop + u * 12f)
            button(
                canvas, "accept", rect, "ВЗЯТЬ",
                accent = pick?.universe?.primary ?: Theme.ENERGY,
                enabled = pick != null
            )
            if (game.canSkipSummon) {
                val skipLeft = w - u * 3f - bw
                rect.set(skipLeft, actionTop, skipLeft + bw, actionTop + u * 12f)
                button(canvas, "skip", rect, "ПРОПУСТИТЬ", accent = Theme.TEXT_FAINT)
            }
        }
    }

    // ------------------------------------------------------------------
    // Экран: лагерь
    // ------------------------------------------------------------------

    private fun drawCamp(canvas: Canvas) {
        header(canvas, "ЛАГЕРЬ", "впереди глубина ${game.depth}")

        val top = safeTop + u * 15f
        val cardH = u * 21f
        val gap = u * 2.4f

        for (i in game.squad.indices) {
            val f = game.squad[i]
            val y = top + i * (cardH + gap)
            rect.set(u * 3f, y, w - u * 3f, y + cardH)
            panel(canvas, rect, Theme.PANEL, Theme.withAlpha(f.universe.primary, 0.55f))

            Avatar.drawBust(
                canvas, paint, f, u * 3f + cardH * 0.5f, y + cardH * 0.5f,
                cardH * 0.34f, clock + i * 0.7f
            )

            val tx = u * 3f + cardH * 1.02f
            val maxTextW = w - tx - u * 4f
            text(
                canvas, fit(f.name, maxTextW, u * 3.6f, Theme.display), tx, y + u * 6f,
                u * 3.6f, Theme.TEXT, Theme.display
            )
            text(
                canvas, fit("${f.role} · ${f.universe.title}", maxTextW, u * 2.6f, Theme.body),
                tx, y + u * 9.6f, u * 2.6f, f.universe.primary, Theme.body
            )
            text(
                canvas, "ЗД ${f.hp}/${f.maxHp}   АТ ${f.atk}   ЗЩ ${f.def}   СК ${f.spd}",
                tx, y + u * 13.5f, u * 2.7f, Theme.TEXT_DIM, Theme.mono
            )

            rect.set(tx, y + u * 15.5f, w - u * 5f, y + u * 17.3f)
            bar(canvas, rect, f.hpFraction, Theme.hpColor(f.hpFraction))

            text(
                canvas, fit("${f.ability.title} · ${f.ability.cost} эн.", maxTextW, u * 2.5f, Theme.body),
                tx, y + u * 19.8f, u * 2.5f, Theme.TEXT_FAINT, Theme.body
            )
        }

        val bw = w * 0.7f
        val by = h - u * 18f
        rect.set((w - bw) / 2f, by, (w + bw) / 2f, by + u * 13f)
        button(canvas, "fight", rect, "В БОЙ", "эхо уже ждёт", accent = Theme.DANGER)
    }

    // ------------------------------------------------------------------
    // Экран: бой
    // ------------------------------------------------------------------

    private fun drawBattle(canvas: Canvas) {
        val battle = game.battle ?: return
        val enemy = battle.enemy
        val hero = battle.active

        // Полоса врага
        val enemyTop = safeTop + u * 1f
        rect.set(u * 3f, enemyTop, w - u * 3f, enemyTop + u * 15f)
        panel(canvas, rect, Theme.PANEL, Theme.withAlpha(enemy.universe.primary, 0.7f))
        text(
            canvas, fit(enemy.name, w * 0.62f, u * 3.4f, Theme.display), u * 5f,
            enemyTop + u * 5.4f, u * 3.4f, Theme.TEXT, Theme.display
        )
        text(
            canvas, fit(enemy.universe.title, w * 0.62f, u * 2.4f, Theme.body), u * 5f,
            enemyTop + u * 8.8f, u * 2.4f, enemy.universe.primary, Theme.body
        )
        text(
            canvas, "${enemy.hp}/${enemy.maxHp}", w - u * 5f, enemyTop + u * 5.4f,
            u * 3f, Theme.TEXT_DIM, Theme.mono, Paint.Align.RIGHT
        )
        rect.set(u * 5f, enemyTop + u * 10.6f, w - u * 5f, enemyTop + u * 12.4f)
        bar(canvas, rect, enemy.hpFraction, Theme.hpColor(enemy.hpFraction))
        drawStatusChips(canvas, enemy, u * 5f, enemyTop + u * 14.2f)

        // Арена
        val arenaTop = enemyTop + u * 17f
        val arenaH = h * 0.26f
        enemyAnchorX = w * 0.72f
        enemyAnchorY = arenaTop + arenaH * 0.42f
        playerAnchorX = w * 0.28f
        playerAnchorY = arenaTop + arenaH * 0.62f

        Avatar.draw(
            canvas, paint, enemy, enemyAnchorX, enemyAnchorY,
            size = arenaH * 0.72f, time = clock, faceRight = false
        )
        Avatar.draw(
            canvas, paint, hero, playerAnchorX, playerAnchorY,
            size = arenaH * 0.82f, time = clock, faceRight = true
        )

        // Всплывающие числа появляются, когда координаты бойцов уже известны
        spawnPopups(battle)

        // Полоса героя
        val heroTop = arenaTop + arenaH + u * 1f
        rect.set(u * 3f, heroTop, w - u * 3f, heroTop + u * 16f)
        panel(canvas, rect, Theme.PANEL, Theme.withAlpha(hero.universe.primary, 0.7f))
        text(
            canvas, fit(hero.name, w * 0.6f, u * 3.4f, Theme.display), u * 5f,
            heroTop + u * 5.4f, u * 3.4f, Theme.TEXT, Theme.display
        )
        text(
            canvas, fit("${hero.role} · ${hero.universe.title}", w * 0.6f, u * 2.4f, Theme.body),
            u * 5f, heroTop + u * 8.8f, u * 2.4f, hero.universe.primary, Theme.body
        )
        text(
            canvas, "${hero.hp}/${hero.maxHp}", w - u * 5f, heroTop + u * 5.4f,
            u * 3f, Theme.TEXT_DIM, Theme.mono, Paint.Align.RIGHT
        )
        rect.set(u * 5f, heroTop + u * 10.6f, w - u * 5f, heroTop + u * 12.4f)
        bar(canvas, rect, hero.hpFraction, Theme.hpColor(hero.hpFraction))
        drawEnergy(canvas, hero, u * 5f, heroTop + u * 14.6f)
        drawStatusChips(canvas, hero, w * 0.42f, heroTop + u * 14.9f)

        // Отряд
        val chipsTop = heroTop + u * 18f
        drawSquadChips(canvas, battle, chipsTop)

        // Журнал
        val logTop = chipsTop + u * 16f
        val actionsTop = h - u * 30f
        drawLog(canvas, battle, logTop, actionsTop - u * 2f)

        // Действия
        drawActions(canvas, battle, actionsTop)

        if (battle.over) drawBattleOver(canvas, battle)
    }

    private fun drawStatusChips(canvas: Canvas, f: Fighter, x: Float, y: Float) {
        var cursor = x
        val size = u * 2.4f
        if (f.shield > 0) {
            val label = "щит ${f.shield}"
            text(canvas, label, cursor, y, size, Theme.SHIELD, Theme.mono)
            cursor += paint.measureText(label) + u * 3f
        }
        if (f.burn > 0) {
            val label = "горит ${f.burn}"
            text(canvas, label, cursor, y, size, Theme.BURN, Theme.mono)
            cursor += paint.measureText(label) + u * 3f
        }
        if (f.evading) {
            text(canvas, "фаза", cursor, y, size, Theme.mix(Theme.SHIELD, Theme.ENERGY, 0.5f), Theme.mono)
        }
    }

    private fun drawEnergy(canvas: Canvas, f: Fighter, x: Float, y: Float) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        val r = u * 1.1f
        for (i in 0 until Fighter.MAX_ENERGY) {
            paint.color = if (i < f.energy) Theme.ENERGY else Theme.withAlpha(Theme.EDGE, 0.8f)
            canvas.drawCircle(x + r + i * (r * 2.6f), y - r * 0.4f, r, paint)
        }
    }

    private fun drawSquadChips(canvas: Canvas, battle: Battle, top: Float) {
        val squad = battle.squad
        val gap = u * 2f
        val chipW = (w - gap * (squad.size + 1)) / squad.size
        val chipH = u * 14f
        for (i in squad.indices) {
            val f = squad[i]
            val left = gap + i * (chipW + gap)
            rect.set(left, top, left + chipW, top + chipH)
            val isActive = i == battle.activeIndex
            val selectable = battle.canSwapTo(i)
            panel(
                canvas, rect,
                if (isActive) Theme.mix(Theme.PANEL, f.universe.primary, 0.25f) else Theme.PANEL,
                when {
                    isActive -> f.universe.primary
                    f.alive -> Theme.EDGE
                    else -> Theme.withAlpha(Theme.DANGER, 0.5f)
                },
                if (isActive) u * 0.6f else u * 0.3f
            )
            if (selectable) hotspots.add(Hotspot("swap:$i", RectF(rect), true))

            Avatar.drawBust(
                canvas, paint, f, left + chipH * 0.48f, top + chipH * 0.5f,
                chipH * 0.33f, clock + i * 0.6f, dimmed = !f.alive
            )

            val tx = left + chipH * 0.95f
            val tw = chipW - (chipH * 0.95f) - u * 1.5f
            text(
                canvas, fit(f.name, tw, u * 2.4f, Theme.bodyBold), tx, top + u * 5.2f,
                u * 2.4f, if (f.alive) Theme.TEXT else Theme.TEXT_FAINT, Theme.bodyBold
            )
            if (f.alive) {
                rect.set(tx, top + u * 7f, tx + tw, top + u * 8.4f)
                bar(canvas, rect, f.hpFraction, Theme.hpColor(f.hpFraction))
                val hint = if (isActive) "в бою" else "заменить"
                text(
                    canvas, hint, tx, top + u * 11.6f, u * 2.2f,
                    if (isActive) f.universe.primary else Theme.TEXT_FAINT, Theme.body
                )
            } else {
                text(canvas, "пал", tx, top + u * 9f, u * 2.4f, Theme.DANGER, Theme.body)
            }
        }
    }

    private fun drawLog(canvas: Canvas, battle: Battle, top: Float, bottom: Float) {
        rect.set(u * 3f, top, w - u * 3f, bottom)
        panel(canvas, rect, Theme.withAlpha(Theme.PANEL, 0.75f), Theme.withAlpha(Theme.EDGE, 0.7f))

        val lineH = u * 3.4f
        val maxLines = max(1, ((bottom - top - u * 3f) / lineH).toInt())
        val lines = battle.log.takeLast(maxLines)
        var y = top + u * 4.4f
        for (i in lines.indices) {
            val freshness = (i + 1).toFloat() / lines.size
            val color = Theme.withAlpha(Theme.TEXT, 0.35f + 0.65f * freshness)
            text(
                canvas, fit(lines[i], w - u * 10f, u * 2.7f, Theme.body),
                u * 5f, y, u * 2.7f, color, Theme.body
            )
            y += lineH
        }
    }

    private fun drawActions(canvas: Canvas, battle: Battle, top: Float) {
        val hero = battle.active
        val gap = u * 2f
        val bw = (w - gap * 4) / 3f
        val bh = u * 14f
        val enabled = !battle.over

        rect.set(gap, top, gap + bw, top + bh)
        button(canvas, "act:attack", rect, "АТАКА", "обычный удар", Theme.DANGER, enabled)

        rect.set(gap * 2 + bw, top, gap * 2 + bw * 2, top + bh)
        button(
            canvas, "act:cast", rect,
            fit(hero.ability.title, bw * 0.9f, u * 3.9f, Theme.display),
            "${hero.ability.cost} эн.",
            hero.universe.primary,
            enabled && hero.canCast()
        )

        rect.set(gap * 3 + bw * 2, top, gap * 3 + bw * 3, top + bh)
        button(canvas, "act:focus", rect, "ФОКУС", "+2 эн. и щит", Theme.ENERGY, enabled)

        text(
            canvas, hero.ability.description, w * 0.5f, top + bh + u * 4f, u * 2.6f,
            Theme.TEXT_FAINT, Theme.body, Paint.Align.CENTER
        )
    }

    private fun drawBattleOver(canvas: Canvas, battle: Battle) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Theme.withAlpha(Theme.BG, 0.82f)
        canvas.drawRect(0f, 0f, w, h, paint)

        val ph = u * 44f
        rect.set(u * 6f, h * 0.5f - ph / 2f, w - u * 6f, h * 0.5f + ph / 2f)
        val accent = if (battle.victory) Theme.HP else Theme.DANGER
        panel(canvas, rect, Theme.PANEL, accent, u * 0.6f)

        val top = rect.top
        text(
            canvas, if (battle.victory) "ЭХО РАССЕЯНО" else "ОТРЯД ПАЛ",
            w * 0.5f, top + u * 10f, u * 6f, accent, Theme.display, Paint.Align.CENTER
        )
        text(
            canvas,
            if (battle.victory) "Глубина ${battle.depth} пройдена" else "Разлом сомкнулся на глубине ${battle.depth}",
            w * 0.5f, top + u * 16f, u * 3.2f, Theme.TEXT_DIM, Theme.body, Paint.Align.CENTER
        )
        text(
            canvas,
            if (battle.victory) "Отряд отдышится и получит новый призыв" else "Но разломов всегда больше одного",
            w * 0.5f, top + u * 21f, u * 2.7f, Theme.TEXT_FAINT, Theme.body, Paint.Align.CENTER
        )

        val bw = w * 0.5f
        rect.set((w - bw) / 2f, top + u * 27f, (w + bw) / 2f, top + u * 39f)
        button(canvas, "next", rect, if (battle.victory) "ДАЛЬШЕ" else "ИТОГИ", accent = accent)
    }

    private fun spawnPopups(battle: Battle) {
        val popups = battle.drainPopups()
        for (p in popups) {
            val color = when (p.kind) {
                PopupKind.DAMAGE -> Theme.TEXT
                PopupKind.CRIT -> Theme.CRIT
                PopupKind.HEAL -> Theme.HEAL
                PopupKind.SHIELD -> Theme.SHIELD
                PopupKind.MISS -> Theme.TEXT_FAINT
                PopupKind.BURN -> Theme.BURN
            }
            val baseX = if (p.onEnemy) enemyAnchorX else playerAnchorX
            val baseY = if (p.onEnemy) enemyAnchorY else playerAnchorY
            floaters.add(
                Floater(
                    x = baseX + (rnd.nextFloat() - 0.5f) * u * 8f,
                    y = baseY - u * 8f - floaters.size * u * 1.2f,
                    text = p.text,
                    color = color,
                    life = 1.15f
                )
            )
            if (p.kind == PopupKind.CRIT) shake = 1f
            else if (p.kind == PopupKind.DAMAGE) shake = max(shake, 0.55f)
        }
    }

    private fun drawFloaters(canvas: Canvas) {
        for (f in floaters) {
            val alpha = (f.life / 1.15f).coerceIn(0f, 1f)
            text(
                canvas, f.text, f.x, f.y, u * 4.4f,
                Theme.withAlpha(f.color, alpha), Theme.display, Paint.Align.CENTER
            )
        }
    }

    // ------------------------------------------------------------------
    // Экран: поражение
    // ------------------------------------------------------------------

    private fun drawDefeat(canvas: Canvas) {
        text(
            canvas, "РАЗЛОМ СОМКНУЛСЯ", w * 0.5f, h * 0.30f, u * 8f,
            Theme.DANGER, Theme.display, Paint.Align.CENTER
        )
        text(
            canvas, "Пройдено глубин: ${max(0, game.depth - 1)}", w * 0.5f, h * 0.40f, u * 4f,
            Theme.TEXT, Theme.body, Paint.Align.CENTER
        )
        text(
            canvas, "Лучший результат: ${game.bestDepth}", w * 0.5f, h * 0.46f, u * 3.4f,
            Theme.CRIT, Theme.bodyBold, Paint.Align.CENTER
        )
        text(
            canvas, "Где-то есть версия тебя, которая дошла дальше", w * 0.5f, h * 0.55f,
            u * 2.9f, Theme.TEXT_FAINT, Theme.body, Paint.Align.CENTER
        )

        val bw = w * 0.62f
        rect.set((w - bw) / 2f, h * 0.68f, (w + bw) / 2f, h * 0.68f + u * 13f)
        button(canvas, "restart", rect, "ЕЩЁ РАЗ", accent = Theme.ENERGY)
    }

    // ------------------------------------------------------------------
    // Ввод
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedId = hit(event.x, event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pressedId != null && hit(event.x, event.y) != pressedId) pressedId = null
                return true
            }

            MotionEvent.ACTION_UP -> {
                val id = hit(event.x, event.y)
                if (id != null && id == pressedId) {
                    performClick()
                    handle(id)
                }
                pressedId = null
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedId = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hit(x: Float, y: Float): String? {
        for (i in hotspots.indices.reversed()) {
            val spot = hotspots[i]
            if (spot.enabled && spot.rect.contains(x, y)) return spot.id
        }
        return null
    }

    private fun handle(id: String) {
        when {
            id == "start" -> game.startRun()
            id == "accept" -> game.acceptOffer()
            id == "skip" -> game.skipSummon()
            id == "fight" -> game.startBattle()
            id == "restart" -> game.backToTitle()
            id == "next" -> game.afterBattle()

            id.startsWith("offer:") -> game.selectOffer(indexOf(id))
            id.startsWith("slot:") -> game.replaceSlot(indexOf(id))
            id.startsWith("swap:") -> game.battle?.swapTo(indexOf(id))

            id == "act:attack" -> game.battle?.act(PlayerAction.ATTACK)
            id == "act:cast" -> game.battle?.act(PlayerAction.CAST)
            id == "act:focus" -> game.battle?.act(PlayerAction.FOCUS)
        }
        invalidate()
    }

    private fun indexOf(id: String): Int =
        id.substringAfter(':').toIntOrNull() ?: -1
}
