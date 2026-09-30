package dev.appboypov.herdridea.terminal.shared.services

import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.enums.NativePlatform
import dev.appboypov.herdridea.ghostty.shared.services.NativeLibraryLoader
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import dev.appboypov.herdridea.terminal.shared.models.TerminalSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TerminalSessionTest {
    private val frame = AtomicReference<ScreenFrame>()
    private val exit = CompletableFuture<Int>()

    @Test
    fun `given a program that prints and exits, when it runs, then the frame shows its output and the exit code is reported`() {
        start("printf 'a\\tb\\n'; exit 3").use {
            assertEquals(3, exit.get(10, TimeUnit.SECONDS))
            assertEquals("a       b", frame.get().rows[0].text().trimEnd())
        }
    }

    @Test
    fun `given a running program, when the session is resized, then the program sees the new size`() {
        start("read x; echo \"size \$(tput cols)x\$(tput lines)\"; read y").use { session ->
            session.resize(100, 30, 8, 16)
            session.paste("\n")

            awaitText("size 100x30")
        }
    }

    @Test
    fun `given a busy terminal thread, when a program writes many chunks quickly and then a marker and waits, then the last frame shows the marker`() {
        // A slow frame sink keeps the terminal thread busy, so later chunks queue up behind a publish.
        val slowFrames: (ScreenFrame) -> Unit = {
            frame.set(it)
            Thread.sleep(300)
        }
        start("for i in 1 2 3 4 5 6 7 8; do printf \"chunk \$i \"; sleep 0.05; done; printf 'END-OF-BURST'; read x", slowFrames).use {
            awaitText("END-OF-BURST")
        }
    }

    private fun start(script: String, onFrame: (ScreenFrame) -> Unit = frame::set) = TerminalSession.start(
        vt,
        listOf("/bin/sh", "-c", script),
        System.getProperty("user.home"),
        System.getenv() + ("TERM" to "xterm-256color"),
        TerminalSize(80, 24, 8, 16),
        TerminalColors(0xFFFFFF, 0x000000, List(16) { 0x808080 }),
        onFrame = onFrame,
        onExit = { exit.complete(it) },
    )

    private fun awaitText(text: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (frame.get()?.text()?.contains(text) == true) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for '$text'; screen was:\n${frame.get()?.text()}")
    }

    companion object {
        private lateinit var vt: GhosttyVt

        @JvmStatic
        @BeforeAll
        fun load() {
            vt = NativeLibraryLoader(NativePlatform.current()!!, Files.createTempDirectory("ghostty-test")).load()
        }
    }
}
