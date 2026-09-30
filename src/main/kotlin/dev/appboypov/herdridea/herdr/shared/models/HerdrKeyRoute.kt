package dev.appboypov.herdridea.herdr.shared.models

import dev.appboypov.herdridea.herdr.shared.enums.HerdrKeyClaim

/**
 * Where one key press goes while the Herdr panel has focus (ADR-0004).
 *
 * @property claim who handles the key.
 * @property closesPrefix Herdr's prefix mode is open and the key is not one Herdr handles in it, so
 *   Esc goes to Herdr first to close prefix mode, before the key goes to [claim].
 */
data class HerdrKeyRoute(val claim: HerdrKeyClaim, val closesPrefix: Boolean = false)
