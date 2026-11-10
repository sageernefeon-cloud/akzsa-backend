# Batch 1: keyboard feel (changes in this zip)

Changed:
- keyboard/KeyboardRenderer.kt: new touch rules (plain keys type on touch-down; other keys fire on release only if the finger is still on the key; sliding off cancels), no dead zones between keys, Shift/Caps relabels keys in place (refreshShiftState) instead of a full render(), key preview + long-press accent popup, backspace stops repeating when the finger slides off.
- keyboard/KeyView.kt: draws inset inside a full-size touch cell.
- keyboard/TypingController.kt: Shift handling uses refreshShiftState().
New: keyboard/KeyAccents.kt, keyboard/KeyTouchPolicy.kt, keyboard/KeyPopup.kt
New tests: KeyAccentsTest.kt, KeyTouchPolicyTest.kt (12 tests; pass in a stub JUnit runner here).

NOT verified: Android compile and on-device behaviour (no Android SDK in the build environment).
Secrets: server/.env and keystore.properties were REMOVED from this zip. Create your own from the .example files and rotate the Groq key that was in the original.
