package com.simplylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class AppContextMenuView(context: Context) : LinearLayout(context) {

    var onUninstall: (() -> Unit)? = null
    var onRename: (() -> Unit)? = null
    var onRemove: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private val menuContainer: LinearLayout

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        visibility = GONE

        menuContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            setPadding(24, 16, 24, 16)

            val drawable = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#EE000000"))
            }
            background = drawable
        }

        val uninstallItem = createMenuItem(context.getString(R.string.menu_uninstall)) {
            onUninstall?.invoke()
            hide()
        }
        val renameItem = createMenuItem(context.getString(R.string.menu_rename)) {
            onRename?.invoke()
            hide()
        }
        val removeItem = createMenuItem(context.getString(R.string.menu_remove)) {
            onRemove?.invoke()
            hide()
        }

        menuContainer.addView(uninstallItem)
        menuContainer.addView(renameItem)
        menuContainer.addView(removeItem)

        addView(menuContainer)
    }

    private fun createMenuItem(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(32, 24, 32, 24)
            setOnClickListener { onClick() }
        }
    }

    fun show(x: Float, y: Float) {
        visibility = VISIBLE
        // Posiziona il menu dove c'è il dito, senza uscire dallo schermo
        post {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            val menuWidth = menuContainer.width
            val menuHeight = menuContainer.height

            val finalX = x.coerceIn(0f, (screenWidth - menuWidth).toFloat())
            val finalY = y.coerceIn(0f, (screenHeight - menuHeight).toFloat())

            menuContainer.x = finalX
            menuContainer.y = finalY
        }
    }

    fun hide() {
        visibility = GONE
        onDismiss?.invoke()
    }

    fun isShowing(): Boolean = visibility == VISIBLE
}
