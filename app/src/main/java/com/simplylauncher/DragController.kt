package com.simplylauncher

import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

/**
 * Coordina il flusso long-press → drag → drop.
 *
 * NOTA: non usa setOnTouchListener sul root (i RecyclerView consumano i touch).
 * Invece espone onInterceptTouch() che va chiamato da dispatchTouchEvent
 * dell'Activity, che riceve TUTTI i touch prima dei figli.
 */
class DragController(
    private val activity: HomeActivity,
    private val homeRecyclerView: View,
    private val drawerOverlay: View,
    private val onDropOnHome: (pkg: String, x: Float, y: Float) -> Boolean,
    private val onDropOnDrawer: (pkg: String) -> Unit,
    private val onLongPressWithoutMove: (AppInfo, View, Boolean) -> Unit
) {

    companion object {
        private const val DRAG_THRESHOLD_DP = 24f
        private const val DRAWER_FADE_MS = 300L

        const val SOURCE_DRAWER = "DRAWER"
        const val SOURCE_HOME = "HOME"
        const val SOURCE_DOCK = "DOCK"
    }

    private val thresholdPx = DRAG_THRESHOLD_DP * activity.resources.displayMetrics.density

    private var pendingApp: AppInfo? = null
    private var pendingAnchor: View? = null
    private var pendingSource: String? = null
    private var pendingStartRawX = 0f
    private var pendingStartRawY = 0f
    private var pendingFromDrawer = false

    private var isDragging = false

    fun onLongPress(app: AppInfo, anchor: View, source: String, fromDrawer: Boolean) {
        if (isDragging) return
        pendingApp = app
        pendingAnchor = anchor
        pendingSource = source
        pendingFromDrawer = fromDrawer
        pendingStartRawX = -1f
        pendingStartRawY = -1f
    }

    /**
     * Da chiamare da Activity.dispatchTouchEvent PRIMA di super.dispatchTouchEvent.
     * Ritorna true se l'evento è stato consumato (per evitare che il RecyclerView scrolli).
     */
    fun onInterceptTouch(event: MotionEvent): Boolean {
        val app = pendingApp ?: return false
        val anchor = pendingAnchor ?: return false
        val source = pendingSource ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // se arriva un nuovo DOWN prima che parta il drag, annulla il pending
                // (non dovrebbe succedere ma sicurezza)
                pendingStartRawX = event.rawX
                pendingStartRawY = event.rawY
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                // inizializza il punto di partenza al primo MOVE (il long-press
                // è già avvenuto, il dito è fermo sopra la view)
                if (pendingStartRawX < 0f) {
                    pendingStartRawX = event.rawX
                    pendingStartRawY = event.rawY
                    return false
                }
                val dx = event.rawX - pendingStartRawX
                val dy = event.rawY - pendingStartRawY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > thresholdPx) {
                    startDrag(app, anchor, source)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // rilasciato senza muoversi oltre soglia → popup
                onLongPressWithoutMove(app, anchor, pendingFromDrawer)
                clearPending()
                return true
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
            drawerOverlay.animate().alpha(0.2f).setDuration(DRAWER_FADE_MS).start()
        }

        clearPending()
    }

    fun onDragEnded() {
        isDragging = false
        if (pendingFromDrawer) {
            drawerOverlay.animate().alpha(1f).setDuration(DRAWER_FADE_MS).start()
        } else {
            drawerOverlay.alpha = 1f
        }
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
