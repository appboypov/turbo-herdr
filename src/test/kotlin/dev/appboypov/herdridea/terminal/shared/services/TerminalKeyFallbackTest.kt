package dev.appboypov.herdridea.terminal.shared.services

import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class TerminalKeyFallbackTest {
    private val source = object : Component() {}
    private val keys = mutableListOf<TerminalKeyInput>()
    private val texts = mutableListOf<String>()

    @Test
    fun `Given macOS, when a line-editing chord is pressed, repeated and released, then the pane gets its Ghostty byte on each press only`() {
        val spec = mapOf(KeyEvent.VK_LEFT to "\u0001", KeyEvent.VK_RIGHT to "\u0005", KeyEvent.VK_BACK_SPACE to "\u0015")

        spec.forEach { (keyCode, byte) ->
            val fallback = fallback(isMac = true)
            texts.clear()

            fallback.keyPressed(press(keyCode, CMD))
            fallback.keyPressed(press(keyCode, CMD))
            fallback.keyReleased(release(keyCode, CMD))

            assertEquals(listOf(byte, byte), texts, "key $keyCode")
        }
        assertEquals(emptyList<TerminalKeyInput>(), keys)
    }

    @Test
    fun `Given Linux, when super+left is pressed and released, then the pane gets the encoded chord`() {
        val fallback = fallback(isMac = false)

        fallback.keyPressed(press(KeyEvent.VK_LEFT, CMD))
        fallback.keyReleased(release(KeyEvent.VK_LEFT, CMD))

        assertEquals(emptyList<String>(), texts)
        assertEquals(listOf("PRESS" to AwtKeyTranslator.SUPER, "RELEASE" to AwtKeyTranslator.SUPER), keys.map { it.action to it.mods })
        assertEquals(setOf("ARROW_LEFT"), keys.map { it.key }.toSet())
    }

    @Test
    fun `Given macOS, when a line-editing key is pressed with another modifier too, then the pane gets the encoded chord`() {
        val spec = listOf(
            KeyEvent.VK_LEFT to (CMD or InputEvent.SHIFT_DOWN_MASK),
            KeyEvent.VK_RIGHT to (CMD or InputEvent.ALT_DOWN_MASK),
            KeyEvent.VK_BACK_SPACE to (CMD or InputEvent.CTRL_DOWN_MASK),
            KeyEvent.VK_LEFT to 0,
        )

        spec.forEach { (keyCode, modifiers) ->
            val fallback = fallback(isMac = true)
            keys.clear()

            fallback.keyPressed(press(keyCode, modifiers))

            assertEquals(listOf(AwtKeyTranslator.key(keyCode)), keys.map { it.key }, "key $keyCode modifiers $modifiers")
        }
        assertEquals(emptyList<String>(), texts)
    }

    @Test
    fun `Given an enabled IDE action handles a keystroke, when it is pressed, typed and released, then the pane gets nothing`() {
        val fallback = fallback(isMac = true, ideHandles = { true })

        fallback.keyPressed(press(KeyEvent.VK_COMMA, CMD))
        fallback.keyTyped(KeyEvent(source, KeyEvent.KEY_TYPED, 0, CMD, KeyEvent.VK_UNDEFINED, ','))
        fallback.keyReleased(release(KeyEvent.VK_COMMA, CMD))
        fallback.keyPressed(press(KeyEvent.VK_LEFT, CMD))

        assertEquals(emptyList<TerminalKeyInput>(), keys)
        assertEquals(emptyList<String>(), texts)
    }

    private fun fallback(isMac: Boolean, ideHandles: (KeyEvent) -> Boolean = { false }) =
        TerminalKeyFallback(isMac, ideHandles, onKey = { keys += it }, onText = { texts += it })

    private fun press(keyCode: Int, modifiers: Int) =
        KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED)

    private fun release(keyCode: Int, modifiers: Int) =
        KeyEvent(source, KeyEvent.KEY_RELEASED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED)

    private companion object {
        const val CMD = InputEvent.META_DOWN_MASK
    }
}
