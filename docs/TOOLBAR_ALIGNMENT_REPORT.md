# Toolbar alignment implementation report

## Root causes (from the source, not assumptions)
The keyboard uses custom Views (no Compose). Toolbar buttons were `KeyView` instances in `KeyboardRenderer.addToolbar`.
| Symptom | Cause |
|---|---|
| Label height differed per button, icon/label crowding | `KeyView` placed the icon at 38% of the body height while the label used bottom gravity plus 3dp padding; icon and label were placed independently. |
| Icon not centred | Layout used grid centring; some glyphs are asymmetric (document icon 0.5 unit right). |
| Settings clipped at the right edge | `cell = max(54dp, available/7)` forced 7 x 54 = 378dp inside a 360dp window; the scroller gave no hint that more existed. |
| Label clipping | 10sp labels in cells narrower than the widest label ("Clipboard"). |
| Row too short for icon + gap + label | Toolbar height was 44dp (40dp low-height). |

## Changes
- `ToolbarItemView.kt` (new): one reusable item, structure icon box (28dp) / glyph (24dp) / gap (4dp) / label (10-11sp, medium sans, one size for all seven, same baseline from shared font metrics, pixel-snapped). Whole view is clickable, keeps content description, selected state, haptics, pressed fill, Button role.
- `ToolbarLayoutPolicy.kt` (new, pure): cell width from available width and the widest measured label; scroll fallback with 6.5 visible items.
- `KeyboardRenderer.addToolbar`: uses the above; fit mode = weighted row inside the horizontal padding; overflow mode = `HorizontalScrollView` with edge fade and kept scroll position. Click handlers and button order come unchanged from `ToolbarController.toolButtons()`.
- `KeyIcons.opticalOffset`: centring by visual bounds.
- `KeyboardLayoutPolicy`: toolbar height 52dp (48dp low-height).
- No backend, API, AI, voice (One Mic), panel or settings code was touched.

## Expected behaviour by width (computed by the policy; see ToolbarLayoutPolicyTest)
| Width | Result |
|---|---|
| 320dp | scrolls, 48dp+ cells, last item half visible with fade |
| 360 / 393 / 412dp | all seven in one row, equal cells >= 48dp |
| 600dp+ / landscape | content width is capped (720 / 640dp), all seven fit |
| Large font scale | falls back to scrolling instead of clipping labels |

## Verification status (honest)
- NOT run: Gradle build, unit tests, instrumented tests, emulator screenshots, physical device. The environment that produced this change had no Android SDK and no network, so none of that could be executed.
- Done: code review of every touched file, and unit tests written (`ToolbarLayoutPolicyTest`) but not executed.
- Before release, run: `cd AKZSA-Android && ./gradlew testDebugUnitTest assembleDebug`, then check the toolbar at 320/360/393/412/600dp, portrait and landscape, and the panel transitions listed in `docs/DEVICE_TEST_PLAN.md`.

## Known issues / follow-ups
- The sparkle button is labelled "Grammar" and the language button "Lang" (original labels kept); rename in `ToolbarController` if you want "Language".
- Increasing the toolbar by 8dp reduces typing space slightly on very short screens; the existing collapse levels still protect the key rows.
- `KeyView` still has the unused `captionBelow` mode; it can be removed once the build is confirmed.
