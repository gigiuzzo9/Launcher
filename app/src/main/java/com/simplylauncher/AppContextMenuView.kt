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
    var onDismiss: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    fun show(anchor: View) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)

            val drawable = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#EE000000"))
            }
            background = drawable
        }

        val uninstallItem = createMenuItem(ctx.getString(R.string.menu_uninstall)) {
            onUninstall?.invoke()
            dismiss()
        }
        val renameItem = createMenuItem(ctx.getString(R.string.menu_rename)) {
            onRename?.invoke()
            dismiss()
        }
        val removeItem = createMenuItem(ctx.getString(R.string.menu_remove)) {
            onRemove?.invoke()
            dismiss()
        }

        container.addView(uninstallItem)
        container.addView(renameItem)
        container.addView(removeItem)

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false   // ← NON focusable
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            setOnDismissListener {
                onDismiss?.invoke()
            }
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
}
