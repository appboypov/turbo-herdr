Owning intent: `/Users/codaveto/Work/intents/herdr-intellij-tool-window/herdr-intellij-tool-window.md`

## Why

Brian runs his coding agents in Herdr inside Ghostty, next to IntelliJ. Switching windows between the IDE and the agents breaks flow. A Herdr screen docked inside IntelliJ, like the Project panel, keeps the agents beside the code. IntelliJ's own Ghostty-based terminal exists only on its unreleased `master`, and it swallows Herdr's `cmd` shortcuts as IDE actions, so it cannot serve today.

## What Changes

- New IntelliJ Platform plugin `herdr-idea`, published on JetBrains Marketplace, for IntelliJ Platform 2026.1 (build 261) and later on macOS and Linux (arm64 and x64).
- A docked `Herdr` tool window that runs the `herdr` client in a pseudo-terminal and draws its screen. Terminal emulation (parsing, screen state, key and mouse encoding) comes from Ghostty's `libghostty-vt`, which the plugin bundles natively for each platform. Font and colours follow the IDE's console font and colour scheme; Ghostty's user config is not read.
- The panel attaches to the shared Herdr session (the one Ghostty uses) by default. The user can create a project-only Herdr session for the open project and switch the panel between the shared and project session from the panel header. The project session outlives IntelliJ and reattaches when the project reopens.
- While the panel has focus, every key bound in Herdr's config (`[keys]` and `[[keys.command]]` in `~/.config/herdr/config.toml`) and Esc go to Herdr, ahead of IntelliJ shortcuts. Keys Herdr does not bind keep their IntelliJ meaning, and reach the terminal when IntelliJ has no action for them.
- When `herdr` cannot be started, the panel says so and names the path it tried.

Assumptions recorded here rather than asked:
- On reopen, the panel shows the session it last showed for that project.
- A project session is named from the project so that the same project always maps to the same session.
- Copy and paste inside the panel use the platform's copy and paste keys (`cmd+c`/`cmd+v` on macOS, `ctrl+shift+c`/`ctrl+shift+v` on Linux) unless Herdr binds them.
- Changes to Herdr's config take effect in the panel without restarting IntelliJ.

## Capabilities

### New Capabilities
- `herdr-tool-window`: the docked panel, its terminal lifecycle, screen drawing, resize, mouse, copy and paste, IDE font and colours, and the error state when Herdr is missing.
- `herdr-sessions`: shared session by default, creating a project-only session, switching between the two, remembering the choice per project, and the project session outliving IntelliJ.
- `herdr-key-routing`: which keys go to Herdr and which stay with IntelliJ while the panel has focus, and picking up Herdr config changes.
- `plugin-distribution`: supported IDE builds, operating systems and CPU architectures, bundled native engine, and Marketplace packaging.

### Modified Capabilities
None. The repository is new.

## Impact

- New repository `~/Repos/herdr-idea`: Kotlin plugin built with the IntelliJ Platform Gradle Plugin 2.x, plus a native build of `libghostty-vt` from a pinned Ghostty commit for `darwin-aarch64`, `darwin-x86_64`, `linux-x86_64` and `linux-aarch64`.
- Runtime dependencies inside the IDE: the platform's bundled `pty4j` for the pseudo-terminal and Java's Foreign Function and Memory API (the bundled JBR 25 supports it) to call `libghostty-vt`.
- External dependency: the `herdr` binary and its config on the user's machine. The plugin starts `herdr` clients and never stops the Herdr server.
- Risk: `libghostty-vt` marks its API unstable. Pinning one Ghostty commit per plugin release contains that risk.
