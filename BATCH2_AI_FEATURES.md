# Batch 2: AI features (changes in this zip, on top of Batch 1)

New keyboard row: tap **AI** (in the second toolbar row). Four tools, each with a short menu. Results are always shown for review (Cancel / Apply) before anything changes. Needs "Share selected and surrounding text" ON in Settings, same as the existing Fix tool.

| Tool | What it does | Menu |
|---|---|---|
| Reply | Writes a reply to the message you last COPIED. Anything already typed/said in the field is used as "what I want to say" (e.g. "say yes, by evening"). | Auto, Polite, Short, Formal, Friendly |
| Rewrite | Rewrites the selection, or the whole field | Fix, Polite, Short, Formal, Friendly, Expand |
| Translate | Translates the selection or field | Hindi, Hinglish, English, Kannada (backend accepts any language name) |
| Notes | Turns dictated notes into a structured result | Email, To-do, Notes, Summary |

"My writing style" (Settings): optional persona, up to 300 chars, sent only with these tools. It shapes tone/wording only.

## Files
Backend: server/app/transform.py (new), server/app/main.py (new POST /v1/ai/transform), tests: test_transform.py, test_android_ai_tools_contract.py (fails if the Android menu and the backend styles drift apart).
Android: keyboard/AiTools.kt (new), PlanController.runAiTool, ToolbarController.aiRow, KeyboardRenderer (AI row), KeyboardState, network/CommandModels.kt + BackendClient.transform, settings (aiPersona) + AkzsaApp settings field. Test: AiToolsTest.kt.

## Verified
Backend: 75 tests pass (python -m pytest server/tests -q).
Kotlin logic (AiTools, KeyAccents, KeyTouchPolicy): 19 tests pass in a stub JUnit runner.
NOT verified: Android compile and on-device behaviour (no Android SDK here). Please build in Android Studio and send any errors.

## Safety
User text, copied message and persona go to the AI as JSON data, never inside the system prompt; the prompt tells the model not to obey instructions found in them. Output is validated (empty / ballooning results are rejected) and the keyboard never applies a result without the user tapping Apply. Protected/password fields are blocked.
