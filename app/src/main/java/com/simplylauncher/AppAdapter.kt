package com.simplylauncher

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongPress: (AppInfo, View, Float, Float) -> Unit,
    private val onMoveAfterLongPress: (AppInfo, View) -> Unit
) : ListAdapter<AppInfo, AppAdapter.AppViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)

        private var longPressed = false
        private var startX = 0f
        private var startY = 0f
        private var popupOpened = false

        fun bind(app: AppInfo) {
            icon.setImageDrawable(app.icon)
            label.text = app.label

            itemView.setOnClickListener { onClick(app) }

            itemView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressed = false
                        popupOpened = false
                        startX = event.rawX
                        startY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (popupOpened) {
                            // Misura il movimento DA QUANDO il popup è aperto
                            val dx = Math.abs(event.rawX - startX)
                            val dy = Math.abs(event.rawY - startY)
                            if (dx > 80 || dy > 80) {  // soglia alta: 80px
                                popupOpened = false
                                longPressed = false
                                onMoveAfterLongPress(app, view)
                            }
                        }
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressed = false
                        popupOpened = false
                        false
                    }
                    else -> false
                }
            }

            itemView.setOnLongClickListener { view ->
                if (!longPressed) {
                    longPressed = true
                    popupOpened = true
                    // Reset posizione iniziale al momento dell'apertura del popup
                    startX = view.x
                    startY = view.y
                    onLongPress(app, view, startX, startY)
                }
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.label == newItem.label &&
                   oldItem.packageName == newItem.packageName
        }
    }
}
