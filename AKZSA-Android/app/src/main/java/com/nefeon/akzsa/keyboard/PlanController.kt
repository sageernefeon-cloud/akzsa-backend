package com.nefeon.akzsa.keyboard

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.nefeon.akzsa.commands.ActionPlan
import com.nefeon.akzsa.commands.ActionValidator
import com.nefeon.akzsa.commands.PlanKind
import com.nefeon.akzsa.commands.ValidationResult
import com.nefeon.akzsa.network.ApiResult
import com.nefeon.akzsa.network.BackendClient
import com.nefeon.akzsa.network.CommandRequest
import com.nefeon.akzsa.network.TransformRequest
import com.nefeon.akzsa.security.InstallId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AI actions that change the host field: the reviewable pending plan (preview, Cancel / Apply), executing a
 * validated [ActionPlan], the whole-field AI tools (Fix, AI Emoji) and the verified field-edit helpers.
 * Nothing here is sent to the backend except what the user explicitly consented to share.
 */
internal class PlanController(private val hub: KeyboardHub) {
    private val state get() = hub.state

    // ---- pending plan ----

    /** Parks [plan] for review; the renderer shows the preview with Cancel / Apply. */
    fun propose(plan: ActionPlan, status: String) {
        state.pendingPlan = plan
        hub.setStatus(status)
        updatePendingPreview()
    }

    fun clearPending() {
        state.pendingPlan = null
        state.regenerate = null
        updatePendingPreview()
    }

    fun updatePendingPreview() = hub.renderer.showPendingPreview(state.pendingPlan)

    fun confirmPendingAction() {
        val plan = state.pendingPlan ?: return
        state.pendingPlan = null
        state.regenerate = null
        updatePendingPreview()
        executePlan(plan)
    }

    fun cancelPendingAction() {
        state.pendingPlan = null
        state.regenerate = null
        updatePendingPreview()
        hub.setStatus("Action cancelled · no change made")
    }

    // ---- whole-field AI tools ----

    /** Reads the focused editor only after explicit context-sharing consent. */
    fun transformWholeField(instruction: String) {
        state.regenerate = { transformWholeField(instruction) }
        if (state.passwordField) { hub.setStatus("AI editing is disabled for protected fields"); return }
        if (!state.settings.shareEditorContext) {
            hub.setStatus("Enable Share editor context in AKZSA Settings before using AI text tools")
            return
        }
        val ic = hub.inputConnection ?: return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val fullText = extracted?.text?.toString().orEmpty()
        if (fullText.isBlank()) { hub.setStatus("There is no accessible text in the focused field to edit"); return }
        if (fullText.length > 10_000) { hub.setStatus("This text is too long for one AI edit"); return }
        val fieldStart = extracted?.startOffset ?: 0
        if (!ic.setSelection(fieldStart, fieldStart + fullText.length)) {
            hub.setStatus("This editor cannot select the complete text safely"); return
        }
        hub.setStatus("Preparing a reviewable AI edit…")
        hub.scope.launch {
            try {
                state.settings = hub.settingsRepository.settings.first()
                if (!state.settings.shareEditorContext) { hub.setStatus("Editor-context sharing is off; no text was sent"); return@launch }
                val token = hub.tokenStore.readTokenAsync()
                if (token.isNullOrBlank()) { hub.setStatus("Add the backend bearer token in AKZSA Settings"); return@launch }
                val payload = CommandRequest(
                    transcript = instruction,
                    selected_text = fullText,
                    surrounding_text = null,
                    mode = "write",
                )
                when (val response = BackendClient.interpret(state.settings.backendBaseUrl, token, payload, InstallId.get(hub.ime.applicationContext))) {
                    is ApiResult.Failure -> hub.setStatus(response.message)
                    is ApiResult.Success -> when (val validated = ActionValidator.validate(response.value.action, instruction, fullText)) {
                        is ValidationResult.Rejected -> hub.setStatus(validated.message)
                        is ValidationResult.Accepted -> {
                            propose(validated.plan.copy(requiresConfirmation = true), "Review the complete-field edit, then tap Apply")
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { KeyboardLog.w("PlanController", e); hub.setStatus("AI text tool failed. Original text has not been replaced.") }
        }
    }

    // ---- AI row tools (Reply / Rewrite / Translate / Notes) ----

    /**
     * Runs one AI text tool. Works on the selection, or on the whole field when nothing is selected, and only
     * after the user's context-sharing consent. The result is never applied directly: it is parked as a pending
     * plan so the user reviews it (Cancel / Apply). Reply uses the most recently copied message as the text being
     * answered and whatever is in the field as "what I want to say".
     */
    fun runAiTool(task: AiTask, option: AiOption) {
        state.regenerate = { runAiTool(task, option) }
        if (state.passwordField) { hub.setStatus("AI tools are disabled for protected fields"); return }
        if (!state.settings.shareEditorContext) {
            hub.setStatus("Enable Share editor context in AKZSA Settings before using AI text tools")
            return
        }
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val extracted = if (selected.isEmpty()) ic.getExtractedText(ExtractedTextRequest(), 0) else null
        val fieldText = extracted?.text?.toString().orEmpty()
        val working = selected.ifEmpty { fieldText }          // what the result will replace, if anything

        val sourceText: String
        var instruction: String? = null
        if (task == AiTask.REPLY) {
            val message = ClipboardHistory.recent(1).firstOrNull()
            if (message.isNullOrBlank()) { hub.setStatus(AiTools.emptyHint(task)); return }
            sourceText = message
            instruction = working.takeIf { it.isNotBlank() }
        } else {
            if (working.isBlank()) { hub.setStatus(AiTools.emptyHint(task)); return }
            sourceText = working
        }
        if (sourceText.length > 10_000 || (instruction?.length ?: 0) > 500) {
            hub.setStatus("This text is too long for one AI tool. Select a shorter part.")
            return
        }
        val replacesExisting = working.isNotBlank()
        if (replacesExisting && selected.isEmpty()) {
            val fieldStart = extracted?.startOffset ?: 0
            if (!ic.setSelection(fieldStart, fieldStart + fieldText.length)) {
                hub.setStatus("This editor cannot select the complete text safely"); return
            }
        }
        val label = AiTools.describe(task, option)
        hub.setStatus("$label…")
        hub.scope.launch {
            try {
                state.settings = hub.settingsRepository.settings.first()
                if (!state.settings.shareEditorContext) { hub.setStatus("Editor-context sharing is off; no text was sent"); return@launch }
                val token = hub.tokenStore.readTokenAsync()
                if (token.isNullOrBlank()) { hub.setStatus("Add the backend bearer token in AKZSA Settings"); return@launch }
                val payload = TransformRequest(
                    task = task.wire,
                    text = sourceText,
                    style = option.style,
                    language = option.language,
                    instruction = instruction,
                    persona = AiTools.cleanPersona(state.settings.aiPersona),
                )
                when (val response = BackendClient.transform(state.settings.backendBaseUrl, token, payload, InstallId.get(hub.ime.applicationContext))) {
                    is ApiResult.Failure -> hub.setStatus(response.message)
                    is ApiResult.Success -> {
                        val text = response.value.text
                        if (text.length > 10_000) { hub.setStatus("The AI result is too long to insert safely"); return@launch }
                        propose(
                            ActionPlan(
                                kind = if (replacesExisting) PlanKind.REPLACE_SELECTED_TEXT else PlanKind.INSERT_GENERATED_TEXT,
                                explanation = label,
                                requiresConfirmation = true,
                                replacementText = text,
                                sourceSelection = if (replacesExisting) working else null,
                            ),
                            "Review the AI result, then tap Apply",
                        )
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { KeyboardLog.w("PlanController", e); hub.setStatus("AI tool failed. Your text has not been changed.") }
        }
    }

    // ---- executing a plan ----

    fun executePlan(plan: ActionPlan) {
        if (state.passwordField) {
            hub.setStatus("AI actions are disabled for protected fields")
            return
        }
        if (!hub.ensureInputConnection()) return
        val ic = hub.inputConnection ?: return
        // Any edit other than a fresh dictation or a Backtrack correction invalidates the Backtrack
        // reference: correcting "what you just said" should never apply across an unrelated edit.
        if (plan.kind != PlanKind.DICTATION && plan.kind != PlanKind.CORRECT_LAST_DICTATION) {
            state.lastDictationText = null
        }
        when (plan.kind) {
            PlanKind.DICTATION -> {
                val text = plan.replacementText ?: run { hub.setStatus("No text to insert"); return }
                hub.typing.commitDictation(text)   // records state.lastDictationText with the exact inserted text
                hub.setStatus("Text inserted")
            }
            PlanKind.CORRECT_LAST_DICTATION -> correctLastDictation(ic, plan)
            PlanKind.NEW_PARAGRAPH -> {
                ic.commitText("\n", 1)
                hub.setStatus("New paragraph inserted")
            }
            PlanKind.INSERT_GENERATED_TEXT -> {
                val text = plan.replacementText ?: run { hub.setStatus("The AI returned no text"); return }
                if (ic.getSelectedText(0)?.toString().orEmpty().isNotEmpty()) {
                    hub.setStatus("Text is selected. Tell AKZSA how you want the selection changed.")
                    return
                }
                if (text.length > 10_000) { hub.setStatus("Generated text exceeds the safety limit"); return }
                ic.commitText(text, 1)
                hub.setStatus("AI writing inserted")
            }
            PlanKind.DELETE_LAST_WORDS -> {
                val count = plan.count ?: run { hub.setStatus("Invalid deletion count"); return }
                if (count !in 1..50) { hub.setStatus("Deletion limit exceeded"); return }
                deleteFieldSuffix(ic, count, words = true)
            }
            PlanKind.DELETE_LAST_CHARACTERS -> {
                val count = plan.count ?: run { hub.setStatus("Invalid deletion count"); return }
                if (count !in 1..100) { hub.setStatus("Deletion limit exceeded"); return }
                deleteFieldSuffix(ic, count, words = false)
            }
            PlanKind.DELETE_ALL_TEXT -> clearCurrentTextField(ic)
            PlanKind.DELETE_SELECTED_TEXT -> {
                val expected = plan.sourceSelection ?: run { hub.setStatus("No original selection was recorded"); return }
                val currentSelected = ic.getSelectedText(0)?.toString()
                if (currentSelected != expected) {
                    hub.setStatus("Selection changed since preview. Select the original text and try again.")
                    return
                }
                ic.commitText("", 1)
                hub.setStatus("Selected text removed")
            }
            PlanKind.REPLACE_SELECTED_TEXT -> {
                val expected = plan.sourceSelection ?: run { hub.setStatus("No original selection was recorded"); return }
                val currentSelected = ic.getSelectedText(0)?.toString()
                if (currentSelected != expected) {
                    hub.setStatus("Selection changed since preview. Select the original text and try again.")
                    return
                }
                val replacement = plan.replacementText ?: run { hub.setStatus("No replacement text was provided"); return }
                if (replacement.length > 10_000) { hub.setStatus("Replacement exceeds the safety limit"); return }
                ic.commitText(replacement, 1)
                hub.setStatus("Selected text replaced")
            }
        }
        updatePendingPreview()
    }

    /** Select and verify the complete focused text field locally; never send this text to the backend here. */
    private fun selectWholeFieldText(ic: InputConnection): String? {
        if (!ic.performContextMenuAction(android.R.id.selectAll)) {
            hub.setStatus("This editor cannot select the whole field safely; nothing changed")
            return null
        }
        val selected = ic.getSelectedText(0)?.toString()
        if (selected == null) {
            hub.setStatus("This editor did not expose the selected text; nothing changed")
            return null
        }
        if (selected.length > 20_000) {
            ic.setSelection(selected.length, selected.length)
            hub.setStatus("The field is too long to edit safely in one operation; nothing changed")
            return null
        }
        return selected
    }

    /**
     * Backtrack: replaces the exact text AKZSA last dictated with a corrected version, only if the field
     * still ends with that exact text. If the user has typed or edited since (so the field no longer ends
     * with what was expected), nothing is changed and the correction is explained as unavailable — this
     * mirrors the verify-before-apply pattern used for DELETE_SELECTED_TEXT / REPLACE_SELECTED_TEXT above.
     */
    private fun correctLastDictation(ic: InputConnection, plan: ActionPlan) {
        val expected = plan.sourceSelection ?: run { hub.setStatus("No recent dictation to correct"); return }
        val rawReplacement = plan.replacementText ?: run { hub.setStatus("No corrected text was provided"); return }
        // Keep the same leading/trailing spaces and first-letter capital the original insertion had.
        val replacement = BacktrackText.carryEdgeFormatting(expected, rawReplacement)
        val whole = selectWholeFieldText(ic) ?: return
        if (!whole.endsWith(expected)) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus("Couldn't apply the correction — the text has changed since you said it")
            return
        }
        val start = whole.length - expected.length
        if (!ic.setSelection(start, whole.length)) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus("This editor could not select the recent dictation safely; nothing changed")
            return
        }
        val selected = ic.getSelectedText(0)?.toString()
        if (selected != expected) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus("The editor selection did not match; nothing changed")
            return
        }
        ic.commitText(replacement, 1)
        state.lastDictationText = replacement
        hub.setStatus("Correction applied")
    }

    /** Delete a final suffix from the entire focused field, not relative to the caret. */
    private fun deleteFieldSuffix(ic: InputConnection, count: Int, words: Boolean) {
        val whole = selectWholeFieldText(ic) ?: return
        if (whole.isEmpty()) {
            hub.setStatus("The text field is already empty")
            return
        }
        val start = FieldEditMath.suffixStart(whole, count, words)
        if (start == null) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus(if (words) "There are fewer than $count words in the field; nothing changed" else "There are fewer than $count characters in the field; nothing changed")
            return
        }
        val expected = whole.substring(start)
        if (!ic.setSelection(start, whole.length)) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus("This editor could not select the final text safely; nothing changed")
            return
        }
        val selected = ic.getSelectedText(0)?.toString()
        if (selected != expected) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus("The editor selection did not match the proposed suffix; nothing changed")
            return
        }
        ic.commitText("", 1)
        hub.setStatus(if (words) "Deleted the last $count words at the end of the field" else "Deleted the last $count characters at the end of the field")
    }

    /** Clears only the currently focused text field after verifying Android selected its full contents. */
    private fun clearCurrentTextField(ic: InputConnection) {
        val whole = selectWholeFieldText(ic) ?: return
        if (whole.isEmpty()) {
            hub.setStatus("The text field is already empty")
            return
        }
        val selected = ic.getSelectedText(0)?.toString()
        if (selected != whole) {
            ic.setSelection(whole.length, whole.length)
            hub.setStatus("The selected text did not match the full field; nothing was deleted")
            return
        }
        ic.commitText("", 1)
        hub.setStatus("Cleared all text in the current field")
    }
}
