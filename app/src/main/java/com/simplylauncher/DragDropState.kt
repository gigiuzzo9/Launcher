package com.simplylauncher

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Stato globale del drag&drop.
 * Quando l'utente trascina un'icona, questo stato tiene traccia di:
 * - quale app si sta trascinando
 * - dove si trova il dito
 * - se il drag è in corso
 */
object DragDropState {
    // App attualmente in drag (null se nessun drag)
    var draggedApp: AppInfo? by mutableStateOf(null)

    // Posizione del dito durante il drag
    var dragPosition: Offset by mutableStateOf(Offset.Zero)

    // Se il drag è attivo
    var isDragging: Boolean by mutableStateOf(false)

    // Callback per il rilascio: chiamato dalla home quando il dito viene rilasciato
    var onDrop: ((AppInfo, Offset) -> Unit)? = null

    fun startDrag(app: AppInfo, position: Offset) {
        draggedApp = app
        dragPosition = position
        isDragging = true
    }

    fun updatePosition(position: Offset) {
        if (isDragging) {
            dragPosition = position
        }
    }

    fun endDrag() {
        val app = draggedApp
        val pos = dragPosition
        if (app != null && onDrop != null) {
            onDrop?.invoke(app, pos)
        }
        draggedApp = null
        isDragging = false
    }

    fun cancelDrag() {
        draggedApp = null
        isDragging = false
    }
}
