package com.simplylauncher

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Adapter per il ViewPager2 della home.
 * Ogni pagina è un RecyclerView con la sua griglia di HomeSlot.
 *
 * Condivide la stessa "cellHeight" tra tutte le pagine.
 */
class HomePagerAdapter(
    private val viewPager: ViewPager2,
    private val cols: Int,
    private val rows: Int,
    private val onSlotClick: (HomeSlot) -> Unit,
    private val onSlotLongPress: (HomeSlot, View) -> Unit,
    private val onPageCreated: (page: Int, recycler: RecyclerView) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    private val pages = mutableListOf<MutableList<HomeSlot>>()
    private val recyclers = mutableListOf<RecyclerView>()
    private var cellHeight: Int = 0

    fun setPages(newPages: List<List<HomeSlot>>) {
        pages.clear()
        pages.addAll(newPages.map { it.toMutableList() })
        notifyDataSetChanged()
    }

    fun getPageItems(page: Int): List<HomeSlot> =
        pages.getOrNull(page) ?: emptyList()

    fun updatePage(page: Int, slots: List<HomeSlot>) {
        if (page !in pages.indices) return
        pages[page] = slots.toMutableList()
        recyclers.getOrNull(page)?.adapter?.let { adapter ->
            (adapter as? HomeGridAdapter)?.submitList(slots)
        }
    }

    fun setCellHeight(height: Int) {
        if (cellHeight != height) {
            cellHeight = height
            recyclers.forEach { rv ->
                (rv.adapter as? HomeGridAdapter)?.setCellHeight(height)
            }
        }
    }

    fun getRecycler(page: Int): RecyclerView? = recyclers.getOrNull(page)

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val rv = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = GridLayoutManager(parent.context, cols)
            clipToPadding = false
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
                ) {
                    // nessuno spacing extra, le celle hanno altezza fissa
                }
            })
        }
        return PageViewHolder(rv)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val items = pages[position]

        if (holder.recycler.adapter == null) {
            val adapter = HomeGridAdapter(
                onClick = { slot -> onSlotClick(slot) },
                onLongPress = { slot, view -> onSlotLongPress(slot, view) }
            )
            holder.recycler.adapter = adapter
            if (cellHeight > 0) adapter.setCellHeight(cellHeight)
        }

        (holder.recycler.adapter as HomeGridAdapter).submitList(items)

        // registra il recycler per questa pagina
        while (recyclers.size <= position) recyclers.add(holder.recycler)
        if (position < recyclers.size) {
            recyclers[position] = holder.recycler
        }

        onPageCreated(position, holder.recycler)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        // rimuove il riferimento ma non lo distrugge
        recyclers.remove(holder.recycler)
    }

    inner class PageViewHolder(val recycler: RecyclerView) : RecyclerView.ViewHolder(recycler)
}
