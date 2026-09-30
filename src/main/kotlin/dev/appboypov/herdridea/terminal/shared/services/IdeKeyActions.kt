package dev.appboypov.herdridea.terminal.shared.services

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbService
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

/**
 * Whether an enabled IDE action handles a key press in the context of the component it was pressed
 * in, under the active keymap (design D5). On macOS the app menu can run a shortcut such as Settings
 * natively and still deliver the press unconsumed, so the terminal asks before sending it.
 *
 * Actions are updated without the key event: macOS app-menu actions such as `ShowSettings` report
 * themselves disabled for a keyboard event (`ActionPlaces.isMacSystemMenuAction`), because the menu,
 * not the key dispatcher, runs them.
 */
object IdeKeyActions {
    /** True when an action bound to [event]'s keystroke is enabled where [event] was pressed. EDT. */
    fun handles(event: KeyEvent): Boolean {
        val shortcut = KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(event), null)
        val ids = KeymapManager.getInstance()?.activeKeymap?.getActionIdList(shortcut)?.takeIf { it.isNotEmpty() } ?: return false
        val actions = ActionManager.getInstance()
        val context = DataManager.getInstance().getDataContext(event.component)
        val dumb = CommonDataKeys.PROJECT.getData(context)?.let { DumbService.isDumb(it) } == true
        return ids.any { id ->
            val action = actions.getAction(id) ?: return@any false
            if (dumb && !action.isDumbAware) return@any false
            val actionEvent = AnActionEvent.createEvent(action, context, null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)
            ActionUtil.updateAction(action, actionEvent)
            actionEvent.presentation.isEnabled
        }
    }
}
