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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var homeRecyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var dockDrawerButton: FrameLayout
    private lateinit var dockLayout: View
    private lateinit var homeAdapter: HomeGridAdapter
    private lateinit var dockSlots: List<FrameLayout>

    private lateinit var drawerOverlay: FrameLayout
    private lateinit var drawerContainer: CardView
    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerAdapter: AppAdapter
    private var drawerOpen = false

    private lateinit var contextMenuView: AppContextMenuView
    private var currentApp: AppInfo? = null
    private var currentAnchor: View? = null
    private var currentFromDrawer = false

    private lateinit var dragController: DragController
    private lateinit var dockController: DockController
    private lateinit var touchHelper: ItemTouchHelper

    private var currentCols = 4
    private var currentRows = 6
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

        homeRecyclerView = findViewById(R.id.homeRecyclerView)
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

        setupDragController()
        setupDockController()
        setupHomeGrid()
        setupDrawer()
        setupContextMenu()
        setupDrawerButton()

        lifecycleScope.launch {
            currentCols = Prefs.colsFlow(this@HomeActivity).first()
            currentRows = Prefs.rowsFlow(this@HomeActivity).first()
            applyGridSettings(currentCols)
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

        homeRecyclerView.post {
            updateRecyclerPadding()
            recalculateCellHeight()
        }
        dockLayout.post { updateRecyclerPadding() }

        homeRecyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRecyclerPadding()
            recalculateCellHeight()
        }
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
        if (drawerOpen) closeDrawer()
    }

    // ─────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────

    private fun setupDragController() {
        dragController = DragController(
            activity = this,
            homeRecyclerView = homeRecyclerView,
            drawerOverlay = drawerOverlay,
            onDropOnHome = { pkg, x, y -> handleDropOnHome(pkg, x, y) },
            onDropOnDrawer = { pkg -> handleDropOnDrawer(pkg) }
        )

        homeRecyclerView.setOnDragListener { _, event ->
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

    private fun setupHomeGrid() {
        homeAdapter = HomeGridAdapter(
            onClick = { slot -> AppRepository.launchApp(this, slot.packageName) },
            onLongPress = { slot, view ->
                val app = HomeGridAdapter.AppCache.get(this, slot.packageName)
                if (app != null) {
                    dragController.onLongPress(app, view, DragController.SOURCE_HOME, false)
                }
            }
        )
        homeRecyclerView.adapter = homeAdapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val swapped = homeAdapter.swap(from, to)
                if (swapped) persistHomeSlots()
                return swapped
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        touchHelper.attachToRecyclerView(homeRecyclerView)
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
        homeRecyclerView.visibility = View.VISIBLE
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

        val lm = homeRecyclerView.layoutManager as? GridLayoutManager
        if (lm == null) {
            isDropInProgress = false
            return false
        }
        val cols = lm.spanCount

        val child = homeRecyclerView.findChildViewUnder(x, y)
        val targetPos = if (child != null) {
            homeRecyclerView.getChildAdapterPosition(child)
        } else {
            val cellH = if (homeAdapter.getCellHeightSafe() > 0)
                homeAdapter.getCellHeightSafe()
            else (homeRecyclerView.height - homeRecyclerView.paddingTop - homeRecyclerView.paddingBottom) / currentRows

            val cellW = homeRecyclerView.width / cols.toFloat()
            val col = (x / cellW).toInt().coerceIn(0, cols - 1)
            val row = (y / cellH).toInt().coerceIn(0, currentRows - 1)
            row * cols + col
        }

        if (targetPos == RecyclerView.NO_POSITION) {
            isDropInProgress = false
            return false
        }

        val currentList = homeAdapter.currentList.toMutableList()
        if (targetPos !in currentList.indices) {
            isDropInProgress = false
            return false
        }

        val sourceIndex = currentList.indexOfFirst { it.packageName == pkg }
        val targetSlot = currentList[targetPos]

        if (sourceIndex == -1) {
            currentList[targetPos] = HomeSlot(pkg, targetSlot.row, targetSlot.col)
            homeAdapter.submitList(currentList)

            lifecycleScope.launch {
                try {
                    val slots = Prefs.homeSlotsFlow(this@HomeActivity).first().toMutableList()
                    slots.removeAll { it.packageName == pkg }
                    slots.removeAll { it.row == targetSlot.row && it.col == targetSlot.col }
                    slots.add(HomeSlot(pkg, targetSlot.row, targetSlot.col))
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
            currentList[sourceIndex] = HomeSlot(targetSlot.packageName, sourceSlot.row, sourceSlot.col)
            currentList[targetPos] = HomeSlot(pkg, targetSlot.row, targetSlot.col)
            homeAdapter.submitList(currentList)

            lifecycleScope.launch {
                try {
                    val slots = Prefs.homeSlotsFlow(this@HomeActivity).first().toMutableList()
                    slots.removeAll { it.packageName == pkg }
                    slots.removeAll { it.row == targetSlot.row && it.col == targetSlot.col }
                    slots.add(HomeSlot(pkg, targetSlot.row, targetSlot.col))
                    if (sourceSlot.packageName.isNotBlank() && sourceSlot.packageName != pkg) {
                        slots.removeAll { it.packageName == sourceSlot.packageName }
                        slots.add(HomeSlot(sourceSlot.packageName, sourceSlot.row, sourceSlot.col))
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
    // GRIGLIA ADATTIVA
    // ─────────────────────────────────────────────────────────────

    private fun updateRecyclerPadding() {
        val dockHeight = if (dockLayout.height > 0) {
            dockLayout.height + (10 * resources.displayMetrics.density).toInt()
        } else {
            (90 * resources.displayMetrics.density).toInt()
        }
        if (homeRecyclerView.paddingBottom != dockHeight) {
            homeRecyclerView.setPadding(
                homeRecyclerView.paddingLeft,
                homeRecyclerView.paddingTop,
                homeRecyclerView.paddingRight,
                dockHeight
            )
        }
    }

    private fun recalculateCellHeight() {
        if (homeRecyclerView.height == 0 || currentRows <= 0) return

        val availableHeight = homeRecyclerView.height -
                homeRecyclerView.paddingTop -
                homeRecyclerView.paddingBottom

        if (availableHeight <= 0) return

        val cellHeight = availableHeight / currentRows
        if (cellHeight > 0) {
            homeAdapter.setCellHeight(cellHeight)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────

    private fun applyGridSettings(cols: Int) {
        homeRecyclerView.layoutManager = GridLayoutManager(this, cols)
    }

    /**
     * Clampa le posizioni fuori griglia (row >= currentRows) e salva.
     */
    private fun clampSlotsToGrid(slots: List<HomeSlot>): List<HomeSlot> {
        if (slots.isEmpty()) return slots
        val out = mutableListOf<HomeSlot>()
        val occupied = mutableSetOf<Pair<Int, Int>>()

        // prima passata: slot validi
        slots.forEach { s ->
            val r = s.row.coerceIn(0, currentRows - 1)
            val c = s.col.coerceIn(0, currentCols - 1)
            val key = r to c
            if (!occupied.contains(key)) {
                occupied.add(key)
                out.add(HomeSlot(s.packageName, r, c))
            }
        }

        // seconda passata: slot che collidevano -> cerca posto libero
        slots.forEach { s ->
            val alreadyPresent = out.any { it.packageName == s.packageName }
            if (!alreadyPresent) {
                // cerca posto libero
                var placed = false
                for (r in 0 until currentRows) {
                    for (c in 0 until currentCols) {
                        if (!occupied.contains(r to c)) {
                            occupied.add(r to c)
                            out.add(HomeSlot(s.packageName, r, c))
                            placed = true
                            break
                        }
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
            val slots = clampSlotsToGrid(rawSlots)

            // se il clamp ha cambiato qualcosa, salva
            if (slots.size != rawSlots.size || slots != rawSlots) {
                Prefs.setHomeSlots(this@HomeActivity, slots)
            }

            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            HomeGridAdapter.AppCache.putAll(allApps)

            val maxIndex = slots.maxOfOrNull { it.row * currentCols + it.col } ?: -1
            val minSlots = currentCols * currentRows
            val totalSlots = maxOf(maxIndex + 1, minSlots)

            val fullList = (0 until totalSlots).map { index ->
                val row = index / currentCols
                val col = index % currentCols
                slots.firstOrNull { it.row == row && it.col == col }
                    ?: HomeSlot("", row, col)
            }
            homeAdapter.submitList(fullList) {
                homeRecyclerView.post {
                    updateRecyclerPadding()
                    recalculateCellHeight()
                }
            }

            if (!drawerOpen) {
                if (slots.isEmpty()) {
                    emptyMessage.visibility = View.VISIBLE
                    homeRecyclerView.visibility = View.GONE
                } else {
                    emptyMessage.visibility = View.GONE
                    homeRecyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    fun reloadHomeGrid() {
        loadHomeApps()
    }

    private fun persistHomeSlots() {
        lifecycleScope.launch {
            val current = homeAdapter.currentList.filter { it.packageName.isNotBlank() }
            Prefs.setHomeSlots(this@HomeActivity, current)
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
        homeRecyclerView.visibility = View.INVISIBLE
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
        homeRecyclerView.visibility = View.VISIBLE
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
            applyGridSettings(currentCols)
            loadHomeApps()
            dockController.refresh()
            loadDrawerApps()
            homeRecyclerView.post {
                updateRecyclerPadding()
                recalculateCellHeight()
            }
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
