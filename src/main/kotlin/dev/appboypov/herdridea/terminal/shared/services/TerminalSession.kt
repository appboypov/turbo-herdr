package dev.appboypov.herdridea.terminal.shared.services

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.ghostty.shared.services.GhosttyTerminal
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalMouseInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalSize
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A program running under a pty, emulated by libghostty-vt (design D2, D3).
 *
 * One terminal thread owns every native handle: it feeds pty output, answers terminal queries
 * back to the pty, encodes input and publishes an immutable [ScreenFrame] after each batch of
 * output. Methods may be called from any thread. Callbacks run on the terminal thread.
 */
class TerminalSession private constructor(
    private val process: PtyProcess,
    private val executor: ExecutorService,
    private val onFrame: (ScreenFrame) -> Unit,
    private val onExit: (Int) -> Unit,
) : AutoCloseable {
    private val log = HerdrLog.of(TerminalSession::class.java)
    private val closed = AtomicBoolean(false)
    private val unfedChunks = AtomicInteger(0)
    private lateinit var terminal: GhosttyTerminal

    fun resize(cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) = onTerminalThread {
        terminal.resize(cols, rows, cellWidthPx, cellHeightPx)
        process.winSize = WinSize(cols, rows, cols * cellWidthPx, rows * cellHeightPx)
        publishFrame()
    }

    fun setColors(colors: TerminalColors) = onTerminalThread {
        terminal.setColors(colors.foreground, colors.background, colors.ansi.toIntArray())
        publishFrame()
    }

    fun key(input: TerminalKeyInput) = onTerminalThread {
        write(terminal.encodeKey(input.action, input.key, input.mods, input.consumedMods, input.text, input.unshiftedCodepoint))
    }

    fun mouse(input: TerminalMouseInput) = onTerminalThread {
        write(terminal.encodeMouse(input.action, input.button, input.mods, input.x, input.y, input.anyButtonPressed))
    }

    fun paste(text: String) = onTerminalThread { write(terminal.encodePaste(text)) }

    /** Ends the client process only; a Herdr server it attached to keeps running. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        process.destroy()
        try {
            executor.execute { terminal.close() }
        } catch (_: RejectedExecutionException) {
        }
        executor.shutdown()
    }

    private fun start(vt: GhosttyVt, size: TerminalSize, colors: TerminalColors, onClipboard: (String) -> Unit) {
        executor.submit {
            terminal = GhosttyTerminal(vt, size.cols, size.rows, ::write, onClipboard)
            // The mouse encoder maps pixels to cells only once it knows the cell size.
            terminal.resize(size.cols, size.rows, size.cellWidthPx, size.cellHeightPx)
            terminal.setColors(colors.foreground, colors.background, colors.ansi.toIntArray())
        }.get()
        Thread.ofPlatform().daemon().name("Herdr pty reader").start(::readLoop)
    }

    private fun readLoop() {
        val buffer = ByteArray(64 * 1024)
        val input = process.inputStream
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val chunk = buffer.copyOf(count)
                unfedChunks.incrementAndGet()
                onTerminalThread { feed(chunk) }
            }
        } catch (e: IOException) {
            if (!closed.get()) log.debug("pty read ended", "reason" to e.message)
        }
        val code = process.waitFor()
        onTerminalThread {
            publishFrame()
            onExit(code)
        }
    }

    /**
     * Feeds one output chunk and coalesces frames (design D1): the terminal thread publishes once no
     * chunk that was read is still waiting to be fed, so a burst yields one frame that includes its
     * last chunk.
     */
    private fun feed(chunk: ByteArray) {
        val lastQueued = unfedChunks.decrementAndGet() == 0
        terminal.feed(chunk)
        if (lastQueued) publishFrame()
    }

    private fun publishFrame() = onFrame(terminal.snapshot())

    private fun write(bytes: ByteArray) {
        if (bytes.isEmpty() || closed.get()) return
        try {
            process.outputStream.write(bytes)
            process.outputStream.flush()
        } catch (e: IOException) {
            log.debug("pty write failed", "reason" to e.message)
        }
    }

    private fun onTerminalThread(task: () -> Unit) {
        if (closed.get()) return
        try {
            executor.execute {
                if (closed.get()) return@execute
                try {
                    task()
                } catch (e: Exception) {
                    log.error("Terminal thread task failed", e)
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    companion object {
        /**
         * Starts [command] under a pty in [directory] with [environment], `TERM` set to
         * `xterm-ghostty` when its terminfo entry exists and `xterm-256color` otherwise.
         */
        fun start(
            vt: GhosttyVt,
            command: List<String>,
            directory: String,
            environment: Map<String, String>,
            size: TerminalSize,
            colors: TerminalColors,
            onFrame: (ScreenFrame) -> Unit,
            onExit: (Int) -> Unit,
            onClipboard: (String) -> Unit = {},
        ): TerminalSession {
            val env = environment + mapOf("TERM" to terminalName(environment), "COLORTERM" to "truecolor")
            val process = PtyProcessBuilder(command.toTypedArray())
                .setDirectory(directory)
                .setEnvironment(env)
                .setInitialColumns(size.cols)
                .setInitialRows(size.rows)
                .setConsole(false)
                .start()
            val executor = Executors.newSingleThreadExecutor { Thread(it, "Herdr terminal").apply { isDaemon = true } }
            return TerminalSession(process, executor, onFrame, onExit).also {
                it.start(vt, size, colors, onClipboard)
            }
        }

        private fun terminalName(environment: Map<String, String>): String = try {
            val probe = ProcessBuilder("infocmp", "xterm-ghostty")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .apply { environment().putAll(environment) }
                .start()
            if (probe.waitFor(5, TimeUnit.SECONDS) && probe.exitValue() == 0) "xterm-ghostty" else "xterm-256color"
        } catch (_: IOException) {
            "xterm-256color"
        }
    }
}
