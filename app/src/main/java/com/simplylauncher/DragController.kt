package com.simplylauncher

import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

/**
 * Coordina il flusso long-press → popup → drag → drop.
 *
 * - Long-press: mostra SUBITO il popup contestuale
 * - ACTION_MOVE oltre soglia: chiude il popup e avvia il drag
 * - ACTION_UP senza superare la soglia: il popup resta aperto
 *
 * Va chiamato da Activity.dispatchTouchEvent tramite onInterceptTouch,
 * perché i RecyclerView consumano i touch prima dei parent.
 */
class DragController(
    private val activity: HomeActivity,
    private val homeRecyclerView: View,
    private val drawerOverlay: View,
    private val onDropOnHome: (pkg: String, x: Float, y: Float) -> Boolean,
    private val onDropOnDrawer: (pkg: String) -> Unit
) {

    companion object {
        private const val DRAG_THRESHOLD_DP = 24f
        private const val DRAWER_FADE_MS = 200L

        const val SOURCE_DRAWER = "DRAWER"
        const val SOURCE_HOME = "HOME"
        const val SOURCE_DOCK = "DOCK"
    }

    private val thresholdPx = DRAG_THRESHOLD_DP * activity.resources.displayMetrics.density

    private var pendingApp: AppInfo? = null
    private var pendingAnchor: View? = null
    private var pendingSource: String? = null
    private var pendingFromDrawer = false
    private var pendingStartRawX = -1f
    private var pendingStartRawY = -1f

    private var isDragging = false

    /**
     * Chiamato dagli adapter al long-press.
     * Mostra subito il popup contestuale e registra il pending per il drag.
     */
    fun onLongPress(app: AppInfo, anchor: View, source: String, fromDrawer: Boolean) {
        if (isDragging) return
        pendingApp = app
        pendingAnchor = anchor
        pendingSource = source
        pendingFromDrawer = fromDrawer
        pendingStartRawX = -1f
        pendingStartRawY = -1f

        // mostra SUBITO il popup contestuale
        activity.showContextMenuForDrag(app, anchor, fromDrawer)
    }

    /**
     * Da chiamare da Activity.dispatchTouchEvent PRIMA di super.dispatchTouchEvent.
     * Ritorna true se l'evento è stato consumato (per evitare scroll del RecyclerView).
     */
    fun onInterceptTouch(event: MotionEvent): Boolean {
        val app = pendingApp ?: return false
        val anchor = pendingAnchor ?: return false
        val source = pendingSource ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pendingStartRawX < 0f) {
                    pendingStartRawX = event.rawX
                    pendingStartRawY = event.rawY
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (pendingStartRawX < 0f) {
                    pendingStartRawX = event.rawX
                    pendingStartRawY = event.rawY
                    return false
                }
                val dx = event.rawX - pendingStartRawX
                val dy = event.rawY - pendingStartRawY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > thresholdPx) {
                    // chiudi il popup e avvia il drag
                    activity.dismissDragPopup()
                    startDrag(app, anchor, source)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // rilascio senza muoversi: lascia il popup aperto (già mostrato)
                clearPending()
                return false
            }
        }
        return false
    }

    private fun startDrag(app: AppInfo, anchor: View, source: String) {
        val clip = ClipData.newPlainText(source, app.packageName)
        val shadow = View.DragShadowBuilder(anchor)
        val started = anchor.startDragAndDrop(clip, shadow, null, 0)

        if (!started) {
            Toast.makeText(activity, "Drag non supportato", Toast.LENGTH_SHORT).show()
            clearPending()
            return
        }

        isDragging = true

        if (source == SOURCE_DRAWER) {
            // chiudi il cassetto e mostra la home
            activity.onDragFromDrawerStarted()
        }

        clearPending()
    }

    fun onDragEnded() {
        isDragging = false
        if (pendingFromDrawer) {
            activity.onDragFromDrawerEnded()
        }
        pendingFromDrawer = false
    }

    fun handleHomeDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

            DragEvent.ACTION_DROP -> {
                val pkg = event.clipData.getItemAt(0).text.toString()
                return onDropOnHome(pkg, event.x, event.y)
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                onDragEnded()
                return true
            }
        }
        return false
    }

    fun handleDrawerDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

            DragEvent.ACTION_DROP -> {
                val pkg = event.clipData.getItemAt(0).text.toString()
                val source = event.clipData.description.label?.toString() ?: ""
                if (source == SOURCE_HOME || source == SOURCE_DOCK) {
                    onDropOnDrawer(pkg)
                }
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                onDragEnded()
                return true
            }
        }
        return false
    }

    private fun clearPending() {
        pendingApp = null
        pendingAnchor = null
        pendingSource = null
        pendingFromDrawer = false
        pendingStartRawX = -1f
        pendingStartRawY = -1f
    }
}
