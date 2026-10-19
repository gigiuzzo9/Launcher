package com.simplylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Popup che appare cliccando un punto vuoto della home.
 * Voci: "Aggiungi widget", "Aggiungi pagina", "Modifica sfondo",
 * e "Rimuovi pagina" (visibile solo se pageCount > 1).
 */
class HomePopupMenu(private val ctx: Context) {

    var onAddWidget: (() -> Unit)? = null
    var onAddPage: (() -> Unit)? = null
    var onChangeWallpaper: (() -> Unit)? = null
    var onRemovePage: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    fun show(anchor: View, showRemovePage: Boolean) {
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

        container.addView(createMenuItem(ctx.getString(R.string.menu_add_widget)) {
            onAddWidget?.invoke()
            dismiss()
        })

        container.addView(createMenuItem(ctx.getString(R.string.menu_add_page)) {
            onAddPage?.invoke()
            dismiss()
        })

        container.addView(createMenuItem(ctx.getString(R.string.menu_change_wallpaper)) {
            onChangeWallpaper?.invoke()
            dismiss()
        })

        if (showRemovePage) {
            container.addView(createMenuItem(ctx.getString(R.string.menu_remove_page)) {
                onRemovePage?.invoke()
                dismiss()
            })
        }

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
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
}
