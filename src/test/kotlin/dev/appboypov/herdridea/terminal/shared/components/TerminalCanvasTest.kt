package dev.appboypov.herdridea.terminal.shared.components

import dev.appboypov.herdridea.terminal.shared.models.ConsoleLook
import dev.appboypov.herdridea.terminal.shared.models.ScreenCursor
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.ScreenRow
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.util.BitSet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Paints one row at 2x scale, as on a Retina display, with a console font whose advance is a
 * fraction of a pixel, and finds where the glyph of a late column lands.
 */
class TerminalCanvasTest {
    private val look = ConsoleLook(Font.MONOSPACED, fractionalAdvanceSize(), 1f, TerminalColors(WHITE, BLACK, List(16) { WHITE }), 0x214283)

    @Test
    fun `Given a plain row, when the panel draws it, then the glyph of column 50 sits inside that column's cell`() {
        val (cellLeft, cellRight, ink) = paintGlyphAtColumn(50, flags = 0)

        assertTrue(ink.first >= cellLeft && ink.last < cellRight, "glyph ink ${ink.first}..${ink.last} outside cell $cellLeft until $cellRight")
    }

    @Test
    fun `Given a bold row, when the panel draws it, then the glyph of column 50 sits inside that column's cell`() {
        val (cellLeft, cellRight, ink) = paintGlyphAtColumn(50, flags = ScreenRow.BOLD_FLAG)

        assertTrue(ink.first >= cellLeft && ink.last < cellRight, "glyph ink ${ink.first}..${ink.last} outside cell $cellLeft until $cellRight")
    }

    /**
     * Paints a row of spaces, one batched run, with `|` at [column], and returns the column's cell
     * and the painted glyph's horizontal extent, all in device pixels.
     */
    private fun paintGlyphAtColumn(column: Int, flags: Int): Triple<Int, Int, IntRange> {
        val canvas = TerminalCanvas(onSizeChange = {}, onMouse = {})
        canvas.setLook(look)
        val cols = column + 10
        canvas.setSize(cols * canvas.gridSize.cellWidthPx, canvas.gridSize.cellHeightPx)
        val cellWidth = canvas.gridSize.cellWidthPx
        val row = ScreenRow(
            Array(cols) { if (it == column) "|" else " " },
            IntArray(cols) { ScreenRow.DEFAULT_COLOR },
            IntArray(cols) { ScreenRow.DEFAULT_COLOR },
            IntArray(cols) { flags },
        )
        canvas.show(ScreenFrame(cols, listOf(row), BitSet().apply { set(0) }, ScreenCursor.HIDDEN, WHITE, BLACK, mouseTracking = false))

        val image = BufferedImage(canvas.width * SCALE, canvas.height * SCALE, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.scale(SCALE.toDouble(), SCALE.toDouble())
        canvas.paint(g)
        g.dispose()

        val inked = (0 until image.width).filter { x -> (0 until image.height).any { y -> image.getRGB(x, y) and 0xFFFFFF != BLACK } }
        assertTrue(inked.isNotEmpty(), "nothing painted")
        return Triple(column * cellWidth * SCALE, (column + 1) * cellWidth * SCALE, inked.first()..inked.last())
    }

    /**
     * A size at which the console font's advance lies between a whole pixel and a half, so the
     * advance differs from the whole-pixel cell width with and without fractional metrics.
     */
    private fun fractionalAdvanceSize(): Float {
        val frc = FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        return generateSequence(10f) { it + 0.25f }.takeWhile { it <= 24f }.first { size ->
            val advance = Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(size).createGlyphVector(frc, "W").getGlyphMetrics(0).advanceX
            (advance - advance.toInt()) in 0.3f..0.45f
        }
    }

    private companion object {
        const val SCALE = 2
        const val WHITE = 0xFFFFFF
        const val BLACK = 0x000000
    }
}
