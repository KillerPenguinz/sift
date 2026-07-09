package com.ironclinicgym.sift.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the single active undo token and the id of the row that last changed (for the highlight
 * flash). Pure state, no Android: the UI observes [active] to show the snackbar and [flashPageId]
 * to paint the brief accent flash. A new action replaces any previous undo (single active undo).
 */
class UndoManager {
    private val _active = MutableStateFlow<UndoToken?>(null)
    val active: StateFlow<UndoToken?> = _active.asStateFlow()

    private val _flashPageId = MutableStateFlow<String?>(null)
    val flashPageId: StateFlow<String?> = _flashPageId.asStateFlow()

    /** Offer a new undo token, replacing any previous one. Null clears the current token. */
    fun offer(token: UndoToken?) {
        _active.value = token
    }

    /** Take and clear the active token (called when the user taps Undo). */
    fun take(): UndoToken? = _active.value.also { _active.value = null }

    /** Clear the active token without acting (snackbar dismissed / timed out). */
    fun clear() {
        _active.value = null
    }

    /** Signal that [pageId] just changed, so the UI can flash it. Pass null after the flash ends. */
    fun flash(pageId: String?) {
        _flashPageId.value = pageId
    }
}
