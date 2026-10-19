package com.simplylauncher

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Toast

/**
 * Coordina il flusso long-press → drag → drop.
 *
 * - Long-press: viene memorizzato un "pending drag" con la view anchor
 * - ACTION_MOVE oltre soglia: parte il drag vero e proprio
 * - ACTION_UP senza superare la soglia: mostra il popup menu contestuale
 *
 * Le soglie sono in dp, convertite in px in base alla densità.
 */
class DragController(
    private val activity: HomeActivity,
    private val rootView: View,
    private val homeRecyclerView: View,
    private val drawerOverlay: View,
    private val onDropOnHome: (pkg: String, x: Float, y: Float) -> Boolean,
    private val onDropOnDrawer: (pkg: String) -> Unit,
    private val onLongPressWithoutMove: (AppInfo, View, Boolean) -> Unit
) {

    companion object {
        // Soglia generosa per evitare partenze accidentali (24dp)
        private const val DRAG_THRESHOLD_DP = 24f
        // Durata fade cassetto
        private const val DRAWER_FADE_MS = 300L

        // Sorgente corrente del drag
        const val SOURCE_DRAWER = "DRAWER"
        const val SOURCE_HOME = "HOME"
        const val SOURCE_DOCK = "DOCK"
    }

    private val thresholdPx = DRAG_THRESHOLD_DP * activity.resources.displayMetrics.density

    // Stato del pending drag (long-press avvenuto, ma drag non ancora partito)
    private var pendingApp: AppInfo? = null
    private var pendingAnchor: View? = null
    private var pendingSource: String? = null
    private var pendingStartX = 0f
    private var pendingStartY = 0f
    private var pendingFromDrawer = false

    // Flag: stiamo gestendo un drag vero e proprio?
    private var isDragging = false

    /**
     * Chiamato dagli adapter quando avviene un long-press.
     * Registra il pending drag e si mette in ascolto dei movimenti sul root.
     */
    fun onLongPress(app: AppInfo, anchor: View, source: String, fromDrawer: Boolean) {
        // se c'è già un pending, ignora
        if (pendingApp != null || isDragging) return

        pendingApp = app
        pendingAnchor = anchor
        pendingSource = source
        pendingFromDrawer = fromDrawer

        // coordinate iniziali: ricavate dalla view
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        pendingStartX = loc[0] + anchor.width / 2f
        pendingStartY = loc[1] + anchor.height / 2f
    }

    /**
     * Da collegare a rootView.setOnTouchListener (o dispatchTouchEvent).
     * Gestisce SOLO il caso "pending drag in corso".
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        val app = pendingApp ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - pendingStartX
                val dy = event.rawY - pendingStartY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > thresholdPx) {
                    startDrag(app)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // l'utente ha rilasciato senza superare la soglia:
                // mostra il popup contestuale
                val anchor = pendingAnchor
                val source = pendingSource
                val fd = pendingFromDrawer
                if (anchor != null && source != null) {
                    onLongPressWithoutMove(app, anchor, fd)
                }
                clearPending()
            }
        }
        return false
    }

    /**
     * Avvia il drag vero e proprio.
     */
    private fun startDrag(app: AppInfo) {
        val anchor = pendingAnchor ?: return
        val source = pendingSource ?: SOURCE_HOME

        val clip = ClipData.newPlainText(source, app.packageName)
        val shadow = View.DragShadowBuilder(anchor)
        val started = anchor.startDragAndDrop(clip, shadow, null, 0)

        if (!started) {
            Toast.makeText(activity, "Drag non supportato", Toast.LENGTH_SHORT).show()
            clearPending()
            return
        }

        isDragging = true

        // fade del cassetto se il drag parte dal drawer
        if (source == SOURCE_DRAWER) {
            drawerOverlay.animate()
                .alpha(0.2f)
                .setDuration(DRAWER_FADE_MS)
                .start()
            drawerOverlay.alpha = 0.2f
        }

        clearPending()
    }

    /**
     * Da collegare a ACTION_DRAG_ENDED di home e drawer.
     * Ripristina lo stato grafico.
     */
    fun onDragEnded() {
        isDragging = false
        if (pendingFromDrawer) {
            drawerOverlay.animate()
                .alpha(1f)
                .setDuration(DRAWER_FADE_MS)
                .start()
            drawerOverlay.alpha = 1f
        }
    }

    /**
     * Gestisce ACTION_DROP sulla home.
     * Chiama onDropOnHome con coordinate relative al RecyclerView.
     */
    fun handleHomeDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }
            DragEvent.ACTION_DROP -> {
                val pkg = event.clipData.getItemAt(0).text.toString()
                val source = event.clipData.description.label?.toString() ?: ""
                // coordinate relative alla home
                val child = homeRecyclerView.findViewWithTag<View>(pkg)
                // delega alla HomeActivity
                val handled = onDropOnHome(pkg, event.x, event.y)
                // ripristina fade drawer
                if (source == SOURCE_DRAWER) {
                    drawerOverlay.alpha = 1f
                }
                return handled
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                onDragEnded()
                return true
            }
        }
        return false
    }

    /**
     * Gestisce ACTION_DROP sul cassetto.
     * Usato per rimuovere un'app dalla home trascinandola sul drawer.
     */
    fun handleDrawerDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }
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
    }
}
