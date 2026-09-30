# ADR Review Manifest

- Status: completed
- Review date: 2026-09-30

## Review Summary

ADR review completed for this change. Design D4 changes ADR-0003's prefix follow-up rule, so a new ADR supersedes it. D1, D2, D3 and D5 are fixes inside the in-force decisions and need no ADR.

## In-Force ADRs Reviewed

- `adr/0001-bundle-libghostty-vt-through-ffm.md`: unchanged; the fixes keep libghostty-vt behind FFM.
- `adr/0002-draw-screen-in-swing-from-render-state.md`: unchanged; D1 and D2 keep the terminal-thread frame copy and Swing painting.
- `adr/0003-herdr-config-decides-key-precedence.md`: superseded by ADR-0004 for the prefix follow-up rule.

## New Durable ADRs Created

- `adr/0004-herdr-prefix-follow-up-only-for-prefix-mode-keys.md`: supersedes ADR-0003.
