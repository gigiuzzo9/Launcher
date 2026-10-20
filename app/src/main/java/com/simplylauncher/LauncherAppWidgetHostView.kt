package com.simplylauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Se il long-press è già scattato, intercetta TUTTI gli eventi successivi
        // (incluso l'ACTION_UP) per impedire al widget di aprire l'app.
        if (longPressTriggered) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressTriggered = false
            }
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                longPressTriggered = false
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
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Necessario per continuare a ricevere gli eventi dopo il long-press
        return true
    }
}
