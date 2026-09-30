package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrKeyClaim
import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyRoute
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap

/**
 * Key precedence while the Herdr panel has focus (ADR-0004, design D4). Herdr gets every keystroke
 * its config binds and Esc. The platform copy and paste keys copy and paste unless Herdr binds them.
 * Every other key belongs to the IDE.
 *
 * After the prefix, Herdr gets the next key only when it handles that key in prefix mode: a key it
 * binds after the prefix, the prefix key itself or Esc. Any other next key closes prefix mode with
 * Esc first and is then routed as if no prefix were pending.
 *
 * Remembers whether the prefix is pending, so one instance serves one panel.
 */
class HerdrKeyClaims(isMac: Boolean) {
    private val clipboardMods = if (isMac) HerdrKeyStroke.SUPER else HerdrKeyStroke.CTRL or HerdrKeyStroke.SHIFT
    private val copy = HerdrKeyStroke(HerdrKey.Char('c'), clipboardMods)
    private val paste = HerdrKeyStroke(HerdrKey.Char('v'), clipboardMods)
    private var prefixPending = false

    /** Where [stroke] goes under [keymap]; a null stroke is a key Herdr cannot bind. */
    fun route(stroke: HerdrKeyStroke?, keymap: HerdrKeymap): HerdrKeyRoute {
        if (!prefixPending) return HerdrKeyRoute(claim(stroke, keymap))
        prefixPending = false
        if (stroke != null && isPrefixFollowUp(stroke, keymap)) return HerdrKeyRoute(HerdrKeyClaim.HERDR)
        return HerdrKeyRoute(claim(stroke, keymap), closesPrefix = true)
    }

    private fun isPrefixFollowUp(stroke: HerdrKeyStroke, keymap: HerdrKeymap): Boolean =
        keymap.bindsAfterPrefix(stroke) || keymap.prefix?.let(stroke::matches) == true || stroke == ESC

    private fun claim(stroke: HerdrKeyStroke?, keymap: HerdrKeymap): HerdrKeyClaim {
        if (stroke == null) return HerdrKeyClaim.IDE
        if (keymap.bindsDirect(stroke)) {
            prefixPending = keymap.prefix?.let(stroke::matches) == true
            return HerdrKeyClaim.HERDR
        }
        return when (stroke) {
            ESC -> HerdrKeyClaim.HERDR
            copy -> HerdrKeyClaim.COPY
            paste -> HerdrKeyClaim.PASTE
            else -> HerdrKeyClaim.IDE
        }
    }

    private companion object {
        val ESC = HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.ESC), 0)
    }
}
