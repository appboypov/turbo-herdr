## 1. Terminal canvas cell grid (Frontend Components, DAS-192, design D2)

- [ ] 1.1 `src/main/kotlin/dev/appboypov/herdridea/terminal/shared/components/TerminalCanvas.kt` -- place every glyph of a batched run, and of the block caret, at its own cell's left edge -- text, backgrounds, selection and caret share one grid (spec "Text and caret share one cell grid"); verified by 1.2 and 5.4.
- [ ] 1.2 `src/test/kotlin/dev/appboypov/herdridea/terminal/shared/components/TerminalCanvasTest.kt` -- test that a painted row puts the glyph of a late column (for example column 50) inside that column's cell, for plain and bold text, with a font whose advance is fractional -- catches the drift returning; `./gradlew test --tests '*TerminalCanvasTest*'` passes, and fails against the old batching.

## 2. Frame publishing (Backend, DAS-196, design D1)

- [ ] 2.1 `src/main/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalSession.kt` -- decide on the terminal thread when to publish, so the frame after a burst always includes the last chunk fed; keep one frame per burst -- fixes the stale rows (spec "The panel shows the latest screen after output stops"); verified by 2.2 and 5.3.
- [ ] 2.2 `src/test/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalSessionTest.kt` -- add a test where a program writes many chunks quickly, then a final marker, then waits; the last published frame shows the marker -- catches a lost last chunk; `./gradlew test --tests '*TerminalSessionTest*'` passes, and fails before 2.1.

## 3. Panel key flow (Frontend, DAS-194, design D3, D4, D5)

- [ ] 3.1 `src/main/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalKeyFallback.kt` -- reproduce first: in the sandbox IDE, log whether the Cmd+comma and Cmd+O presses reach the fallback unconsumed, and record the result in Implementation Notes; remove the log after -- confirms design D5's inferred cause before the fix; if the cause differs, stop and revise the plan.
- [ ] 3.2 `src/main/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalKeyFallback.kt` -- send nothing for a press, its typed event or its release when an enabled IDE action in the panel's context handles the keystroke -- spec "A shortcut the IDE handles sends nothing to the pane"; verified by 5.7.
- [ ] 3.3 `src/main/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalKeyFallback.kt` -- on macOS, send raw 0x01, 0x05, 0x15 for `cmd+left`, `cmd+right`, `cmd+backspace` without other modifiers on press and repeat, nothing on release; Linux unchanged -- spec "macOS line-editing shortcuts work as in Ghostty"; verified by 3.6 and 5.5.
- [ ] 3.4 `src/main/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalSession.kt` and the key path from `herdr/shared/views/herdrpanel/HerdrPanelView.kt` -- carry raw bytes from the fallback to the pty without the key encoder -- needed by 3.3 so the Kitty protocol does not change the bytes; verified by 5.5.
- [ ] 3.5 `src/main/kotlin/dev/appboypov/herdridea/herdr/shared/services/HerdrKeyClaims.kt` and `HerdrKeyRouter.kt` -- while the prefix is pending, claim only a key Herdr binds after the prefix, the prefix key or Esc; for any other key send Esc to Herdr first, then route the key as without a pending prefix; point the class docs at ADR-0004 -- spec "The key after the Herdr prefix goes to Herdr only when Herdr handles it in prefix mode", ADR-0004; verified by 3.6 and 5.6.
- [ ] 3.6 `src/test/kotlin/dev/appboypov/herdridea/herdr/shared/services/HerdrKeyClaimsTest.kt` and a new `src/test/kotlin/dev/appboypov/herdridea/terminal/shared/services/TerminalKeyFallbackTest.kt` -- test the prefix follow-up outcomes (bound follow-up, prefix twice, Esc, unbound chord, unbound plain key, non-bindable key, pending state cleared) and the macOS chord bytes against Linux behaviour and other modifiers -- catches routing regressions; `./gradlew test --tests '*HerdrKeyClaimsTest*' --tests '*TerminalKeyFallbackTest*'` passes.

## 4. Release (Infra)

- [ ] 4.1 `gradle.properties` and `build.gradle.kts` -- set `pluginVersion=0.1.1` and add 0.1.1 change notes naming the three fixes -- the issues ask for a new Marketplace version; 0.1.0 is the only published one; after `./gradlew patchPluginXml`, the patched `plugin.xml` under `build/` shows version 0.1.1 and the new notes.
- [ ] 4.2 after merge, tag `v0.1.1` on main and push it -- runs the `publish` workflow; the workflow succeeds and the Marketplace lists 0.1.1 (`curl -s https://plugins.jetbrains.com/api/plugins/34433/updates?size=1`).

## 5. Verification

- [ ] 5.1 `openspec validate das-195-fix-terminal-input-bugs --type change --strict` -- reports the change valid.
- [ ] 5.2 Crabbox `check` job from `.crabbox.yaml` (natives build plus `./gradlew check`) on the VPS -- passes; local `./gradlew check` only per the `madspec-remote-checks-and-tests` fallback rules.
- [ ] 5.3 Sandbox IDE (`./gradlew runIde`, herdrPath wrapper `exec herdr --session <isolated>`), a Herdr session with two tabs of different full-screen content: switch tabs 30 times with the Herdr tab keys, read the panel after 1 s each time with `herdr.panel.read` -- every read matches `herdr pane read` of the new tab; before the fix 30 of 32 failed.
- [ ] 5.4 Sandbox: type a line past column 50 in zsh and capture with `herdr.panel.capture` -- the caret sits directly after the last character; check once more with a bold prompt segment.
- [ ] 5.5 Sandbox, real keys (CGEvent) into `cat -v` and into omp: Cmd+Left, Cmd+Right, Cmd+Backspace -- `cat -v` shows `^A`, `^E`, `^U`; in omp's prompt `aaa bbb`, Cmd+Left then `x` gives `xaaa bbb`.
- [ ] 5.6 Sandbox, real keys: `ctrl+;` then `v` splits the pane; `ctrl+;` then Cmd+O opens Go to Class, Herdr's PREFIX bar is gone, and a later `v` types `v`; `ctrl+;` then `x` types `x`.
- [ ] 5.7 Sandbox, real keys into `cat -v`: Cmd+comma opens Settings and `cat -v` shows nothing; repeat 10 times.

## Implementation Notes

Sandbox rig for the real-key tasks (3.1, 5.3 to 5.7), prepared by the planner:

- Natives are already in `src/main/resources/native/` (gitignored). `./gradlew runIde` starts the sandbox IDE; its settings file `.intellijPlatform/sandbox/herdr-idea/IU-2026.1.2/config_runIde/options/herdr-idea.xml` points herdrPath at `/tmp/das195/herdr`, a wrapper that runs `herdr --session das195-verify`, isolated from Brian's sessions. The sandbox asks "Trust Project" first. Stop the sandbox and kill the `das195-verify` Herdr session when done.
- Named actions: `curl -s -X POST 'http://127.0.0.1:63343/api/herdr?action=<name>'` with `herdr.panel.show`, `herdr.panel.read` (screen, cursor, focused), `herdr.panel.capture` (arg `path`, PNG). Port 63342 is Brian's own IDE: never send it keys.
- Real keys: `/tmp/das195-rig/ev` (source `ev.swift` beside it) posts CGEvents: `ev act <sandbox pid>` brings the sandbox to front (Stage Manager hides it otherwise), `ev key <mac keycode> cmd,shift,ctrl,alt`, `ev text <string>`, `ev click X Y` (screen points). Keycodes: left 123, right 124, delete 51, comma 43, o 31, v 9, x 7, semicolon 41, escape 53.
- Herdr side: `HERDR_SOCKET_PATH=~/.config/herdr/sessions/das195-verify/herdr.sock herdr tab list|focus|create` and `herdr pane list|read|send-text`.

## Plan Change Log

## Review Triage Log
