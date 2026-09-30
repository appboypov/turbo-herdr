package dev.appboypov.herdridea.herdr.shared.views.herdrpanel

import dev.appboypov.herdridea.herdr.shared.models.HerdrPanelState
import dev.appboypov.herdridea.terminal.shared.models.ConsoleLook
import dev.appboypov.herdridea.terminal.shared.models.ScreenFrame
import dev.appboypov.herdridea.herdr.shared.models.HerdrKeymap
import dev.appboypov.herdridea.herdr.shared.services.HerdrKeymapService
import dev.appboypov.herdridea.terminal.shared.models.TerminalKeyInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalMouseInput
import dev.appboypov.herdridea.terminal.shared.models.TerminalSize
import kotlinx.coroutines.flow.StateFlow

/** The Herdr panel's state, and the intents it forwards to [HerdrPanelViewService] by action name. */
class HerdrPanelViewModel(private val service: HerdrPanelViewService) {
    val state: StateFlow<HerdrPanelState> = service.client.state
    val frame: StateFlow<ScreenFrame?> = service.client.frame
    val look: StateFlow<ConsoleLook> = service.look

    /** Herdr's current key bindings, re-read when its config changes. */
    val keymap: HerdrKeymap get() = HerdrKeymapService.getInstance().keymap

    fun start() = service.start()

    fun resize(size: TerminalSize) = service.resize(size)

    fun reconnect() = service.run(HerdrPanelViewService.RECONNECT)

    fun openSettings() = service.run(HerdrPanelViewService.OPEN_SETTINGS)

    fun createProjectSession() = service.run(HerdrPanelViewService.NEW_PROJECT_SESSION)

    fun switchToShared() = service.run(HerdrPanelViewService.SWITCH_SHARED)

    fun key(input: TerminalKeyInput) = service.run(HerdrPanelViewService.KEY, input.toArgs())

    /** Sends [text] to the pane as is, without the key encoder. */
    fun input(text: String) = service.run(HerdrPanelViewService.INPUT, mapOf("text" to text))

    fun mouse(input: TerminalMouseInput) = service.run(HerdrPanelViewService.MOUSE, input.toArgs())

    fun copy() = service.run(HerdrPanelViewService.COPY)

    fun paste() = service.run(HerdrPanelViewService.PASTE)

    fun attach(view: HerdrPanelView) = service.attach(view)

    fun detach(view: HerdrPanelView) = service.detach(view)
}
