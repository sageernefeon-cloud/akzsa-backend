package com.nefeon.akzsa

/**
 * Single on/off switches for features that are built but intentionally not shipped in a given release.
 * Flip a flag back to true to re-enable a feature — no other code needs to change.
 */
object FeatureFlags {
    /** Backtrack: self-correction mid-dictation ("actually, make that 6pm") without re-dictating. */
    const val BACKTRACK_ENABLED = true

    /** Snippets: short spoken triggers that expand to a saved block of text, entirely on-device. */
    const val SNIPPETS_ENABLED = true
}
