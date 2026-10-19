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
        private var lastX = 0f
        private var lastY = 0f

        fun bind(app: AppInfo) {
            icon.setImageDrawable(app.icon)
            label.text = app.label

            itemView.setOnClickListener { onClick(app) }

            itemView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressed = false
                        lastX = event.rawX
                        lastY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (longPressed) {
                            val dx = Math.abs(event.rawX - lastX)
                            val dy = Math.abs(event.rawY - lastY)
                            if (dx > 40 || dy > 40) {
                                onMoveAfterLongPress(app, view)
                                longPressed = false
                            }
                        }
                        false
                    }
                    else -> false
                }
            }

            itemView.setOnLongClickListener {
                longPressed = true
                lastX = it.x + it.translationX
                lastY = it.y + it.translationY
                onLongPress(app, it, lastX, lastY)
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
