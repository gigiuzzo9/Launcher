package com.simplylauncher

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class HomePagerAdapter(
    private val viewPager: ViewPager2,
    private val cols: Int,
    private val rows: Int,
    private val onSlotClick: (HomeSlot) -> Unit,
    private val onSlotLongPress: (HomeSlot, View) -> Unit,
    private val onEmptyCellLongPress: (View) -> Unit,
    private val onPageCreated: (page: Int, recycler: RecyclerView, widgetOverlay: FrameLayout) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    private val pages = mutableListOf<MutableList<HomeSlot>>()
    private val recyclers = mutableListOf<RecyclerView>()
    private val overlays = mutableListOf<FrameLayout>()
    private var cellHeight: Int = 0

    fun setPages(newPages: List<List<HomeSlot>>) {
        pages.clear()
        pages.addAll(newPages.map { it.toMutableList() })
        notifyDataSetChanged()
    }

    fun getPageItems(page: Int): List<HomeSlot> = pages.getOrNull(page) ?: emptyList()

    fun setCellHeight(height: Int) {
        if (cellHeight != height) {
            cellHeight = height
            recyclers.forEach { rv ->
                (rv.adapter as? HomeGridAdapter)?.setCellHeight(height)
            }
        }
    }

    fun getRecycler(page: Int): RecyclerView? = recyclers.getOrNull(page)
    fun getOverlay(page: Int): FrameLayout? = overlays.getOrNull(page)

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        // container che tiene RecyclerView + overlay widget
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val rv = RecyclerView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            layoutManager = GridLayoutManager(parent.context, cols)
            clipToPadding = false
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        // overlay per i widget (sopra il RecyclerView)
        val overlay = FrameLayout(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // non intercetta i tocchi di default: solo i widget lo faranno
            isClickable = false
            isFocusable = false
        }

        container.addView(rv)
        container.addView(overlay)

        return PageViewHolder(container, rv, overlay)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val items = pages[position]

        if (holder.recycler.adapter == null) {
            val adapter = HomeGridAdapter(
                onClick = { slot -> onSlotClick(slot) },
                onLongPress = { slot, view -> onSlotLongPress(slot, view) },
                onEmptyCellLongPress = { view -> onEmptyCellLongPress(view) }
            )
            holder.recycler.adapter = adapter
            if (cellHeight > 0) adapter.setCellHeight(cellHeight)
        }

        (holder.recycler.adapter as HomeGridAdapter).submitList(items)

        while (recyclers.size <= position) recyclers.add(holder.recycler)
        while (overlays.size <= position) overlays.add(holder.overlay)
        if (position < recyclers.size) recyclers[position] = holder.recycler
        if (position < overlays.size) overlays[position] = holder.overlay

        onPageCreated(position, holder.recycler, holder.overlay)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        recyclers.remove(holder.recycler)
        overlays.remove(holder.overlay)
    }

    inner class PageViewHolder(
        container: FrameLayout,
        val recycler: RecyclerView,
        val overlay: FrameLayout
    ) : RecyclerView.ViewHolder(container)
}
