## Why

Brian works in the Herdr panel all day, and three terminal faults make it worse than Ghostty with the same Herdr session: after a Herdr tab switch the bottom rows keep the old tab's content, the caret drifts away from typed text on long lines, and macOS line-editing shortcuts plus IDE shortcuts after the Herdr prefix do not work (DAS-195: DAS-196, DAS-192, DAS-194). Each fault was reproduced on 2026-09-30 in a sandbox IntelliJ with real key events.

Success signal: in the Herdr panel, a Herdr tab switch shows the new tab completely, the caret sits right after the last typed character at any column, Cmd+Left/Right/Backspace move to line start, move to line end and delete the line as in Ghostty, and an IDE shortcut pressed after `ctrl+;` runs in IntelliJ.

## What Changes

- The panel shows the terminal's latest output after every burst of output, so no rows stay stale until the next keypress (DAS-196).
- Text, backgrounds, selection and the caret line up on the same cell grid at every column (DAS-192).
- On macOS, Cmd+Left, Cmd+Right and Cmd+Backspace send line start, line end and delete line to the pane, as Ghostty's macOS defaults do (DAS-194).
- After the Herdr prefix, only a key Herdr binds after the prefix goes to Herdr; any other key keeps its normal routing (DAS-194).
- A shortcut the IDE handles, such as Cmd+comma for Settings, sends nothing to the pane (DAS-194).
- A new plugin version with these fixes is published on the JetBrains Marketplace.

## Capabilities

### New Capabilities
- `herdr-key-routing`: adds requirements for macOS line-editing shortcuts, the key after the Herdr prefix, and IDE-handled shortcuts. The capability of the same name is introduced by the unarchived change `add-herdr-intellij-tool-window`; this change adds requirements under new names.
- `herdr-tool-window`: adds requirements that the panel shows the latest screen after output and that text and caret share one cell grid. Same relation to `add-herdr-intellij-tool-window` as above.

### Modified Capabilities

None.

## Impact

- Terminal session output-to-frame path, the Swing terminal canvas, the Herdr key claims, and the terminal key path for macOS chords.
- No change to Herdr, omp, Ghostty or the named actions.
- Release: a `v*` tag runs the existing `publish` pipeline.

## Constraints

- Herdr's config still decides which keys Herdr claims (ADR-0003); only the prefix follow-up rule changes, recorded in a new ADR.
- The screen stays drawn by the plugin in Swing from libghostty-vt frames (ADR-0002).
- Key behaviour matches Ghostty on macOS with Brian's config: Ghostty's alt+left and alt+right text defaults are not ported, because Brian unbinds them and Herdr binds those keys.
- The macOS line-editing chords apply on macOS only, as in Ghostty.

## Non-goals

- DAS-191 (omp ask answer empty) and DAS-193 (Cmd+V paste): verified as not plugin bugs and closed.
- Porting Ghostty's user config or any other Ghostty keybinding.
- Changing omp's ask dialog or Herdr's key handling.

## Sources

- Intent record: `/Users/codaveto/Brainspace/intents/herdr-intellij-tool-window/herdr-intellij-tool-window.md` (User requests, 2026-09-30, with the verification results).
- Linear issues DAS-195, DAS-196, DAS-192, DAS-194 and their verification comments.
- Ghostty macOS default keybinds: `.cache/ghostty/src/config/Config.zig` lines 7313-7342 (pinned commit `4ae9f1a2de5484de3d6a13fe03676b8853b9c41c`).
- ADRs: `adr/0002-draw-screen-in-swing-from-render-state.md`, `adr/0003-herdr-config-decides-key-precedence.md`.
