package com.multiverse.rift.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.multiverse.rift.model.Fighter
import com.multiverse.rift.model.Universe
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Процедурные силуэты бойцов. Никаких картинок в ресурсах — каждый
 * персонаж собирается из фигур по своей вселенной и зерну [Fighter.seed].
 */
object Avatar {

    private val path = Path()
    private val clip = Path()
    private val rect = RectF()

    /**
     * Рисует фигуру в полный рост. [size] — примерная высота силуэта,
     * [cy] — его вертикальный центр.
     */
    fun draw(
        canvas: Canvas,
        paint: Paint,
        f: Fighter,
        cx: Float,
        cy: Float,
        size: Float,
        time: Float,
        faceRight: Boolean,
        dimmed: Boolean = false
    ) {
        val rnd = Random(f.seed)
        val primary = f.universe.primary
        val secondary = f.universe.secondary
        val deep = f.universe.deep

        val bob = sin(time * 1.9f + f.seed % 10) * size * 0.014f

        canvas.save()
        canvas.translate(cx, cy + bob)
        if (!faceRight) canvas.scale(-1f, 1f)

        val alpha = if (dimmed) 0.35f else 1f

        // Аура
        paint.style = Paint.Style.FILL
        val auraR = size * 0.66f
        val pulse = 0.78f + 0.22f * sin(time * 1.4f + f.seed % 7)
        paint.shader = RadialGradient(
            0f, 0f, auraR,
            Theme.withAlpha(primary, 0.30f * pulse * alpha),
            Theme.withAlpha(primary, 0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, auraR, paint)
        paint.shader = null

        val headR = size * 0.15f
        val headY = -size * 0.27f
        val shoulderY = -size * 0.07f
        val hipY = size * 0.30f
        val shoulderHalf = size * 0.19f
        val hipHalf = size * 0.11f
        val footY = size * 0.50f

        val bodyTop = Theme.withAlpha(primary, alpha)
        val bodyBottom = Theme.withAlpha(Theme.mix(deep, primary, 0.25f), alpha)
        paint.shader = LinearGradient(
            0f, shoulderY, 0f, footY,
            bodyTop, bodyBottom, Shader.TileMode.CLAMP
        )

        // Ноги
        paint.style = Paint.Style.FILL
        val legHalf = hipHalf * 0.42f
        path.reset()
        path.moveTo(-hipHalf, hipY)
        path.lineTo(-hipHalf + legHalf * 1.6f, hipY)
        path.lineTo(-legHalf * 0.6f, footY)
        path.lineTo(-hipHalf + legHalf * 0.1f, footY)
        path.close()
        canvas.drawPath(path, paint)

        path.reset()
        path.moveTo(hipHalf, hipY)
        path.lineTo(hipHalf - legHalf * 1.6f, hipY)
        path.lineTo(legHalf * 0.6f, footY)
        path.lineTo(hipHalf - legHalf * 0.1f, footY)
        path.close()
        canvas.drawPath(path, paint)

        // Торс
        path.reset()
        path.moveTo(-shoulderHalf, shoulderY)
        path.lineTo(shoulderHalf, shoulderY)
        path.lineTo(hipHalf, hipY)
        path.lineTo(-hipHalf, hipY)
        path.close()
        canvas.drawPath(path, paint)

        // Руки
        val armSwing = sin(time * 1.9f + f.seed % 5) * size * 0.02f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.055f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            -shoulderHalf * 0.9f, shoulderY + size * 0.02f,
            -shoulderHalf * 1.05f, hipY + armSwing, paint
        )
        canvas.drawLine(
            shoulderHalf * 0.9f, shoulderY + size * 0.02f,
            shoulderHalf * 1.05f, hipY - armSwing, paint
        )
        paint.shader = null

        // Шея
        paint.style = Paint.Style.FILL
        paint.color = Theme.withAlpha(Theme.mix(primary, deep, 0.45f), alpha)
        rect.set(-headR * 0.28f, headY + headR * 0.55f, headR * 0.28f, shoulderY + size * 0.01f)
        canvas.drawRect(rect, paint)

        drawHead(canvas, paint, f.universe, headR, headY, time, primary, secondary, deep, alpha, rnd)

        canvas.restore()
    }

    private fun drawHead(
        canvas: Canvas,
        paint: Paint,
        universe: Universe,
        headR: Float,
        headY: Float,
        time: Float,
        primary: Int,
        secondary: Int,
        deep: Int,
        alpha: Float,
        rnd: Random
    ) {
        val skin = Theme.withAlpha(Theme.mix(primary, deep, 0.30f), alpha)
        val glow = Theme.withAlpha(secondary, alpha)
        paint.style = Paint.Style.FILL

        when (universe) {
            Universe.NEON -> {
                paint.color = skin
                rect.set(-headR, headY - headR, headR, headY + headR * 0.85f)
                canvas.drawRoundRect(rect, headR * 0.38f, headR * 0.38f, paint)
                // Визор
                paint.color = glow
                rect.set(-headR * 0.72f, headY - headR * 0.18f, headR * 0.82f, headY + headR * 0.14f)
                canvas.drawRoundRect(rect, headR * 0.12f, headR * 0.12f, paint)
                // Антенна
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = headR * 0.14f
                canvas.drawLine(headR * 0.45f, headY - headR, headR * 0.78f, headY - headR * 2.0f, paint)
                paint.style = Paint.Style.FILL
                val blink = 0.6f + 0.4f * sin(time * 5f)
                paint.color = Theme.withAlpha(secondary, alpha * blink)
                canvas.drawCircle(headR * 0.78f, headY - headR * 2.0f, headR * 0.18f, paint)
            }

            Universe.ASH -> {
                paint.color = skin
                rect.set(-headR * 0.92f, headY - headR, headR * 0.92f, headY + headR * 0.9f)
                canvas.drawRoundRect(rect, headR * 0.3f, headR * 0.3f, paint)
                // Рога
                paint.color = Theme.withAlpha(secondary, alpha)
                val horn = headR * (1.1f + rnd.nextFloat() * 0.5f)
                path.reset()
                path.moveTo(-headR * 0.85f, headY - headR * 0.75f)
                path.lineTo(-headR * 1.5f, headY - headR * 0.6f - horn)
                path.lineTo(-headR * 0.35f, headY - headR * 0.95f)
                path.close()
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(headR * 0.85f, headY - headR * 0.75f)
                path.lineTo(headR * 1.5f, headY - headR * 0.6f - horn)
                path.lineTo(headR * 0.35f, headY - headR * 0.95f)
                path.close()
                canvas.drawPath(path, paint)
                // Глаз
                paint.color = Theme.withAlpha(Theme.CRIT, alpha)
                canvas.drawCircle(headR * 0.35f, headY, headR * 0.16f, paint)
            }

            Universe.VERDANT -> {
                paint.color = skin
                canvas.drawCircle(0f, headY, headR, paint)
                // Листья
                paint.color = Theme.withAlpha(secondary, alpha)
                val sway = sin(time * 1.2f) * 8f
                for (i in 0 until 3) {
                    canvas.save()
                    canvas.rotate(-40f + i * 40f + sway, 0f, headY - headR * 0.6f)
                    rect.set(
                        -headR * 0.22f, headY - headR * 2.0f,
                        headR * 0.22f, headY - headR * 0.55f
                    )
                    canvas.drawOval(rect, paint)
                    canvas.restore()
                }
                paint.color = Theme.withAlpha(Theme.BG, alpha)
                canvas.drawCircle(headR * 0.34f, headY + headR * 0.05f, headR * 0.13f, paint)
            }

            Universe.CHROME -> {
                paint.color = skin
                canvas.drawCircle(0f, headY, headR, paint)
                // Орбита
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = headR * 0.12f
                paint.color = Theme.withAlpha(secondary, alpha * 0.9f)
                canvas.save()
                canvas.rotate(time * 34f, 0f, headY)
                rect.set(-headR * 1.65f, headY - headR * 0.45f, headR * 1.65f, headY + headR * 0.45f)
                canvas.drawOval(rect, paint)
                canvas.restore()
                paint.style = Paint.Style.FILL
                paint.color = Theme.withAlpha(Theme.SHIELD, alpha)
                canvas.drawCircle(headR * 0.32f, headY - headR * 0.05f, headR * 0.14f, paint)
            }

            Universe.EMBER -> {
                paint.color = skin
                canvas.drawCircle(0f, headY, headR, paint)
                // Корона из пламени
                for (i in 0 until 4) {
                    val flick = 0.75f + 0.45f * sin(time * 6f + i * 1.7f)
                    paint.color = Theme.withAlpha(
                        if (i % 2 == 0) secondary else primary,
                        alpha * 0.95f
                    )
                    val bx = -headR * 0.75f + i * headR * 0.5f
                    path.reset()
                    path.moveTo(bx - headR * 0.26f, headY - headR * 0.78f)
                    path.lineTo(bx, headY - headR * (1.0f + 0.95f * flick))
                    path.lineTo(bx + headR * 0.26f, headY - headR * 0.78f)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                paint.color = Theme.withAlpha(Theme.CRIT, alpha)
                canvas.drawCircle(headR * 0.33f, headY + headR * 0.05f, headR * 0.15f, paint)
            }

            Universe.GLASS -> {
                // Голова-осколок
                paint.color = skin
                path.reset()
                path.moveTo(0f, headY - headR * 1.15f)
                path.lineTo(headR * 0.9f, headY - headR * 0.2f)
                path.lineTo(headR * 0.55f, headY + headR * 0.9f)
                path.lineTo(-headR * 0.55f, headY + headR * 0.9f)
                path.lineTo(-headR * 0.9f, headY - headR * 0.2f)
                path.close()
                canvas.drawPath(path, paint)
                // Осколки вокруг
                paint.color = Theme.withAlpha(secondary, alpha * 0.85f)
                for (i in 0 until 3) {
                    val ang = time * 0.8f + i * 2.1f
                    val rx = cos(ang) * headR * 1.7f
                    val ry = headY + sin(ang) * headR * 0.9f
                    path.reset()
                    path.moveTo(rx, ry - headR * 0.28f)
                    path.lineTo(rx + headR * 0.18f, ry)
                    path.lineTo(rx, ry + headR * 0.28f)
                    path.lineTo(rx - headR * 0.18f, ry)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                paint.color = Theme.withAlpha(secondary, alpha)
                rect.set(-headR * 0.5f, headY - headR * 0.1f, headR * 0.6f, headY + headR * 0.08f)
                canvas.drawRect(rect, paint)
            }
        }

        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
    }

    /**
     * Компактный портрет для карточек отряда: голова и плечи в круге.
     */
    fun drawBust(
        canvas: Canvas,
        paint: Paint,
        f: Fighter,
        cx: Float,
        cy: Float,
        radius: Float,
        time: Float,
        dimmed: Boolean = false
    ) {
        val alpha = if (dimmed) 0.4f else 1f
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius,
            Theme.withAlpha(f.universe.primary, 0.35f * alpha),
            Theme.withAlpha(f.universe.deep, 0.9f * alpha),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        canvas.save()
        clip.reset()
        clip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        // Фигура крупнее круга, чтобы в кадр попали голова и плечи.
        draw(
            canvas = canvas,
            paint = paint,
            f = f,
            cx = cx,
            cy = cy + radius * 1.55f,
            size = radius * 4.6f,
            time = time,
            faceRight = true,
            dimmed = dimmed
        )
        canvas.restore()

        // Ободок поверх силуэта, чтобы край круга остался чистым.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.08f
        paint.color = Theme.withAlpha(f.universe.primary, alpha)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.FILL
    }
}
