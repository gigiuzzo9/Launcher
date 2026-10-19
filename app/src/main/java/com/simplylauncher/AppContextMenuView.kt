package com.simplylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class AppContextMenuView(private val ctx: Context) {

    var onUninstall: (() -> Unit)? = null
    var onRename: (() -> Unit)? = null
    var onRemove: (() -> Unit)? = null
    var onMove: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    fun show(anchor: View, showRemove: Boolean) {
        dismiss()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            val drawable = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#EE000000"))
            }
            background = drawable
        }

        if (showRemove) {
            container.addView(createMenuItem(ctx.getString(R.string.menu_remove)) {
                onRemove?.invoke()
                dismiss()
            })
        }

        container.addView(createMenuItem(ctx.getString(R.string.menu_uninstall)) {
            onUninstall?.invoke()
            dismiss()
        })

        container.addView(createMenuItem(ctx.getString(R.string.menu_rename)) {
            onRename?.invoke()
            dismiss()
        })

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false // NON focusable: così non ruba i touch al drag
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            setOnDismissListener { onDismiss?.invoke() }
            showAtLocation(anchor, Gravity.CENTER, 0, 0)
        }
    }

    private fun createMenuItem(text: String, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(32, 24, 32, 24)
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    /**
     * Ritorna true se il punto (in coordinate schermo) è dentro il popup.
     * Usato da HomeActivity per capire se chiudere il popup al tocco.
     */
    fun isPointInside(screenX: Float, screenY: Float): Boolean {
        val pw = popupWindow ?: return false
        if (!pw.isShowing) return false
        val container = pw.contentView ?: return false
        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        return screenX >= loc[0] && screenX <= loc[0] + container.width &&
                screenY >= loc[1] && screenY <= loc[1] + container.height
    }
}
