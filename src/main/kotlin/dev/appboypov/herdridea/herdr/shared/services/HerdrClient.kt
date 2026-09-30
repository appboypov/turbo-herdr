package dev.appboypov.herdridea.herdr.shared.services

import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.ghostty.shared.apis.GhosttyVt
import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind
import dev.appboypov.herdridea.herdr.shared.models.HerdrLocation
import dev.appboypov.herdridea.herdr.shared.models.HerdrPanelState
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.terminal.shared.models.TerminalColors
import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalMouseInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalSize
import dev.appboypov.herdridea.terminal.shared.services.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * The panel's `herdr` client (design D3, D6): locates `herdr`, checks a project session exists,
 * runs the client under a pty and publishes the panel state and its screen frames.
 * Methods may be called from any thread; connecting runs on its own worker thread.
 */
class HerdrClient(
    private val projectDirectory: String,
    private val projectSessionName: String,
    private val configuredPath: () -> String,
    private val locator: HerdrLocator,
    private val library: () -> GhosttyVt?,
    private val platformName: String,
    initialColors: TerminalColors,
    private val onClipboard: (String) -> Unit,
) : AutoCloseable {
    private val log = HerdrLog.of(HerdrClient::class.java)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "Herdr connect").apply { isDaemon = true } }
    private val mutableState = MutableStateFlow<HerdrPanelState>(HerdrPanelState.Connecting)
    private val mutableFrame = MutableStateFlow<ScreenFrame?>(null)

    @Volatile private var session: TerminalSession? = null
    @Volatile private var generation = 0
    @Volatile private var started = false
    @Volatile var size: TerminalSize = TerminalSize.DEFAULT
        private set
    @Volatile var colors: TerminalColors = initialColors
        private set

    val state: StateFlow<HerdrPanelState> = mutableState.asStateFlow()

    /** The latest screen of the running client; null before the first client draws. */
    val frame: StateFlow<ScreenFrame?> = mutableFrame.asStateFlow()

    /** Connects to [kind] once; later calls do nothing. */
    fun startOnce(kind: HerdrSessionKind) {
        if (started) return
        started = true
        connect(kind, checkExists = true)
    }

    /**
     * Ends the current client and starts one on [kind]. With [checkExists] a project session that
     * Herdr no longer lists becomes [HerdrPanelState.SessionMissing] instead of being created again.
     */
    fun connect(kind: HerdrSessionKind, checkExists: Boolean): Future<*> {
        started = true
        return worker.submit { runConnect(kind, checkExists) }
    }

    fun resize(size: TerminalSize) {
        this.size = size
        session?.resize(size.cols, size.rows, size.cellWidthPx, size.cellHeightPx)
    }

    fun setColors(colors: TerminalColors) {
        this.colors = colors
        session?.setColors(colors)
    }

    fun key(input: TerminalKeyInput) = session?.key(input)

    fun mouse(input: TerminalMouseInput) = session?.mouse(input)

    fun paste(text: String) = session?.paste(text)

    fun input(text: String) = session?.input(text)

    /** Ends only the client; the Herdr server and its sessions keep running. */
    override fun close() {
        worker.shutdownNow()
        endSession()
    }

    private fun runConnect(kind: HerdrSessionKind, checkExists: Boolean) {
        endSession()
        val attempt = ++generation
        mutableState.value = HerdrPanelState.Connecting
        val vt = library()
        if (vt == null) {
            mutableState.value = HerdrPanelState.UnsupportedPlatform(platformName)
            return
        }
        val location = locator.locate(configuredPath())
        if (location is HerdrLocation.Missing) {
            log.info("herdr not found", "searched" to location.searched.size)
            mutableState.value = HerdrPanelState.HerdrMissing(location.searched)
            return
        }
        location as HerdrLocation.Found
        val environment = HerdrClientCommand.environment(location.environment)
        if (kind == HerdrSessionKind.PROJECT && checkExists) {
            val names = HerdrSessionList.names(location.path, environment)
            if (names != null && projectSessionName !in names) {
                mutableState.value = HerdrPanelState.SessionMissing(projectSessionName)
                return
            }
        }
        val size = size
        try {
            session = TerminalSession.start(
                vt = vt,
                command = HerdrClientCommand.command(location.path, kind, projectSessionName),
                directory = projectDirectory,
                environment = environment,
                size = size,
                colors = colors,
                onFrame = { if (attempt == generation) mutableFrame.value = it },
                onExit = { code -> onExit(attempt, kind, code) },
                onClipboard = onClipboard,
            )
            if (this.size != size) resize(this.size)
            mutableState.value = HerdrPanelState.Live(kind, if (kind == HerdrSessionKind.PROJECT) projectSessionName else null)
            log.info("herdr client started", "kind" to kind, "cols" to size.cols, "rows" to size.rows)
        } catch (e: Exception) {
            log.warn("herdr client failed to start", e, "path" to location.path)
            mutableState.value = HerdrPanelState.Exited(kind, -1)
        }
    }

    private fun onExit(attempt: Int, kind: HerdrSessionKind, code: Int) {
        if (attempt != generation) return
        log.info("herdr client exited", "kind" to kind, "code" to code)
        mutableState.value = HerdrPanelState.Exited(kind, code)
        try {
            worker.execute { if (attempt == generation) endSession() }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun endSession() {
        session?.close()
        session = null
    }
}
