package dev.appboypov.herdridea.terminal.shared.services

import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * Sends keys no IDE action consumed to the terminal (design D4). A press that types nothing, or one
 * with ctrl, alt or command, is sent on press. A typing press waits for its typed event and is sent
 * with that text. A typed event whose press an IDE action took is dropped.
 *
 * A press that [ideHandles] reports an enabled IDE action for sends nothing, and neither do its
 * typed event and release (design D5): macOS can run such a shortcut from its menu and still deliver
 * the press unconsumed.
 *
 * On macOS, `cmd+left`, `cmd+right` and `cmd+backspace` send line start, line end and delete line as
 * raw text to [onText] on press and repeat, and nothing on release, as Ghostty's macOS text bindings
 * do (design D3); the key encoder never sees them, so no keyboard protocol changes the bytes.
 */
class TerminalKeyFallback(
    private val isMac: Boolean,
    private val ideHandles: (KeyEvent) -> Boolean,
    private val onKey: (TerminalKeyInput) -> Unit,
    private val onText: (String) -> Unit,
) : KeyListener {
    private var typingPress: KeyEvent? = null
    private val pressed = HashSet<Int>()

    override fun keyPressed(e: KeyEvent) {
        typingPress = null
        if (e.isConsumed || AwtKeyTranslator.isModifierOnly(e) || ideHandles(e)) return
        val lineEditing = lineEditingText(e)
        if (lineEditing != null) {
            onText(lineEditing)
            e.consume()
            return
        }
        if (AwtKeyTranslator.text(e) != null) {
            typingPress = e
            return
        }
        send(e, null)
        e.consume()
    }

    override fun keyTyped(e: KeyEvent) {
        val press = typingPress ?: return
        typingPress = null
        if (e.isConsumed || e.keyChar == KeyEvent.CHAR_UNDEFINED || e.keyChar.isISOControl()) return
        send(press, e.keyChar.toString())
        e.consume()
    }

    override fun keyReleased(e: KeyEvent) {
        if (!pressed.remove(e.keyCode)) return
        onKey(AwtKeyTranslator.input(e, "RELEASE", null))
        e.consume()
    }

    private fun send(event: KeyEvent, text: String?) {
        val action = if (!pressed.add(event.keyCode)) "REPEAT" else "PRESS"
        onKey(AwtKeyTranslator.input(event, action, text))
    }

    private fun lineEditingText(event: KeyEvent): String? =
        if (isMac && AwtKeyTranslator.mods(event) == AwtKeyTranslator.SUPER) MAC_LINE_EDITING[event.keyCode] else null

    private companion object {
        /** Ghostty's macOS defaults: `text:\x01`, `text:\x05` and `text:\x15`. */
        val MAC_LINE_EDITING = mapOf(
            KeyEvent.VK_LEFT to "\u0001",
            KeyEvent.VK_RIGHT to "\u0005",
            KeyEvent.VK_BACK_SPACE to "\u0015",
        )
    }
}
