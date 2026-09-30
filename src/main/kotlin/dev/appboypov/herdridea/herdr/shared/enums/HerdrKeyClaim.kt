package dev.appboypov.herdridea.herdr.shared.enums

/** Who handles a key pressed while the Herdr panel has focus (ADR-0004). */
enum class HerdrKeyClaim {
    /** Herdr gets the key; the IDE never sees it. */
    HERDR,

    /** The panel copies its selection to the clipboard. */
    COPY,

    /** The panel pastes the clipboard into Herdr. */
    PASTE,

    /** The IDE's action system gets the key; unused keys still reach Herdr through the fallback. */
    IDE,
}
