package com.simplylauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

/**
 * AppWidgetHostView personalizzata che rileva il long-press in modo affidabile,
 * anche se il widget consuma i tocchi (pulsanti, clock, ecc.).
 *
 * Usa un Handler con postDelayed per il long-press, cancellato su move/up.
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private val longPressRunnable = Runnable {
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                handler.postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (Math.abs(ev.x - downX) > touchSlop || Math.abs(ev.y - downY) > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    fun cancelLongPress() {
        handler.removeCallbacks(longPressRunnable)
    }
}
