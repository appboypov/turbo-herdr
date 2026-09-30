package dev.appboypov.herdridea.terminal.shared.components

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.util.ui.UIUtil
import dev.appboypov.herdridea.terminal.shared.services.AwtKeyTranslator
import dev.appboypov.herdridea.terminal.shared.models.TerminalMouseInput
import dev.appboypov.herdridea.terminal.shared.enums.CursorStyle
import dev.appboypov.herdridea.terminal.shared.models.ConsoleLook
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.ScreenRow
import dev.appboypov.herdridea.terminal.shared.models.TerminalSize
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Point
import java.awt.geom.Point2D
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws [ScreenFrame]s cell by cell on a fixed grid (ADR-0002): the console font, the colours
 * libghostty-vt resolved, text attributes, wide cells and the cursor. It repaints only rows whose
 * [ScreenRow] changed since the frame it last drew, and reports its grid size through [onSizeChange].
 *
 * Mouse: while the program tracks the mouse, presses, releases, drags, motion and the wheel go to
 * [onMouse]. Otherwise, or with shift held, dragging selects text locally for [selectedText]. EDT only.
 */
class TerminalCanvas(
    private val onSizeChange: (TerminalSize) -> Unit,
    private val onMouse: (TerminalMouseInput) -> Unit,
) : JComponent() {
    private var frame: ScreenFrame? = null
    private var fonts = arrayOfNulls<Font>(4)
    private var metrics: FontMetrics? = null
    private var cellWidth = TerminalSize.DEFAULT.cellWidthPx
    private var cellHeight = TerminalSize.DEFAULT.cellHeightPx
    private var ascentOffset = 0
    private val glyphPosition = Point2D.Float()
    private var background = Color.BLACK
    private var reportedSize: TerminalSize? = null
    private val colors = HashMap<Int, Color>()
    private var selectionAnchor: Point? = null
    private var selectionEnd: Point? = null
    private var selecting = false
    private val pressedButtons = HashSet<String>()
    private var wheelRemainder = 0.0
    private var selection = Color(0x21, 0x42, 0x83, 0x80)

    init {
        isFocusable = true
        isOpaque = true
        focusTraversalKeysEnabled = false
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = reportSize()
        })
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = press(e)
            override fun mouseReleased(e: MouseEvent) = release(e)
            override fun mouseDragged(e: MouseEvent) = drag(e)
            override fun mouseMoved(e: MouseEvent) = move(e)
            override fun mouseWheelMoved(e: MouseWheelEvent) = wheel(e)
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
    }

    /** The grid size that fits the component with the current font. */
    val gridSize: TerminalSize
        get() = TerminalSize(max(1, width / cellWidth), max(1, height / cellHeight), cellWidth, cellHeight)

    val currentFrame: ScreenFrame? get() = frame

    /** The locally selected text, one line per row with trailing spaces removed, or null without a selection. */
    fun selectedText(): String? {
        val frame = frame ?: return null
        val (start, end) = selectionRange() ?: return null
        return (start.y..min(end.y, frame.rowCount - 1)).joinToString("\n") { y ->
            val from = if (y == start.y) start.x else 0
            val to = if (y == end.y) end.x + 1 else frame.cols
            frame.rows[y].text(from, to).trimEnd()
        }
    }

    private fun press(e: MouseEvent) {
        requestFocusInWindow()
        clearSelection()
        val button = button(e) ?: return
        if (tracksMouse(e)) {
            pressedButtons += button
            onMouse(TerminalMouseInput("PRESS", button, AwtKeyTranslator.mods(e), e.x.toFloat(), e.y.toFloat(), true))
        } else if (button == LEFT) {
            selecting = true
            selectionAnchor = cellAt(e)
            selectionEnd = selectionAnchor
        }
    }

    private fun release(e: MouseEvent) {
        val button = button(e) ?: return
        if (selecting) {
            selecting = false
            if (selectionAnchor == selectionEnd) clearSelection()
            return
        }
        if (!pressedButtons.remove(button)) return
        onMouse(TerminalMouseInput("RELEASE", button, AwtKeyTranslator.mods(e), e.x.toFloat(), e.y.toFloat(), pressedButtons.isNotEmpty()))
    }

    private fun drag(e: MouseEvent) {
        if (selecting) {
            selectionEnd = cellAt(e)
            repaint()
            return
        }
        val button = button(e)?.takeIf { it in pressedButtons } ?: return
        onMouse(TerminalMouseInput("MOTION", button, AwtKeyTranslator.mods(e), e.x.toFloat(), e.y.toFloat(), true))
    }

    private fun move(e: MouseEvent) {
        if (!tracksMouse(e)) return
        onMouse(TerminalMouseInput("MOTION", null, AwtKeyTranslator.mods(e), e.x.toFloat(), e.y.toFloat(), false))
    }

    /** One wheel press per notch or accumulated trackpad unit: button four scrolls up, five down. */
    private fun wheel(e: MouseWheelEvent) {
        if (!tracksMouse(e)) return
        wheelRemainder += e.preciseWheelRotation
        val steps = wheelRemainder.toInt()
        wheelRemainder -= steps
        val button = if (steps < 0) "FOUR" else "FIVE"
        repeat(abs(steps)) {
            onMouse(TerminalMouseInput("PRESS", button, AwtKeyTranslator.mods(e), e.x.toFloat(), e.y.toFloat(), false))
        }
    }

    private fun tracksMouse(e: MouseEvent) = frame?.mouseTracking == true && !e.isShiftDown

    private fun button(e: MouseEvent): String? = when (e.button) {
        MouseEvent.BUTTON1 -> LEFT
        MouseEvent.BUTTON2 -> "MIDDLE"
        MouseEvent.BUTTON3 -> "RIGHT"
        MouseEvent.NOBUTTON -> when {
            SwingUtilities.isLeftMouseButton(e) -> LEFT
            SwingUtilities.isMiddleMouseButton(e) -> "MIDDLE"
            SwingUtilities.isRightMouseButton(e) -> "RIGHT"
            else -> null
        }
        else -> null
    }

    private fun cellAt(e: MouseEvent) = Point((e.x / cellWidth).coerceAtLeast(0), (e.y / cellHeight).coerceAtLeast(0))

    private fun clearSelection() {
        if (selectionAnchor == null) return
        selectionAnchor = null
        selectionEnd = null
        repaint()
    }

    /** The selection's first and last cell in reading order. */
    private fun selectionRange(): Pair<Point, Point>? {
        val a = selectionAnchor ?: return null
        val b = selectionEnd ?: return null
        if (a == b) return null
        return if (a.y < b.y || (a.y == b.y && a.x <= b.x)) a to b else b to a
    }

    fun setLook(look: ConsoleLook) {
        val base = UIUtil.getFontWithFallback(look.fontName, Font.PLAIN, look.fontSize.toInt()).deriveFont(look.fontSize)
        fonts = arrayOf(base, base.deriveFont(Font.BOLD), base.deriveFont(Font.ITALIC), base.deriveFont(Font.BOLD or Font.ITALIC))
        val fm = getFontMetrics(base)
        metrics = fm
        cellWidth = max(1, fm.charWidth('W'))
        cellHeight = max(1, ceil(fm.height * look.lineSpacing).toInt())
        ascentOffset = (cellHeight - fm.height) / 2 + fm.ascent
        background = color(look.colors.background)
        selection = Color(look.selectionColor and 0xFFFFFF or (0x80 shl 24), true)
        reportSize()
        repaint()
    }

    fun show(next: ScreenFrame) {
        val previous = frame
        frame = next
        if (previous == null || previous.cols != next.cols || previous.rowCount != next.rowCount ||
            previous.defaultBackground != next.defaultBackground || previous.defaultForeground != next.defaultForeground
        ) {
            repaint()
            return
        }
        for (y in 0 until next.rowCount) {
            if (next.rows[y] !== previous.rows[y]) repaintRow(y)
        }
        if (previous.cursor != next.cursor) {
            repaintRow(previous.cursor.y)
            repaintRow(next.cursor.y)
        }
    }

    override fun getPreferredSize() = Dimension(TerminalSize.DEFAULT.cols * cellWidth, TerminalSize.DEFAULT.rows * cellHeight)

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(true))
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.editorFractionalMetricsHint)
            val frame = frame
            g.color = frame?.let { color(it.defaultBackground) } ?: background
            val clip = g.clipBounds ?: java.awt.Rectangle(0, 0, width, height)
            g.fillRect(clip.x, clip.y, clip.width, clip.height)
            if (frame == null || fonts[0] == null) return
            val first = max(0, clip.y / cellHeight)
            val last = minOf(frame.rowCount - 1, (clip.y + clip.height) / cellHeight)
            for (y in first..last) paintRow(g, frame, y)
            paintCursor(g, frame)
            paintSelection(g, frame)
        } finally {
            g.dispose()
        }
    }

    private fun paintRow(g: Graphics2D, frame: ScreenFrame, y: Int) {
        val row = frame.rows[y]
        val top = y * cellHeight
        var x = 0
        while (x < row.width) {
            val bg = background(frame, row, x)
            var end = x + 1
            while (end < row.width && background(frame, row, end) == bg) end++
            if (bg != frame.defaultBackground) {
                g.color = color(bg)
                g.fillRect(x * cellWidth, top, (end - x) * cellWidth, cellHeight)
            }
            x = end
        }
        x = 0
        while (x < row.width) {
            val flags = row.flags[x]
            val text = row.text[x]
            if (flags and ScreenRow.SPACER_FLAG != 0 || text == null || flags and ScreenRow.INVISIBLE_FLAG != 0) {
                x++
                continue
            }
            val fg = foreground(frame, row, x)
            var end = x + 1
            val batch = isAscii(text)
            if (batch) {
                while (end < row.width && row.flags[end] == flags && foreground(frame, row, end) == fg && row.text[end].let { it != null && isAscii(it) }) end++
            }
            g.color = color(fg)
            g.font = fonts[fontIndex(flags)]
            val left = x * cellWidth
            val cells = if (flags and ScreenRow.WIDE_FLAG != 0) 2 else end - x
            if (batch) drawOnGrid(g, row.text(x, end), left, top + ascentOffset)
            else if (isCellFilling(text)) drawStretched(g, text, left, top)
            else g.drawString(text, left, top + ascentOffset)
            decorate(g, flags, left, top, cells * cellWidth)
            x = if (batch) end else x + 1
        }
    }

    /**
     * Draws an ASCII run, one glyph per cell from [left], in one draw call: each glyph starts at its
     * own cell's left edge whatever the font's advance, so text stays on the grid that backgrounds,
     * selection and the caret use (design D2). Without shaping, a ligature font draws no ligatures here.
     */
    private fun drawOnGrid(g: Graphics2D, text: String, left: Int, baseline: Int) {
        val glyphs = g.font.createGlyphVector(g.fontRenderContext, text)
        for (i in 0 until glyphs.numGlyphs) {
            glyphPosition.setLocation((i * cellWidth).toFloat(), 0f)
            glyphs.setGlyphPosition(i, glyphPosition)
        }
        g.drawGlyphVector(glyphs, left.toFloat(), baseline.toFloat())
    }

    /**
     * Box-drawing and block glyphs span the font's line height; stretching them to the cell
     * height joins them across rows when line spacing adds space, as Ghostty draws them.
     */
    private fun drawStretched(g: Graphics2D, text: String, left: Int, top: Int) {
        val fm = metrics ?: return
        val saved = g.transform
        g.translate(left.toDouble(), top.toDouble())
        g.scale(1.0, cellHeight.toDouble() / fm.height)
        g.drawString(text, 0, fm.ascent)
        g.transform = saved
    }

    private fun isCellFilling(text: String) = text.length == 1 && text[0].code in 0x2500..0x259F

    private fun decorate(g: Graphics2D, flags: Int, left: Int, top: Int, width: Int) {
        val baseline = top + ascentOffset
        if (flags and ScreenRow.UNDERLINE_FLAG != 0) g.fillRect(left, baseline + 1, width, 1)
        if (flags and ScreenRow.STRIKETHROUGH_FLAG != 0) g.fillRect(left, baseline - (metrics?.ascent ?: 0) / 3, width, 1)
        if (flags and ScreenRow.OVERLINE_FLAG != 0) g.fillRect(left, top, width, 1)
    }

    /** The selection as a translucent wash in the scheme's selection colour over each selected cell. */
    private fun paintSelection(g: Graphics2D, frame: ScreenFrame) {
        val (start, end) = selectionRange() ?: return
        g.color = selection
        for (y in start.y..min(end.y, frame.rowCount - 1)) {
            val from = if (y == start.y) start.x else 0
            val to = if (y == end.y) min(end.x + 1, frame.cols) else frame.cols
            g.fillRect(from * cellWidth, y * cellHeight, (to - from) * cellWidth, cellHeight)
        }
    }

    private fun paintCursor(g: Graphics2D, frame: ScreenFrame) {
        val cursor = frame.cursor
        if (!cursor.visible || cursor.y !in 0 until frame.rowCount || cursor.x !in 0 until frame.cols) return
        val row = frame.rows[cursor.y]
        val left = cursor.x * cellWidth
        val top = cursor.y * cellHeight
        val cells = if (row.flags[cursor.x] and ScreenRow.WIDE_FLAG != 0) 2 else 1
        g.color = color(foreground(frame, row, cursor.x))
        val style = if (hasFocus()) cursor.style else CursorStyle.BLOCK_HOLLOW
        when (style) {
            CursorStyle.BAR -> g.fillRect(left, top, max(1, cellWidth / 8), cellHeight)
            CursorStyle.UNDERLINE -> g.fillRect(left, top + cellHeight - 2, cells * cellWidth, 2)
            CursorStyle.BLOCK_HOLLOW -> g.drawRect(left, top, cells * cellWidth - 1, cellHeight - 1)
            CursorStyle.BLOCK -> {
                g.fillRect(left, top, cells * cellWidth, cellHeight)
                val text = row.text[cursor.x] ?: return
                g.color = color(background(frame, row, cursor.x))
                g.font = fonts[fontIndex(row.flags[cursor.x])]
                g.drawString(text, left, top + ascentOffset)
            }
        }
    }

    private fun foreground(frame: ScreenFrame, row: ScreenRow, x: Int): Int {
        val flags = row.flags[x]
        val fg = if (flags and ScreenRow.INVERSE_FLAG != 0) row.bg[x].orDefault(frame.defaultBackground) else row.fg[x].orDefault(frame.defaultForeground)
        return if (flags and ScreenRow.FAINT_FLAG != 0) blend(fg, background(frame, row, x)) else fg
    }

    private fun background(frame: ScreenFrame, row: ScreenRow, x: Int): Int =
        if (row.flags[x] and ScreenRow.INVERSE_FLAG != 0) row.fg[x].orDefault(frame.defaultForeground) else row.bg[x].orDefault(frame.defaultBackground)

    private fun Int.orDefault(default: Int) = if (this == ScreenRow.DEFAULT_COLOR) default else this

    private fun blend(a: Int, b: Int): Int {
        fun mix(shift: Int) = (((a shr shift) and 0xFF) + ((b shr shift) and 0xFF)) / 2 shl shift
        return mix(16) or mix(8) or mix(0)
    }

    private fun fontIndex(flags: Int) = (if (flags and ScreenRow.BOLD_FLAG != 0) 1 else 0) + (if (flags and ScreenRow.ITALIC_FLAG != 0) 2 else 0)

    private fun isAscii(text: String) = text.length == 1 && text[0].code in 0x20..0x7E

    private fun color(rgb: Int): Color = colors.getOrPut(rgb) { Color(rgb) }

    private fun repaintRow(y: Int) = repaint(0, y * cellHeight, width, cellHeight)

    private fun reportSize() {
        if (width <= 0 || height <= 0) return
        val size = gridSize
        if (size == reportedSize) return
        reportedSize = size
        onSizeChange(size)
    }

    private companion object {
        const val LEFT = "LEFT"
    }
}
