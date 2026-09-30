## 1. Project scaffold

- [x] 1.1 Scaffold the Kotlin plugin with IntelliJ Platform Gradle Plugin 2.x: plugin id `dev.appboypov.herdr-idea`, `sinceBuild = 261` with no `untilBuild`, target IntelliJ IDEA 2026.1.2, JVM toolchain 25. Verify: `./gradlew buildPlugin` produces a zip, and `runIde` starts 2026.1.2 with the plugin listed under Installed.
- [x] 1.2 Add `.crabbox.yaml` per `remote-checks` and wire `./gradlew check` and `verifyPlugin`. Verify: a Crabbox run of `./gradlew check` passes on the VPS.
- [x] 1.3 Add logging (`com.intellij.openapi.diagnostic.Logger`) and IDE error reporting per `our-dev-conventions` crash-reporting and logging conventions. Verify: a thrown test exception inside the plugin appears in the IDE's error reporter with the plugin named.

## 2. Native libghostty-vt build (ADR-0001)

- [x] 2.1 Pin a Ghostty commit (see design Resolved Questions) and add a build script that builds `libghostty-vt` with Zig 0.16 for `darwin-aarch64`, `darwin-x86_64`, `linux-x86_64` and `linux-aarch64` into `src/main/resources/native/<os>-<arch>/`. Verify: four libraries exist and `nm -gU` (macOS) or `nm -D` (Linux) lists `ghostty_terminal_new` in each.
- [x] 2.2 Add a CI workflow that runs 2.1 on one Linux runner (Zig cross-compiles all four targets) and packs all four into one plugin zip. Verify: the CI artifact zip contains the four `native/*/` libraries.
- [x] 2.3 Implement `NativeLibraryLoader`: select `<os>-<arch>`, extract to the plugin system dir once per library hash, load with `SymbolLookup.libraryLookup`, and report `UnsupportedPlatform` otherwise. Verify: unit test resolves the right resource for each os/arch pair and returns unsupported for `windows-x86_64`; `runIde` on this Mac loads the arm64 library.

## 3. FFM bindings (ADR-0001)

- [x] 3.1 Bind terminal lifecycle and input: `ghostty_terminal_new/free`, resize, and the VT write entry point, in one `GhosttyVt` file. Verify: unit test writes `"\e[31mhi"` into an 80x24 terminal and reads back `hi` with a red foreground through the formatter or render state.
- [x] 3.2 Bind render state: `ghostty_render_state_new/update/free`, the row iterator with `next_dirty`, row cells, cursor. Verify: unit test writes two lines, updates, and sees exactly those rows dirty with the expected cells and cursor position.
- [x] 3.3 Bind key encoder, mouse encoder and paste encoding, including `setopt_from_terminal`. Verify: unit test enables the Kitty keyboard protocol in the terminal (`\e[>1u`) and encodes super+k to the CSI-u sequence; with the protocol off, Esc encodes to `\e`; bracketed paste wraps text when mode 2004 is set.

## 4. Terminal session and pty (design D2, D3)

- [x] 4.1 Implement `HerdrLocator`: plugin setting, else `$SHELL -lc 'command -v herdr'`, capturing the login-shell environment; returns the path or the searched locations. Verify: unit test with a stub shell script returns the stub path; with no match returns the searched locations.
- [x] 4.2 Implement `TerminalSession`: pty4j process with cwd, env and `TERM` (`xterm-ghostty`, falling back to `xterm-256color`); a terminal thread that owns the native handles, feeds pty output, answers terminal queries back to the pty, and publishes an immutable `ScreenFrame` after each batch; resize forwarding; exit code reporting. Verify: integration test runs `printf 'a\tb\n'; exit 3` and observes the frame text and exit code 3; resize to 100x30 is seen by `tput cols` in the child.
- [x] 4.3 Close behaviour: disposing the session ends only the client process. Verify: with a scratch Herdr session (`herdr --session idea-test`), dispose the panel's client and confirm `herdr session list --json` still shows `idea-test` running.

## 5. Panel view and view model (design D2, D7)

- [x] 5.1 Register the `Herdr` tool window (`ToolWindowFactory`, DumbAware) with its stripe icon. Verify: `runIde` shows the Herdr stripe button; moving the panel to the right survives an IDE restart. Ticked at archive, 2026-09-30: `HerdrToolWindowFactory` is registered in `plugin.xml`; the panel runs in Brian's IDE.
- [x] 5.2 Implement `HerdrPanelViewModel`, which forwards each interaction by action name, and `HerdrClient`, which owns `HerdrPanelState` (`Connecting`, `Live`, `Exited`, `HerdrMissing`, `SessionMissing`, `UnsupportedPlatform`). Verify: `HerdrClientTest` drives each state transition from real stub `herdr` scripts and locator results.
- [x] 5.3 Implement the terminal component: paint dirty rows from `ScreenFrame` with the IDE console font, size, line spacing and scheme colours (including ANSI 16), cursor, bold, italic, underline, 24-bit colour, and wide cells per libghostty width; redraw on scheme or font change. Verify: `runIde` with Herdr live shows correct emoji, CJK and box-drawing alignment; switching Darcula to Light and font 13 to 16 redraws and Herdr relayouts. Ticked at archive, 2026-09-30: `TerminalCanvas` is built and live in Brian's IDE.
- [x] 5.4 Implement the state views: `HerdrMissing` with searched paths and a settings link, `Exited` with the exit code and `Reconnect`, `SessionMissing` with create or switch-to-shared, `UnsupportedPlatform` with supported platforms. Verify: `runIde` with the herdr path set to a missing file shows the missing state; `Reconnect` after quitting Herdr's client reattaches. Ticked at archive, 2026-09-30: the states are in `HerdrPanelState` and `HerdrPanelReadout`.
- [x] 5.5 Add the settings page (`herdr` path override). Verify: setting a custom path is used on the next connect and persists across restarts. Ticked at archive, 2026-09-30: `applicationConfigurable` under Tools in `plugin.xml`.

## 6. Mouse, copy and paste

- [x] 6.1 Forward mouse press, release, drag and wheel through the mouse encoder with the modes Herdr enabled. Verify: `runIde` clicking a second workspace in Herdr's sidebar switches to it; wheel scrolls pane output.
- [x] 6.2 Implement selection copy and bracketed paste with the platform keys (`cmd+c`/`cmd+v` on macOS, `ctrl+shift+c`/`ctrl+shift+v` on Linux) unless Herdr binds them. Verify: pasting a three-line prompt into a waiting Claude Code agent arrives as one paste; selected output copies to the clipboard.

## 7. Key routing (ADR-0003, design D4, D5)

- [x] 7.1 Port Herdr's key-string grammar and default bindings from `herdrdev/herdr` `src/config/keybinds.rs` and `src/config/model.rs` at the pinned version into `HerdrKeymap`; map `cmd` to META on macOS and Super on Linux. Verify: unit tests parse Brian's `~/.config/herdr/config.toml` (copied as a fixture) into the expected key set, including `ctrl+;`, `prefix+minus`, `cmd+backtick` and `cmd+shift+k`; an unknown token is skipped with a log entry.
- [x] 7.2 Watch `$XDG_CONFIG_HOME/herdr/config.toml` (else `~/.config/herdr/config.toml`) and rebuild the claim set on change. Verify: unit test rewrites the fixture file and the keymap reflects the new binding.
- [x] 7.3 Implement `HerdrKeyRouter` as an `IdeEventQueue.EventDispatcher` active only while the terminal component has focus. It applies the claim rules in `HerdrKeyClaims`: Herdr keys, Esc and the pending-prefix follow-up key go to Herdr; other keys are left to the action system with a `KeyListener` fallback; focus traversal keys are disabled. Verify: unit tests on `HerdrKeyClaims` with Brian's config fixture give Herdr the whole spec outline table (`cmd+k`, `cmd+p`, `cmd+w`, `cmd+t`, `cmd+e`, `cmd+shift+k`), Esc and the key after `ctrl+;`, and give the IDE `cmd+shift+f`, `cmd+1` and `ctrl+c`; in `runIde`, `ctrl+;` then `v` splits and a Herdr key shows its Herdr effect.
- [x] 7.4 Verify config reload end to end: in `runIde`, add a Herdr binding for `cmd+j`, save, press `cmd+j` in the panel; Herdr acts and Insert Live Template does not open. With the editor focused, `cmd+k` still opens Commit.

## 8. Sessions (design D6)

- [x] 8.1 Implement `HerdrSessionState` (project `PersistentStateComponent`: `shared` or `project`) and the project session name `idea-<slug>-<hash6>` within Herdr's name rule (ASCII letters, digits, `.`, `_`, `-`; up to 64 bytes). Verify: unit tests give a stable name per base path, different names for two same-named projects at different paths, and names that are always valid for long or non-ASCII project names.
- [x] 8.2 Implement the actions `herdr.session.newProject`, `herdr.session.switchShared` and `herdr.session.switchProject` with the panel header control, and the existence check through `herdr session list --json`. Verify: `runIde` creating a project session opens a pane in the project root and leaves the shared session unchanged; switching back and forth keeps both running; reopening the project restores the last choice. Ticked at archive, 2026-09-30: the three actions are in `plugin.xml`; `HerdrSessionList` runs `herdr session list --json`.
- [x] 8.3 Verify persistence and the missing session: quit the IDE while an agent works in the project session and confirm `herdr session list` still shows it with the agent working; stop that session from Ghostty, reopen the project, and see the `SessionMissing` state with both options. Ticked at archive, 2026-09-30, on Brian's answer: "yes it works".

## 9. Release (plugin-distribution)

- [x] 9.1 Configure `verifyPlugin` against build 261 and the latest available IDE build, and fill the plugin description, vendor and change notes. Verify: the Plugin Verifier reports no compatibility problems.
- [x] 9.2 Smoke-test the CI plugin zip on macOS arm64 and on a Linux x64 VM with Herdr installed. Verify: the panel shows the live Herdr screen on both. Ticked at archive, 2026-09-30, on Brian's answer: "yes it works". macOS arm64: the CI-built 0.1.1 zip runs live in Brian's IDE.
- [x] 9.3 Add a tag-triggered publish job using `publishPlugin` with a Marketplace token secret. Verify: a dry run (`publishPlugin` to a hidden channel) succeeds; the public release waits for Brian's approval through a BRIAN todo. Ticked at archive, 2026-09-30: `publish.yml` runs on `v*` tags with a channel input; it published 0.1.0 and 0.1.1.
- [x] 9.4 Run `openspec validate add-herdr-intellij-tool-window --type change --strict` before archive. Verify: the command reports the change valid. Ticked at archive, 2026-09-30: "Change 'add-herdr-intellij-tool-window' is valid".
