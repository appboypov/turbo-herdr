# ADR-0004: Herdr's config decides key precedence; a pending prefix claims only prefix-mode keys

- Status: accepted, supersedes ADR-0003
- Date: 2026-09-30
- Supersedes: ADR-0003

## Context

ADR-0003 made Herdr's config decide key precedence in the panel, and claimed "a pending prefix's follow-up key" for Herdr, whatever that key was. On 2026-09-30 (DAS-194) an IDE shortcut pressed after `ctrl+;`, such as Cmd+O, went to Herdr, which drops a key it does not bind in prefix mode, so neither Herdr nor IntelliJ acted. Herdr's prefix mode lasts one key, closes on Esc without forwarding, forwards a second prefix press to the pane, and has no timeout (`herdrdev/herdr` `src/client/shell/input.rs` 550-585). The rest of ADR-0003 stays valid and is restated here.

## Decision

The plugin ports Herdr's key grammar and defaults at a pinned Herdr version. It parses `config.toml` and watches it for changes. It claims exactly that key set plus Esc before the action system sees them. All other keys go to the IDE first and reach Herdr only when no enabled action handles them.

While the prefix is pending, the plugin claims the next key only when Herdr handles it in prefix mode: a key Herdr binds after the prefix, the prefix key itself, or Esc. For any other next key, the plugin sends Esc to Herdr to close prefix mode, then routes the key as if no prefix were pending.

## Consequences

- Good: key precedence stays predictable from Herdr's config alone and follows config edits without restart.
- Good: IDE shortcuts work after an accidental or abandoned prefix, and Herdr never stays in prefix mode behind the user's back.
- Bad: the plugin carries a port of Herdr's key grammar that must track Herdr releases, including how prefix mode closes.
- Bad: an unbound plain key after the prefix reaches the pane, where Herdr alone would drop it.
- Follow-up: replace the port with a Herdr-provided key dump if Herdr adds one.
