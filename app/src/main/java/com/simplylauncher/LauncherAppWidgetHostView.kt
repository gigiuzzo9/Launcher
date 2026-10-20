package com.simplylauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.MotionEvent

/**
 * AppWidgetHostView personalizzata che intercetta i tocchi.
 * Necessaria perché gli widget consumano i tocchi e il long-press non arriva mai alla view.
 * Come in Launcher3, intercettiamo ACTION_DOWN e gestiamo il long-press manualmente.
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null
    private var downTime: Long = 0L
    private val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = System.currentTimeMillis()
                // Chiedi al parent di non intercettare
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (System.currentTimeMillis() - downTime > longPressTimeout) {
                    // Long-press rilevato
                    onLongPress?.invoke()
                    downTime = 0L
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                downTime = 0L
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
