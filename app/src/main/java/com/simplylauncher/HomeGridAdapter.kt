package com.simplylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter per la griglia della home.
 * Ogni item rappresenta un HomeSlot (posizione fissa row/col).
 * Supporta swap tra celle tramite moveItem().
 */
class HomeGridAdapter(
    private val onClick: (HomeSlot) -> Unit,
    private val onLongPress: (HomeSlot, View) -> Unit
) : ListAdapter<HomeSlot, HomeGridAdapter.SlotViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Scambia due slot (usato da ItemTouchHelper per il riordino).
     * Ritorna true se lo swap è avvenuto.
     */
    fun swap(from: Int, to: Int): Boolean {
        val current = currentList.toMutableList()
        if (from !in current.indices || to !in current.indices) return false
        val a = current[from]
        val b = current[to]
        // scambia le posizioni (row/col) mantenendo i packageName
        current[from] = a.copy(row = b.row, col = b.col)
        current[to] = b.copy(row = a.row, col = a.col)
        submitList(current)
        return true
    }

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(slot: HomeSlot) {
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
                // app non installata: cella vuota
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

    /**
     * Cache leggera per evitare di risolvere icone ad ogni bind.
     * Viene popolata da HomeActivity prima del submitList.
     */
    object AppCache {
        private val map = mutableMapOf<String, AppInfo>()
        fun putAll(apps: List<AppInfo>) {
            map.clear()
            apps.forEach { map[it.packageName] = it }
        }
        fun get(context: android.content.Context, pkg: String): AppInfo? = map[pkg]
    }
}
