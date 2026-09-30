package dev.appboypov.herdridea.herdr.shared.views.herdrpanel

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import dev.appboypov.herdridea.core.services.HerdrIdeaBundle
import dev.appboypov.herdridea.herdr.shared.enums.HerdrSessionKind
import dev.appboypov.herdridea.herdr.shared.models.HerdrPanelState
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.util.SystemInfo
import dev.appboypov.herdridea.herdr.shared.services.HerdrKeyClaims
import dev.appboypov.herdridea.herdr.shared.services.HerdrKeyRouter
import dev.appboypov.herdridea.terminal.shared.components.TerminalCanvas
import dev.appboypov.herdridea.terminal.shared.services.IdeKeyActions
import dev.appboypov.herdridea.terminal.shared.services.TerminalKeyFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.CardLayout
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JPanel

/** The Herdr panel: the live terminal, or a message for every state without a live client. */
class HerdrPanelView(private val viewModel: HerdrPanelViewModel, private val content: Content, parent: Disposable) {
    private val cards = CardLayout()
    private val terminal = TerminalCanvas(viewModel::resize) { viewModel.mouse(it) }
    private val status = JBPanelWithEmptyText()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT + ModalityState.any().asContextElement())

    val component = JPanel(cards).apply {
        add(terminal, TERMINAL)
        add(status, STATUS)
    }

    val isShowing: Boolean get() = component.isShowing

    val hasTerminalFocus: Boolean get() = terminal.hasFocus()

    /** The component that takes keyboard focus when the panel is activated. */
    val focusTarget: javax.swing.JComponent get() = terminal

    /** The text selected locally in the terminal, or null. */
    fun selectedText(): String? = terminal.selectedText()

    init {
        viewModel.attach(this)
        Disposer.register(parent) {
            scope.cancel()
            viewModel.detach(this)
        }
        val router = HerdrKeyRouter(
            target = terminal,
            keymap = { viewModel.keymap },
            claims = HerdrKeyClaims(SystemInfo.isMac),
            onKey = { viewModel.key(it) },
            onCopy = { viewModel.copy() },
            onPaste = { viewModel.paste() },
        )
        IdeEventQueue.getInstance().addDispatcher(router, parent)
        terminal.addKeyListener(TerminalKeyFallback(ideHandles = IdeKeyActions::handles, onKey = { viewModel.key(it) }))
        scope.launch { viewModel.look.collect { look -> terminal.setLook(look); status.background = terminal.background } }
        scope.launch { viewModel.frame.collect { frame -> frame?.let(terminal::show) } }
        scope.launch { viewModel.state.collect(::render) }
        viewModel.start()
    }

    /** Writes a PNG of what the panel shows now to [path]. EDT. */
    fun capture(path: Path) {
        val image = UIUtil.createImage(component, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            component.paint(g)
        } finally {
            g.dispose()
        }
        ImageIO.write(image, "png", path.toFile())
    }

    private fun render(state: HerdrPanelState) {
        content.displayName = when (state) {
            is HerdrPanelState.Live -> sessionTitle(state.kind)
            is HerdrPanelState.Exited -> sessionTitle(state.kind)
            else -> ""
        }
        if (state is HerdrPanelState.Live) {
            cards.show(component, TERMINAL)
            return
        }
        val text = status.emptyText
        text.clear()
        when (state) {
            HerdrPanelState.Connecting -> text.appendLine(HerdrIdeaBundle.message("panel.connecting"))
            is HerdrPanelState.HerdrMissing -> {
                text.appendLine(HerdrIdeaBundle.message("panel.missing.title"))
                text.appendLine(HerdrIdeaBundle.message("panel.missing.searched"), SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
                state.searched.forEach { text.appendLine(it, SimpleTextAttributes.GRAYED_ATTRIBUTES, null) }
                text.appendLine(HerdrIdeaBundle.message("panel.missing.settings"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { viewModel.openSettings() }
            }
            is HerdrPanelState.Exited -> {
                text.appendLine(HerdrIdeaBundle.message("panel.exited.title", state.exitCode))
                text.appendLine(HerdrIdeaBundle.message("panel.reconnect"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { viewModel.reconnect() }
            }
            is HerdrPanelState.SessionMissing -> {
                text.appendLine(HerdrIdeaBundle.message("panel.sessionMissing.title", state.sessionName))
                text.appendLine(HerdrIdeaBundle.message("panel.sessionMissing.create"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { viewModel.createProjectSession() }
                text.appendLine(HerdrIdeaBundle.message("panel.sessionMissing.shared"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { viewModel.switchToShared() }
            }
            is HerdrPanelState.UnsupportedPlatform -> {
                text.appendLine(HerdrIdeaBundle.message("panel.unsupported.title", state.platform))
                text.appendLine(HerdrIdeaBundle.message("panel.unsupported.supported"), SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
            }
            is HerdrPanelState.Live -> Unit
        }
        cards.show(component, STATUS)
        status.repaint()
    }

    private fun sessionTitle(kind: HerdrSessionKind) = when (kind) {
        HerdrSessionKind.SHARED -> HerdrIdeaBundle.message("panel.session.shared")
        HerdrSessionKind.PROJECT -> HerdrIdeaBundle.message("panel.session.project")
    }

    private companion object {
        const val TERMINAL = "terminal"
        const val STATUS = "status"
    }
}
