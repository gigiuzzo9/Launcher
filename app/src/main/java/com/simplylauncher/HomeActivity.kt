package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var homeViewPager: ViewPager2
    private lateinit var pageIndicator: PageIndicatorView
    private lateinit var emptyMessage: TextView
    private lateinit var dockDrawerButton: FrameLayout
    private lateinit var dockLayout: View
    private lateinit var dockSlots: List<FrameLayout>

    private lateinit var homePagerAdapter: HomePagerAdapter

    private lateinit var drawerOverlay: FrameLayout
    private lateinit var drawerContainer: CardView
    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerAdapter: AppAdapter
    private var drawerOpen = false

    private lateinit var contextMenuView: AppContextMenuView
    private var currentApp: AppInfo? = null
    private var currentAnchor: View? = null
    private var currentFromDrawer = false

    private lateinit var homePopupMenu: HomePopupMenu

    private lateinit var dragController: DragController
    private lateinit var dockController: DockController

    private var currentCols = 4
    private var currentRows = 6
    private var currentPageCount = 1
    private var isDropInProgress = false

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadHomeApps()
            dockController.refresh()
            loadDrawerApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.parseColor("#80000000")

        homeViewPager = findViewById(R.id.homeViewPager)
        pageIndicator = findViewById(R.id.pageIndicator)
        emptyMessage = findViewById(R.id.emptyMessage)
        dockDrawerButton = findViewById(R.id.dockDrawerButton)
        dockLayout = findViewById(R.id.dockLayout)

        dockSlots = listOf(
            findViewById(R.id.dockSlot1),
            findViewById(R.id.dockSlot2),
            findViewById(R.id.dockSlot3),
            findViewById(R.id.dockSlot4)
        )

        drawerOverlay = findViewById(R.id.drawerOverlay)
        drawerContainer = findViewById(R.id.drawerContainer)
        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)

        setupHomePager()
        setupDragController()
        setupDockController()
        setupDrawer()
        setupContextMenu()
        setupHomePopup()
        setupDrawerButton()

        lifecycleScope.launch {
            currentCols = Prefs.colsFlow(this@HomeActivity).first()
            currentRows = Prefs.rowsFlow(this@HomeActivity).first()
            currentPageCount = Prefs.pageCountFlow(this@HomeActivity).first()
            rebuildPagerAdapter()
            loadHomeApps()
            dockController.refresh()
            loadDrawerApps()
        }

        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                loadHomeApps()
                dockController.refresh()
                loadDrawerApps()
            }
        }

        askLauncherIfNeeded()

        registerReceiver(
            reloadReceiver,
            IntentFilter("com.simplylauncher.RELOAD"),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::dragController.isInitialized && dragController.onInterceptTouch(ev)) {
            return true
        }
        if (::contextMenuView.isInitialized && contextMenuView.isShowing()) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                if (!contextMenuView.isPointInside(ev.rawX, ev.rawY)) {
                    contextMenuView.dismiss()
                    currentApp = null
                    currentAnchor = null
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::contextMenuView.isInitialized && contextMenuView.isShowing()) {
            contextMenuView.dismiss()
            currentApp = null
            currentAnchor = null
            return
        }
        if (::homePopupMenu.isInitialized && homePopupMenu.isShowing()) {
            homePopupMenu.dismiss()
            return
        }
        if (drawerOpen) closeDrawer()
    }

    // ─────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────

    private fun setupHomePager() {
        homeViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.setCurrentPage(position)
            }
        })
    }

    private fun setupDragController() {
        dragController = DragController(
            activity = this,
            homeRecyclerView = homeViewPager,
            drawerOverlay = drawerOverlay,
            onDropOnHome = { pkg, x, y -> handleDropOnHome(pkg, x, y) },
            onDropOnDrawer = { pkg -> handleDropOnDrawer(pkg) }
        )

        // listener drop sul ViewPager (per swap tra pagine - per ora stesso comportamento)
        homeViewPager.setOnDragListener { _, event ->
            dragController.handleHomeDragEvent(event)
        }

        drawerRecyclerView.setOnDragListener { _, event ->
            dragController.handleDrawerDragEvent(event)
        }

        drawerOverlay.setOnDragListener { _, event ->
            dragController.handleDrawerDragEvent(event)
        }

        dockLayout.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    return@setOnDragListener event.clipDescription
                        .hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)

                DragEvent.ACTION_DROP -> {
                    val pkg = event.clipData.getItemAt(0).text.toString()
                    val source = event.clipData.description.label?.toString() ?: ""

                    var closestIndex = 0
                    var closestDist = Float.MAX_VALUE
                    val dockLoc = IntArray(2)
                    dockLayout.getLocationOnScreen(dockLoc)
                    val dropScreenX = dockLoc[0] + event.x
                    val dropScreenY = dockLoc[1] + event.y

                    dockSlots.forEachIndexed { index, slot ->
                        val loc = IntArray(2)
                        slot.getLocationOnScreen(loc)
                        val cx = loc[0] + slot.width / 2f
                        val cy = loc[1] + slot.height / 2f
                        val dist = Math.hypot(
                            (dropScreenX - cx).toDouble(),
                            (dropScreenY - cy).toDouble()
                        ).toFloat()
                        if (dist < closestDist) {
                            closestDist = dist
                            closestIndex = index
                        }
                    }

                    lifecycleScope.launch {
                        if (source == DragController.SOURCE_DOCK) {
                            dockController.swapDockSlots(pkg, closestIndex)
                        } else {
                            dockController.addToDockAtPublic(pkg, closestIndex)
                        }
                    }
                    return@setOnDragListener true
                }

                DragEvent.ACTION_DRAG_ENDED -> return@setOnDragListener true
                else -> return@setOnDragListener false
            }
        }
    }

    private fun setupDockController() {
        dockController = DockController(
            activity = this,
            slots = dockSlots,
            onAppLongPressed = { app, view ->
                dragController.onLongPress(app, view, DragController.SOURCE_DOCK, false)
            }
        )
        dockController.setup()
    }

    private fun setupDrawer() {
        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        drawerRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
            ) {
                val spacing = (12 * resources.displayMetrics.density).toInt()
                outRect.bottom = spacing
            }
        })
        drawerAdapter = AppAdapter(
            onClick = { app ->
                AppRepository.launchApp(this, app.packageName)
                closeDrawer()
            },
            onLongPress = { app, view ->
                dragController.onLongPress(app, view, DragController.SOURCE_DRAWER, true)
            }
        )
        drawerRecyclerView.adapter = drawerAdapter

        drawerOverlay.setOnClickListener { closeDrawer() }
    }

    private fun setupContextMenu() {
        contextMenuView = AppContextMenuView(this)

        contextMenuView.onMove = { contextMenuView.dismiss() }

        contextMenuView.onUninstall = {
            currentApp?.let { app ->
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = android.net.Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        contextMenuView.onRename = {
            currentApp?.let { app ->
                RenameDialog.show(this, app) {
                    loadHomeApps()
                    dockController.refresh()
                    loadDrawerApps()
                }
            }
        }

        contextMenuView.onRemove = {
            currentApp?.let { app ->
                lifecycleScope.launch {
                    val homeSlots = Prefs.homeSlotsFlow(this@HomeActivity).first()
                    val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
                    Prefs.setHomeSlots(this@HomeActivity, homeSlots.filter { it.packageName != app.packageName })
                    Prefs.setDockItems(this@HomeActivity, dockItems.map { if (it == app.packageName) "" else it })
                    loadHomeApps()
                    dockController.refresh()
                }
            }
        }

        contextMenuView.onDismiss = {
            currentApp = null
            currentAnchor = null
        }
    }

    private fun setupHomePopup() {
        homePopupMenu = HomePopupMenu(this)

        homePopupMenu.onAddWidget = {
            // TODO: implementare selezione widget
        }

        homePopupMenu.onAddPage = {
            lifecycleScope.launch {
                currentPageCount += 1
                Prefs.setPageCount(this@HomeActivity, currentPageCount)
                rebuildPagerAdapter()
                loadHomeApps()
            }
        }

        homePopupMenu.onChangeWallpaper = {
            try {
                val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                startActivity(intent)
            } catch (_: Exception) {}
        }

        homePopupMenu.onRemovePage = {
            if (currentPageCount > 1) {
                val currentPageIndex = homeViewPager.currentItem
                // non rimuovere la pagina se ci sono icone (per ora sicurezza)
                lifecycleScope.launch {
                    val slots = Prefs.homeSlotsFlow(this@HomeActivity).first()
                    val itemsOnPage = slots.count { it.page == currentPageIndex }
                    if (itemsOnPage > 0) {
                        // non rimuovere, sposta prima le icone
                        return@launch
                    }
                    // rimuovi la pagina e shift delle successive
                    val newSlots = slots.map {
                        if (it.page > currentPageIndex) it.copy(page = it.page - 1) else it
                    }
                    Prefs.setHomeSlots(this@HomeActivity, newSlots)
                    currentPageCount -= 1
                    Prefs.setPageCount(this@HomeActivity, currentPageCount)
                    rebuildPagerAdapter()
                    loadHomeApps()
                }
            }
        }
    }

    private fun setupDrawerButton() {
        dockDrawerButton.removeAllViews()
        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_drawer)
            val size = (62 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        }
        dockDrawerButton.addView(imageView)
        dockDrawerButton.setOnClickListener { openDrawer() }
    }

    // ─────────────────────────────────────────────────────────────
    // PAGER
    // ─────────────────────────────────────────────────────────────

    private fun rebuildPagerAdapter() {
        homePagerAdapter = HomePagerAdapter(
            viewPager = homeViewPager,
            cols = currentCols,
            rows = currentRows,
            onSlotClick = { slot ->
                AppRepository.launchApp(this, slot.packageName)
            },
            onSlotLongPress = { slot, view ->
                val app = HomeGridAdapter.AppCache.get(this, slot.packageName)
                if (app != null) {
                    dragController.onLongPress(app, view, DragController.SOURCE_HOME, false)
                }
            },
            onPageCreated = { page, recycler ->
                setupRecyclerTouchHelper(recycler)
            }
        )
        homeViewPager.adapter = homePagerAdapter
        pageIndicator.setPageCount(currentPageCount)
        pageIndicator.setCurrentPage(homeViewPager.currentItem.coerceIn(0, currentPageCount - 1))

        // setup drag listener + click listener sul ViewPager per popup punto vuoto
        setupViewPagerTouchListener()
    }

    private fun setupRecyclerTouchHelper(recycler: RecyclerView) {
        // aggiunge ItemTouchHelper una sola volta per recycler
        if (recycler.tag != null) return
        recycler.tag = "touch_helper_attached"

        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val adapter = rv.adapter as? HomeGridAdapter ?: return false
                val swapped = adapter.swap(from, to)
                if (swapped) persistHomeSlotsFromAdapter(adapter)
                return swapped
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        helper.attachToRecyclerView(recycler)
    }

    private fun setupViewPagerTouchListener() {
        // listener click su ViewPager per aprire popup su punto vuoto
        // NOTA: il click su icona è gestito da HomeGridAdapter
        homeViewPager.setOnClickListener {
            showHomePopup()
        }
    }

    private fun showHomePopup() {
        val anchor = findViewById<View>(android.R.id.content)
        homePopupMenu.show(anchor, showRemovePage = currentPageCount > 1)
    }

    // ─────────────────────────────────────────────────────────────
    // METODI CHIAMATI DA DragController
    // ─────────────────────────────────────────────────────────────

    fun showContextMenuForDrag(app: AppInfo, anchor: View, fromDrawer: Boolean) {
        currentApp = app
        currentAnchor = anchor
        currentFromDrawer = fromDrawer
        contextMenuView.show(anchor, showRemove = !fromDrawer)
    }

    fun dismissDragPopup() {
        contextMenuView.dismiss()
        currentApp = null
        currentAnchor = null
    }

    fun onDragFromDrawerStarted() {
        drawerOverlay.visibility = View.GONE
        drawerOverlay.alpha = 1f
        drawerOpen = false
        homeViewPager.visibility = View.VISIBLE
        dockLayout.visibility = View.VISIBLE
        loadHomeApps()
    }

    fun onDragFromDrawerEnded() {
        if (!drawerOpen) {
            drawerOverlay.alpha = 1f
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DROP
    // ─────────────────────────────────────────────────────────────

    private fun handleDropOnHome(pkg: String, x: Float, y: Float): Boolean {
        if (isDropInProgress) return false
        isDropInProgress = true

        val pageIndex = homeViewPager.currentItem
        val recycler = homePagerAdapter.getRecycler(pageIndex)
        if (recycler == null) {
            isDropInProgress = false
            return false
        }
        val lm = recycler.layoutManager as? GridLayoutManager
        if (lm == null) {
            isDropInProgress = false
            return false
        }
        val cols = lm.spanCount

        // coordinate relative al ViewPager → relative al RecyclerView della pagina
        val pagerLoc = IntArray(2)
        homeViewPager.getLocationOnScreen(pagerLoc)
        val recyclerLoc = IntArray(2)
        recycler.getLocationOnScreen(recyclerLoc)
        val rvX = x + pagerLoc[0] - recyclerLoc[0]
        val rvY = y + pagerLoc[1] - recyclerLoc[1]

        val child = recycler.findChildViewUnder(rvX, rvY)
        val targetPos = if (child != null) {
            recycler.getChildAdapterPosition(child)
        } else {
            val cellH = if (homePagerAdapter.getPageItems(pageIndex).isNotEmpty())
                (recycler.height - recycler.paddingTop - recycler.paddingBottom) / currentRows
            else (recycler.height - recycler.paddingTop - recycler.paddingBottom) / currentRows
            val cellW = recycler.width / cols.toFloat()
            val col = (rvX / cellW).toInt().coerceIn(0, cols - 1)
            val row = (rvY / cellH).toInt().coerceIn(0, currentRows - 1)
            row * cols + col
        }

        if (targetPos == RecyclerView.NO_POSITION) {
            isDropInProgress = false
            return false
        }

        val adapter = recycler.adapter as? HomeGridAdapter
        if (adapter == null) {
            isDropInProgress = false
            return false
        }
        val currentList = adapter.currentList.toMutableList()
        if (targetPos !in currentList.indices) {
            isDropInProgress = false
            return false
        }

        val sourceIndex = currentList.indexOfFirst { it.packageName == pkg }
        val targetSlot = currentList[targetPos]

        if (sourceIndex == -1) {
            currentList[targetPos] = HomeSlot(pkg, targetSlot.row, targetSlot.col, pageIndex)
            adapter.submitList(currentList)

            lifecycleScope.launch {
                try {
                    val slots = Prefs.homeSlotsFlow(this@HomeActivity).first().toMutableList()
                    slots.removeAll { it.packageName == pkg }
                    slots.removeAll { it.page == pageIndex && it.row == targetSlot.row && it.col == targetSlot.col }
                    slots.add(HomeSlot(pkg, targetSlot.row, targetSlot.col, pageIndex))
                    Prefs.setHomeSlots(this@HomeActivity, slots)

                    val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
                    if (dockItems.contains(pkg)) {
                        Prefs.setDockItems(this@HomeActivity, dockItems.map { if (it == pkg) "" else it })
                        dockController.refresh()
                    }
                } finally {
                    isDropInProgress = false
                }
            }
        } else {
            val sourceSlot = currentList[sourceIndex]
            currentList[sourceIndex] = HomeSlot(targetSlot.packageName, sourceSlot.row, sourceSlot.col, pageIndex)
            currentList[targetPos] = HomeSlot(pkg, targetSlot.row, targetSlot.col, pageIndex)
            adapter.submitList(currentList)

            lifecycleScope.launch {
                try {
                    val slots = Prefs.homeSlotsFlow(this@HomeActivity).first().toMutableList()
                    slots.removeAll { it.packageName == pkg }
                    slots.removeAll { it.page == pageIndex && it.row == targetSlot.row && it.col == targetSlot.col }
                    slots.add(HomeSlot(pkg, targetSlot.row, targetSlot.col, pageIndex))
                    if (sourceSlot.packageName.isNotBlank() && sourceSlot.packageName != pkg) {
                        slots.removeAll { it.packageName == sourceSlot.packageName }
                        slots.add(HomeSlot(sourceSlot.packageName, sourceSlot.row, sourceSlot.col, pageIndex))
                    }
                    Prefs.setHomeSlots(this@HomeActivity, slots)
                } finally {
                    isDropInProgress = false
                }
            }
        }
        return true
    }

    private fun handleDropOnDrawer(pkg: String) {
        lifecycleScope.launch {
            val slots = Prefs.homeSlotsFlow(this@HomeActivity).first()
            Prefs.setHomeSlots(this@HomeActivity, slots.filter { it.packageName != pkg })
            val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
            Prefs.setDockItems(this@HomeActivity, dockItems.map { if (it == pkg) "" else it })
            loadHomeApps()
            dockController.refresh()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────

    private fun clampSlotsToGrid(slots: List<HomeSlot>): List<HomeSlot> {
        if (slots.isEmpty()) return slots
        val out = mutableListOf<HomeSlot>()
        val occupied = mutableSetOf<Triple<Int, Int, Int>>() // page, row, col

        slots.forEach { s ->
            val p = s.page.coerceAtLeast(0)
            val r = s.row.coerceIn(0, currentRows - 1)
            val c = s.col.coerceIn(0, currentCols - 1)
            val key = Triple(p, r, c)
            if (!occupied.contains(key)) {
                occupied.add(key)
                out.add(HomeSlot(s.packageName, r, c, p))
            }
        }

        // second pass: place colliding items in free cells
        slots.forEach { s ->
            if (out.none { it.packageName == s.packageName }) {
                var placed = false
                for (p in 0 until currentPageCount) {
                    for (r in 0 until currentRows) {
                        for (c in 0 until currentCols) {
                            val key = Triple(p, r, c)
                            if (!occupied.contains(key)) {
                                occupied.add(key)
                                out.add(HomeSlot(s.packageName, r, c, p))
                                placed = true
                                break
                            }
                        }
                        if (placed) break
                    }
                    if (placed) break
                }
            }
        }
        return out
    }

    private fun loadHomeApps() {
        lifecycleScope.launch {
            val rawSlots = Prefs.homeSlotsFlow(this@HomeActivity).first()

            // calcola pageCount effettivo
            val maxPage = rawSlots.maxOfOrNull { it.page } ?: 0
            val effectivePageCount = maxOf(currentPageCount, maxPage + 1)
            if (effectivePageCount != currentPageCount) {
                currentPageCount = effectivePageCount
                Prefs.setPageCount(this@HomeActivity, currentPageCount)
                pageIndicator.setPageCount(currentPageCount)
            }

            val slots = clampSlotsToGrid(rawSlots)
            if (slots.size != rawSlots.size || slots != rawSlots) {
                Prefs.setHomeSlots(this@HomeActivity, slots)
            }

            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            HomeGridAdapter.AppCache.putAll(allApps)

            // costruisce le pagine
            val pages = (0 until currentPageCount).map { page ->
                val minSlots = currentCols * currentRows
                val pageSlots = slots.filter { it.page == page }
                val maxIndex = pageSlots.maxOfOrNull { it.row * currentCols + it.col } ?: -1
                val totalSlots = maxOf(maxIndex + 1, minSlots)
                (0 until totalSlots).map { index ->
                    val r = index / currentCols
                    val c = index % currentCols
                    pageSlots.firstOrNull { it.row == r && it.col == c }
                        ?: HomeSlot("", r, c, page)
                }
            }

            homePagerAdapter.setPages(pages)
            pageIndicator.setPageCount(currentPageCount)
            pageIndicator.setCurrentPage(homeViewPager.currentItem.coerceIn(0, currentPageCount - 1))

            if (!drawerOpen) {
                if (slots.isEmpty()) {
                    emptyMessage.visibility = View.VISIBLE
                } else {
                    emptyMessage.visibility = View.GONE
                }
            }
        }
    }

    fun reloadHomeGrid() {
        loadHomeApps()
    }

    private fun persistHomeSlotsFromAdapter(adapter: HomeGridAdapter) {
        lifecycleScope.launch {
            val pageIndex = homeViewPager.currentItem
            val current = adapter.currentList.filter { it.packageName.isNotBlank() }
                .map { it.copy(page = pageIndex) }

            val allSlots = Prefs.homeSlotsFlow(this@HomeActivity).first().toMutableList()
            allSlots.removeAll { it.page == pageIndex }
            allSlots.addAll(current)
            Prefs.setHomeSlots(this@HomeActivity, allSlots)
        }
    }

    private fun loadDrawerApps() {
        lifecycleScope.launch {
            drawerAdapter.submitList(AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DRAWER
    // ─────────────────────────────────────────────────────────────

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        homeViewPager.visibility = View.INVISIBLE
        pageIndicator.visibility = View.INVISIBLE
        dockLayout.visibility = View.INVISIBLE
        emptyMessage.visibility = View.INVISIBLE
        applyDrawerBackground()
        drawerOverlay.alpha = 1f
        drawerOverlay.visibility = View.VISIBLE
        drawerOverlay.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_up))
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawerOverlay.visibility = View.GONE
        homeViewPager.visibility = View.VISIBLE
        pageIndicator.visibility = View.VISIBLE
        dockLayout.visibility = View.VISIBLE
        loadHomeApps()
    }

    private fun applyDrawerBackground() {
        lifecycleScope.launch {
            val color = Prefs.colorFlow(this@HomeActivity).first()
            val alpha = Prefs.alphaFlow(this@HomeActivity).first()
            drawerContainer.setCardBackgroundColor(
                Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (drawerOpen) closeDrawer()
        lifecycleScope.launch {
            currentCols = Prefs.colsFlow(this@HomeActivity).first()
            currentRows = Prefs.rowsFlow(this@HomeActivity).first()
            currentPageCount = Prefs.pageCountFlow(this@HomeActivity).first()
            rebuildPagerAdapter()
            loadHomeApps()
            dockController.refresh()
            loadDrawerApps()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    private fun askLauncherIfNeeded() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(homeIntent, 0)
        if (res?.activityInfo?.packageName == packageName) return
        val prefs = getSharedPreferences("launcher_flags", MODE_PRIVATE)
        if (prefs.getBoolean("asked_launcher", false)) return
        prefs.edit().putBoolean("asked_launcher", true).apply()
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
        }
    }
}
