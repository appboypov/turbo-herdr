package dev.appboypov.herdridea.herdr.shared.views.herdrpanel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import dev.appboypov.herdridea.core.services.HerdrLog
import dev.appboypov.herdridea.ghostty.shared.services.GhosttyLibrary
import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind
import dev.appboypov.herdridea.herdr.shared.exceptions.HerdrActionException
import dev.appboypov.herdridea.herdr.shared.services.HerdrClient
import dev.appboypov.herdridea.herdr.shared.services.HerdrLocator
import dev.appboypov.herdridea.herdr.shared.services.HerdrSessionState
import dev.appboypov.herdridea.herdr.shared.services.HerdrSettings
import dev.appboypov.herdridea.herdr.shared.views.herdrsettings.HerdrSettingsPage
import dev.appboypov.herdridea.terminal.shared.models.ConsoleLook
import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalMouseInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalSize
import dev.appboypov.herdridea.terminal.shared.services.ConsoleLookReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Herdr panel's action service (design D7): one registry from action name to handler. The panel,
 * its IDE actions and the dispatcher all run interactions through [run], so each one has a single
 * handler whether or not the panel is on screen. It lives as long as the project.
 */
@Service(Service.Level.PROJECT)
class HerdrPanelViewService(private val project: Project) : Disposable {
    private val log = HerdrLog.of(HerdrPanelViewService::class.java)
    private val sessionState = HerdrSessionState.getInstance(project)
    private val mutableLook = MutableStateFlow(ConsoleLookReader.read())
    private var view: HerdrPanelView? = null

    /** The console font and colours the panel draws with; follows the IDE colour scheme. */
    val look: StateFlow<ConsoleLook> = mutableLook.asStateFlow()

    val client = HerdrClient(
        projectDirectory = project.basePath ?: System.getProperty("user.home"),
        projectSessionName = sessionState.projectSessionName,
        configuredPath = { HerdrSettings.getInstance().herdrPath },
        locator = HerdrLocator(),
        library = { GhosttyLibrary.getInstance().vt },
        platformName = GhosttyLibrary.getInstance().platformName,
        initialColors = look.value.colors,
        onClipboard = { text -> ApplicationManager.getApplication().invokeLater { CopyPasteManager.getInstance().setContents(StringSelection(text)) } },
    )

    private val handlers: Map<String, (Map<String, String>) -> Any?> = mapOf(
        RECONNECT to { _ -> client.connect(sessionState.kind, checkExists = true); null },
        NEW_PROJECT_SESSION to { _ -> switchTo(HerdrSessionKind.PROJECT, checkExists = false) },
        SWITCH_SHARED to { _ -> switchTo(HerdrSessionKind.SHARED, checkExists = true) },
        SWITCH_PROJECT to { _ -> switchTo(HerdrSessionKind.PROJECT, checkExists = true) },
        OPEN_SETTINGS to { _ -> openSettings() },
        SHOW_PANEL to { _ -> showPanel() },
        CAPTURE to { args -> capture(args) },
        READ to { _ -> read() },
        KEY to { args -> client.key(parsed { TerminalKeyInput.fromArgs(args) }); null },
        INPUT to { args -> client.input(parsed { requireNotNull(args["text"]) { "Missing argument: text" } }); null },
        MOUSE to { args -> client.mouse(parsed { TerminalMouseInput.fromArgs(args) }); null },
        PASTE to { args -> paste(args) },
        COPY to { _ -> copy() },
    )

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshLook() })
    }

    /** Reads action arguments; a missing or malformed one is the caller's error, not a plugin failure. */
    private fun <T> parsed(read: () -> T): T = try {
        read()
    } catch (e: IllegalArgumentException) {
        throw HerdrActionException(e.message ?: "Invalid arguments")
    }

    /** Every registered action name. */
    val actionNames: Set<String> get() = handlers.keys

    /** The session kind this project's panel attaches to. */
    val sessionKind: HerdrSessionKind get() = sessionState.kind

    /** Runs the handler registered for [name] with [args]. EDT. */
    fun run(name: String, args: Map<String, String> = emptyMap()): Any? {
        val handler = handlers[name] ?: throw HerdrActionException("Unknown action: $name")
        log.debug("action", "name" to name)
        return handler(args)
    }

    /** Starts the remembered session the first time the panel is shown. */
    fun start() = client.startOnce(sessionState.kind)

    fun resize(size: TerminalSize) = client.resize(size)

    fun attach(view: HerdrPanelView) {
        this.view = view
    }

    fun detach(view: HerdrPanelView) {
        if (this.view === view) this.view = null
    }

    override fun dispose() = client.close()

    private fun switchTo(kind: HerdrSessionKind, checkExists: Boolean): Any? {
        sessionState.kind = kind
        client.connect(kind, checkExists)
        return null
    }

    private fun openSettings(): Any? {
        // The settings dialog is modal; open it after the caller returns.
        ApplicationManager.getApplication().invokeLater { ShowSettingsUtil.getInstance().showSettingsDialog(project, HerdrSettingsPage::class.java) }
        return null
    }

    private fun showPanel(): Any? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(HerdrToolWindowFactory.ID)
            ?: throw HerdrActionException("The Herdr tool window is not registered")
        toolWindow.activate(null)
        return null
    }

    private fun capture(args: Map<String, String>): Any {
        val view = view?.takeIf { it.isShowing } ?: throw HerdrActionException("The Herdr panel is not visible; run $SHOW_PANEL first")
        val path = args["path"]?.let(Path::of) ?: Files.createTempFile("herdr-panel-", ".png")
        view.capture(path)
        return path.toAbsolutePath().toString()
    }

    /** Pastes the `text` argument, or the clipboard's text without one. */
    private fun paste(args: Map<String, String>): Any? {
        val text = args["text"] ?: CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor) ?: return null
        client.paste(text)
        return null
    }

    /** Puts the panel's local selection on the clipboard and returns it; null when nothing is selected. */
    private fun copy(): String? {
        val text = view?.selectedText() ?: return null
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        return text
    }

    private fun read(): Map<String, Any?> {
        val frame = client.frame.value
        val view = view
        return mapOf(
            "state" to HerdrPanelReadout.state(client.state.value),
            "sessionKind" to sessionState.kind.name,
            "projectSession" to sessionState.projectSessionName,
            "visible" to (view?.isShowing ?: false),
            "focused" to (view?.hasTerminalFocus ?: false),
            "cols" to frame?.cols,
            "rows" to frame?.rowCount,
            "cursor" to frame?.cursor?.let { mapOf("x" to it.x, "y" to it.y, "visible" to it.visible, "style" to it.style.name) },
            "screen" to frame?.text()?.lines(),
            "mouseTracking" to frame?.mouseTracking,
            "selection" to view?.selectedText(),
        )
    }

    private fun refreshLook() {
        val look = ConsoleLookReader.read()
        mutableLook.value = look
        client.setColors(look.colors)
    }

    companion object {
        const val RECONNECT = "herdr.client.reconnect"
        const val NEW_PROJECT_SESSION = "herdr.session.newProject"
        const val SWITCH_SHARED = "herdr.session.switchShared"
        const val SWITCH_PROJECT = "herdr.session.switchProject"
        const val OPEN_SETTINGS = "herdr.settings.open"
        const val SHOW_PANEL = "herdr.panel.show"
        const val CAPTURE = "herdr.panel.capture"
        const val READ = "herdr.panel.read"
        const val KEY = "herdr.terminal.key"
        const val INPUT = "herdr.terminal.input"
        const val MOUSE = "herdr.terminal.mouse"
        const val PASTE = "herdr.terminal.paste"
        const val COPY = "herdr.terminal.copy"

        fun getInstance(project: Project): HerdrPanelViewService = project.service()
    }
}
