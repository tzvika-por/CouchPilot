package com.myremote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/** Small original line icons, drawn at any density without bitmap assets or a new dependency. */
internal enum class RemoteGlyph { SETTINGS, POWER, HELP, TV, BOX, SOUNDBAR, GAMEPAD, LAPTOP, PC,
    HOME, BACK, UP, DOWN, LEFT, RIGHT, MINUS, PLUS, PLAY_PAUSE, REWIND, FORWARD }

@Composable
internal fun RemoteGlyph(glyph: RemoteGlyph, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x, y), Offset(x2, y2), 1.8f, StrokeCap.Round)
            fun path(vararg points: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
                val shape = Path().apply {
                    moveTo(points[0].first, points[0].second)
                    for ((x, y) in points.drop(1)) lineTo(x, y)
                    if (close) this.close()
                }
                if (fill) drawPath(shape, tint) else drawPath(shape, tint, style = stroke)
            }
            when (glyph) {
                RemoteGlyph.POWER -> { drawArc(tint, -48f, 276f, false, Offset(5f, 5f), Size(22f, 22f), style = stroke); line(16f, 3f, 16f, 15f) }
                RemoteGlyph.SETTINGS -> {
                    drawCircle(tint, 9f, Offset(16f, 16f), style = stroke)
                    drawCircle(tint, 3.5f, Offset(16f, 16f), style = stroke)
                    for (i in 0..7) {
                        val a = i * Math.PI / 4
                        line(16f + (9 * kotlin.math.cos(a)).toFloat(), 16f + (9 * kotlin.math.sin(a)).toFloat(),
                            16f + (12 * kotlin.math.cos(a)).toFloat(), 16f + (12 * kotlin.math.sin(a)).toFloat())
                    }
                }
                RemoteGlyph.HELP -> {
                    drawCircle(tint, 12f, Offset(16f, 16f), style = stroke)
                    drawArc(tint, 180f, 245f, false, Offset(12f, 8f), Size(8f, 8f), style = stroke)
                    line(18.5f, 15f, 16f, 18f); drawCircle(tint, 1f, Offset(16f, 23f))
                }
                RemoteGlyph.TV, RemoteGlyph.PC -> {
                    drawRoundRect(tint, Offset(3f, 5f), Size(26f, 18f), CornerRadius(1.5f), style = stroke)
                    line(16f, 23f, 16f, 28f); line(10f, 28f, 22f, 28f)
                    if (glyph == RemoteGlyph.PC) line(7f, 20f, 25f, 20f)
                }
                RemoteGlyph.BOX -> {
                    drawRoundRect(tint, Offset(2f, 9f), Size(28f, 15f), CornerRadius(6f), style = stroke)
                    line(7f, 16.5f, 17f, 16.5f); drawCircle(tint, 1.4f, Offset(24f, 16.5f), style = stroke)
                }
                RemoteGlyph.SOUNDBAR -> {
                    drawRoundRect(tint, Offset(2f, 12f), Size(28f, 8f), CornerRadius(2f), style = stroke)
                    for (x in listOf(6f, 10f, 22f, 26f)) drawCircle(tint, .7f, Offset(x, 16f))
                }
                RemoteGlyph.LAPTOP -> { path(7f to 5f, 25f to 5f, 25f to 23f, 7f to 23f, close = true); path(7f to 23f, 2f to 27f, 30f to 27f, 25f to 23f) }
                RemoteGlyph.GAMEPAD -> {
                    path(10f to 9f, 22f to 9f, 26f to 12f, 29f to 23f, 27f to 26f, 24f to 26f,
                        20f to 21f, 12f to 21f, 8f to 26f, 5f to 26f, 3f to 23f, 6f to 12f, close = true)
                    line(8f, 15f, 14f, 15f); line(11f, 12f, 11f, 18f)
                    drawCircle(tint, 1.1f, Offset(23f, 14f)); drawCircle(tint, 1.1f, Offset(20f, 17f))
                }
                RemoteGlyph.HOME -> { path(3f to 15f, 16f to 4f, 29f to 15f, 25f to 15f, 25f to 28f, 19f to 28f, 19f to 19f, 13f to 19f, 13f to 28f, 7f to 28f, 7f to 15f, close = true, fill = true) }
                RemoteGlyph.BACK -> { path(12f to 7f, 5f to 13f, 12f to 19f); line(5f, 13f, 20f, 13f); drawArc(tint, -90f, 180f, false, Offset(13f, 13f), Size(14f, 14f), style = stroke); line(20f, 27f, 13f, 27f) }
                RemoteGlyph.UP -> path(7f to 21f, 16f to 12f, 25f to 21f)
                RemoteGlyph.DOWN -> path(7f to 12f, 16f to 21f, 25f to 12f)
                RemoteGlyph.LEFT -> path(21f to 7f, 12f to 16f, 21f to 25f)
                RemoteGlyph.RIGHT -> path(12f to 7f, 21f to 16f, 12f to 25f)
                RemoteGlyph.MINUS -> line(6f, 16f, 26f, 16f)
                RemoteGlyph.PLUS -> { line(6f, 16f, 26f, 16f); line(16f, 6f, 16f, 26f) }
                RemoteGlyph.PLAY_PAUSE -> { path(4f to 6f, 16f to 16f, 4f to 26f, close = true, fill = true); line(22f, 7f, 22f, 25f); line(28f, 7f, 28f, 25f) }
                RemoteGlyph.REWIND -> { path(15f to 7f, 4f to 16f, 15f to 25f, close = true); path(28f to 7f, 17f to 16f, 28f to 25f, close = true) }
                RemoteGlyph.FORWARD -> { path(4f to 7f, 15f to 16f, 4f to 25f, close = true); path(17f to 7f, 28f to 16f, 17f to 25f, close = true) }
            }
        }
    }
}
