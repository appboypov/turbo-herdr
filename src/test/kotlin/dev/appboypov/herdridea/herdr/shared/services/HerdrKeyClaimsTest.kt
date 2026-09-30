package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.herdr.shared.enums.HerdrKeyClaim
import dev.appboypov.herdridea.herdr.shared.enums.HerdrNamedKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKey
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyRoute
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.CTRL
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SHIFT
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeyStroke.Companion.SUPER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Against Brian's config fixture: prefix `ctrl+;`; `prefix+v` and `cmd+k` bound; `cmd+shift+f`, `cmd+1`,
 * `cmd+o`, `cmd+c`, `cmd+v` and `prefix+y` unbound.
 */
class HerdrKeyClaimsTest {
    private val keymap = HerdrKeymapParser.parse(javaClass.getResource("/herdr/config.toml")!!.readText()).keymap
    private val prefix = char(';', CTRL)
    private val esc = HerdrKeyStroke(HerdrKey.Named(HerdrNamedKey.ESC), 0)

    @Test
    fun `Given the panel has focus, when a key Herdr binds and the IDE also binds is pressed, then Herdr gets it`() {
        val claims = HerdrKeyClaims(isMac = true)

        val spec = listOf(char('k', SUPER), char('p', SUPER), char('w', SUPER), char('t', SUPER), char('e', SUPER), char('k', SUPER or SHIFT))

        assertEquals(spec.map { HERDR }, spec.map { claims.route(it, keymap) })
    }

    @Test
    fun `Given the panel has focus, when a key Herdr does not bind is pressed, then the IDE gets it`() {
        val claims = HerdrKeyClaims(isMac = true)

        assertEquals(IDE, claims.route(char('f', SUPER or SHIFT), keymap))
        assertEquals(IDE, claims.route(char('1', SUPER), keymap))
        assertEquals(IDE, claims.route(char('c', CTRL), keymap))
        assertEquals(IDE, claims.route(null, keymap))
    }

    @Test
    fun `Given the prefix was pressed, when the next key is one Herdr binds after the prefix, then Herdr gets it and the prefix clears`() {
        val claims = HerdrKeyClaims(isMac = true)

        assertEquals(listOf(HERDR, HERDR, IDE), listOf(prefix, char('v', 0), char('v', 0)).map { claims.route(it, keymap) })
    }

    @Test
    fun `Given the prefix was pressed, when the prefix is pressed again, then Herdr gets it and the prefix clears`() {
        val claims = HerdrKeyClaims(isMac = true)

        assertEquals(listOf(HERDR, HERDR, IDE), listOf(prefix, prefix, char('v', 0)).map { claims.route(it, keymap) })
    }

    @Test
    fun `Given the prefix was pressed, when Esc is pressed, then Herdr gets it and the prefix clears`() {
        val claims = HerdrKeyClaims(isMac = true)

        assertEquals(listOf(HERDR, HERDR, IDE), listOf(prefix, esc, char('v', 0)).map { claims.route(it, keymap) })
    }

    @Test
    fun `Given the prefix was pressed, when the next key is not a prefix follow-up, then prefix mode closes and the key routes as without the prefix`() {
        val spec = mapOf(
            char('o', SUPER) to IDE,
            char('y', 0) to IDE,
            null to IDE,
            char('k', SUPER) to HERDR,
            char('c', SUPER) to HerdrKeyRoute(HerdrKeyClaim.COPY),
        )

        spec.forEach { (stroke, withoutPrefix) ->
            val claims = HerdrKeyClaims(isMac = true)
            claims.route(prefix, keymap)

            assertEquals(withoutPrefix.copy(closesPrefix = true), claims.route(stroke, keymap), "after the prefix: $stroke")
            assertEquals(IDE, claims.route(char('v', 0), keymap), "the key after: $stroke")
        }
    }

    @Test
    fun `Given the panel has focus, when Esc is pressed, then Herdr gets it`() {
        assertEquals(HERDR, HerdrKeyClaims(isMac = true).route(esc, keymap))
    }

    @Test
    fun `Given each platform, when its copy and paste keys are pressed, then the panel copies and pastes`() {
        val mac = HerdrKeyClaims(isMac = true)
        val linux = HerdrKeyClaims(isMac = false)

        assertEquals(HerdrKeyClaim.COPY, mac.route(char('c', SUPER), keymap).claim)
        assertEquals(HerdrKeyClaim.PASTE, mac.route(char('v', SUPER), keymap).claim)
        assertEquals(HerdrKeyClaim.COPY, linux.route(char('c', CTRL or SHIFT), keymap).claim)
        assertEquals(HerdrKeyClaim.PASTE, linux.route(char('v', CTRL or SHIFT), keymap).claim)
    }

    private fun char(char: Char, mods: Int) = HerdrKeyStroke(HerdrKey.Char(char), mods)

    private companion object {
        val HERDR = HerdrKeyRoute(HerdrKeyClaim.HERDR)
        val IDE = HerdrKeyRoute(HerdrKeyClaim.IDE)
    }
}
