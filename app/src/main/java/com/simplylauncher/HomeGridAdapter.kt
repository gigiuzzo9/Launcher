package com.simplylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class HomeGridAdapter(
    private val onClick: (HomeSlot) -> Unit,
    private val onLongPress: (HomeSlot, View) -> Unit
) : ListAdapter<HomeSlot, HomeGridAdapter.SlotViewHolder>(DiffCallback()) {

    private var cellHeight: Int = 0

    fun setCellHeight(height: Int) {
        if (cellHeight != height) {
            cellHeight = height
            notifyDataSetChanged()
        }
    }

    fun getCellHeightSafe(): Int = cellHeight

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun swap(from: Int, to: Int): Boolean {
        val current = currentList.toMutableList()
        if (from !in current.indices || to !in current.indices) return false
        val a = current[from]
        val b = current[to]
        current[from] = a.copy(row = b.row, col = b.col)
        current[to] = b.copy(row = a.row, col = a.col)
        submitList(current)
        return true
    }

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(slot: HomeSlot) {
            if (cellHeight > 0) {
                val lp = itemView.layoutParams
                if (lp.height != cellHeight) {
                    lp.height = cellHeight
                    itemView.layoutParams = lp
                }
            }

            val app = AppCache.get(itemView.context, slot.packageName)
            if (app != null) {
                icon.setImageDrawable(app.icon)
                label.text = app.label
                itemView.visibility = View.VISIBLE
                itemView.setOnClickListener { onClick(slot) }
                itemView.setOnLongClickListener { view ->
                    onLongPress(slot, view)
                    true
                }
            } else {
                icon.setImageDrawable(null)
                label.text = ""
                itemView.visibility = View.INVISIBLE
                itemView.setOnClickListener(null)
                itemView.setOnLongClickListener(null)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HomeSlot>() {
        override fun areItemsTheSame(oldItem: HomeSlot, newItem: HomeSlot): Boolean {
            return oldItem.row == newItem.row && oldItem.col == newItem.col
        }

        override fun areContentsTheSame(oldItem: HomeSlot, newItem: HomeSlot): Boolean {
            return oldItem == newItem
        }
    }

    object AppCache {
        private val map = mutableMapOf<String, AppInfo>()
        fun putAll(apps: List<AppInfo>) {
            map.clear()
            apps.forEach { map[it.packageName] = it }
        }
        fun get(context: android.content.Context, pkg: String): AppInfo? = map[pkg]
    }
}
