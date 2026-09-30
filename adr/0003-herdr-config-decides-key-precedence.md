# ADR-0003: Herdr's config decides key precedence in the panel

- Status: superseded by ADR-0004
- Date: 2026-09-22
- Supersedes: none

## Context

Brian's Herdr config binds `prefix = "ctrl+;"` and many `cmd+…` chords (`cmd+k`, `cmd+p`, `cmd+w`, `cmd+t`, `cmd+e`, …) that are also IntelliJ shortcuts. The user asked that, while the panel has focus, Herdr's bound keys and Esc go to Herdr and every other IDE shortcut keeps working. Herdr has no CLI that reports its effective keys. Its grammar and defaults live in `herdrdev/herdr` `src/config/keybinds.rs` and `src/config/model.rs`. IntelliJ's terminal overrides IDE shortcuts with an `IdeEventQueue.EventDispatcher` plus a `KeyListener` fallback (`TerminalEventDispatcher.kt`, branch `261`).

## Considered Options

- Claim exactly Herdr's effective key set plus Esc, read from `config.toml` merged over Herdr's defaults, through an `IdeEventQueue.EventDispatcher` active only while the panel has focus.
- Claim every key while the panel has focus: rejected by the user.
- Keep an allow-list of IDE actions, as IntelliJ's terminal does: precedence would depend on a plugin-maintained list instead of Herdr's config.

## Decision

The plugin ports Herdr's key grammar and defaults at a pinned Herdr version. It parses `config.toml` and watches it for changes. It claims exactly that key set plus Esc, and a pending prefix's follow-up key, before the action system sees them. All other keys go to the IDE first and reach Herdr only when no action consumes them.

## Consequences

- Good: key precedence is predictable from Herdr's config alone and follows config edits without restart.
- Bad: the plugin carries a port of Herdr's key grammar that must track Herdr releases.
- Bad: a new Herdr key syntax is ignored, with a log entry, until the port catches up.
- Follow-up: replace the port with a Herdr-provided key dump if Herdr adds one.
