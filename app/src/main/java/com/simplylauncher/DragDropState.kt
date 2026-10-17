package com.simplylauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

object DragDropState {
    var draggedApp: AppInfo? by mutableStateOf(null)
    var dragPosition: Offset by mutableStateOf(Offset.Zero)
    var isDragging: Boolean by mutableStateOf(false)
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
