package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/** Against Brian's config fixture: prefix `ctrl+;`, `prefix+v` bound, `cmd+o` unbound (ADR-0004). */
class HerdrKeyRouterTest {
    private val target = object : Component() {}
    private val keys = mutableListOf<TerminalKeyInput>()
    private val keymap = HerdrKeymapParser.parse(javaClass.getResource("/herdr/config.toml")!!.readText()).keymap
    private val router = HerdrKeyRouter(target, { keymap }, HerdrKeyClaims(isMac = true), { keys += it }, {}, {})
    private lateinit var previousFocus: KeyboardFocusManager

    @BeforeEach
    fun focusTarget() {
        previousFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(object : DefaultKeyboardFocusManager() {
            override fun getFocusOwner(): Component = target
        })
    }

    @AfterEach
    fun restoreFocus() = KeyboardFocusManager.setCurrentKeyboardFocusManager(previousFocus)

    @Test
    fun `Given the prefix was pressed, when an unbound IDE chord follows, then Herdr gets Esc and the IDE keeps the chord`() {
        router.dispatch(press(KeyEvent.VK_SEMICOLON, InputEvent.CTRL_DOWN_MASK))
        keys.clear()

        val herdrTookIt = router.dispatch(press(KeyEvent.VK_O, InputEvent.META_DOWN_MASK))

        assertFalse(herdrTookIt)
        assertEquals(listOf("PRESS" to "ESCAPE", "RELEASE" to "ESCAPE"), keys.map { it.action to it.key })
    }

    @Test
    fun `Given the prefix was pressed, when a key Herdr binds after the prefix follows, then Herdr gets only that key`() {
        router.dispatch(press(KeyEvent.VK_SEMICOLON, InputEvent.CTRL_DOWN_MASK))
        keys.clear()

        router.dispatch(press(KeyEvent.VK_V, 0))

        assertEquals(listOf("PRESS"), keys.map { it.action })
        assertFalse(keys.single().key == "ESCAPE")
    }

    private fun press(keyCode: Int, modifiers: Int) =
        KeyEvent(target, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED)
}
