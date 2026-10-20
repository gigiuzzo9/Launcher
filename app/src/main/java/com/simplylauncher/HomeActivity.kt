package com.simplylauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var widgetOverlay: FrameLayout
    private lateinit var resizeOverlay: FrameLayout
    private lateinit var resizeFrame: WidgetResizeFrame

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

    // Widget
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetHostId = 1001
    private var pendingWidgetId = -1
    private var pendingWidgetPage = 0
    private var pendingWidgetRow = 0
    private var pendingWidgetCol = 0
    private var pendingWidgetWidthCells = 2
    private var pendingWidgetHeightCells = 1

    private var resizingWidgetSlot: WidgetSlot? = null

    private var currentCols = 4
    private var currentRows = 6
    private var currentPageCount = 1
    private var isDropInProgress = false
    private var currentDrawerColor: Int = Color.BLACK

    private val REQUEST_PICK_WIDGET = 100
    private val REQUEST_BIND_WIDGET = 101
    private val REQUEST_CONFIGURE_WIDGET = 102

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
        widgetOverlay = findViewById(R.id.widgetOverlay)
        resizeOverlay = findViewById(R.id.resizeOverlay)
        resizeFrame = findViewById(R.id.resizeFrame)

        dockSlots = listOf(
            findViewById(R.id.dockSlot1),
            findViewById(R.id.dockSlot2),
            findViewById(R.id.dockSlot3),
            findViewById(R.id.dockSlot4)
        )

        drawerOverlay = findViewById(R.id.drawerOverlay)
        drawerContainer = findViewById(R.id.drawerContainer)
        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = object : AppWidgetHost(this, widgetHostId) {
            override fun onCreateView(
                context: Context,
                appWidgetId: Int,
                appWidget: AppWidgetProviderInfo
            ): AppWidgetHostView {
                return LauncherAppWidgetHostView(context)
            }
        }

        setupResizeFrame()
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
            currentDrawerColor = Prefs.colorFlow(this@HomeActivity).first()
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

        homeViewPager.post { recalculateCellHeight() }
        dockLayout.post { recalculateCellHeight() }

        homeViewPager.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            recalculateCellHeight()
        }
    }

    private fun setupResizeFrame() {
        resizeFrame.onResizeEnd = { left, top, right, bottom ->
            resizingWidgetSlot?.let { slot ->
                lifecycleScope.launch {
                    val widgets = Prefs.widgetSlotsFlow(this@HomeActivity).first().toMutableList()
                    val index = widgets.indexOfFirst { it.appWidgetId == slot.appWidgetId }
                    if (index != -1) {
                        widgets[index] = slot.copy(
                            row = top,
                            col = left,
                            widthCells = right - left + 1,
                            heightCells = bottom - top + 1
                        )
                        Prefs.setWidgetSlots(this@HomeActivity, widgets)
                    }
                    resizingWidgetSlot = null
                    resizeOverlay.visibility = View.GONE
                    resizeFrame.visibility = View.GONE
                    renderWidgets()
                }
            }
        }
        resizeFrame.onResizeCancel = {
            resizingWidgetSlot = null
            resizeOverlay.visibility = View.GONE
            resizeFrame.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        try { appWidgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_WIDGET -> {
                if (resultCode == RESULT_OK && data != null) {
                    val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    val needsBind = data.getBooleanExtra(WidgetPickerActivity.EXTRA_NEEDS_BIND, false)
                    val provider: android.content.ComponentName? =
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            data.getParcelableExtra(
                                WidgetPickerActivity.EXTRA_PROVIDER,
                                android.content.ComponentName::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            data.getParcelableExtra(WidgetPickerActivity.EXTRA_PROVIDER)
                        }

                    if (widgetId != -1) {
                        pendingWidgetId = widgetId
                        if (needsBind && provider != null) {
                            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, Bundle())
                            }
                            startActivityForResult(bindIntent, REQUEST_BIND_WIDGET)
                        } else {
                            lifecycleScope.launch { findFreeCellAndPlaceWidget() }
                        }
                    }
                } else {
                    if (pendingWidgetId != -1) {
                        appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                        pendingWidgetId = -1
                    }
                }
            }
            REQUEST_BIND_WIDGET -> {
                if (resultCode == RESULT_OK) {
                    val info = appWidgetManager.getAppWidgetInfo(pendingWidgetId)
                    if (info != null) {
                        if (info.configure != null) {
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                component = info.configure
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                            }
                            startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET)
                        } else {
                            saveWidgetToHome()
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.widget_bind_denied, Toast.LENGTH_SHORT).show()
                    appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                    pendingWidgetId = -1
                }
            }
            REQUEST_CONFIGURE_WIDGET -> {
                if (resultCode == RESULT_OK) saveWidgetToHome()
                else Toast.makeText(this, R.string.widget_config_cancelled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun findFreeCellAndPlaceWidget() {
        val slots = Prefs.homeSlotsFlow(this@HomeActivity).first()
        val widgets = Prefs.widgetSlotsFlow(this@HomeActivity).first()
        val occupied = mutableSetOf<Pair<Int, Int>>()
        slots.filter { it.page == pendingWidgetPage }.forEach { occupied.add(it.row to it.col) }
        widgets.filter { it.page == pendingWidgetPage }.forEach {
            for (r in it.row until it.row + it.heightCells) {
                for (c in it.col until it.col + it.widthCells) occupied.add(r to c)
            }
        }
        var found = false
        for (r in 0 until currentRows) {
            for (c in 0..(currentCols - pendingWidgetWidthCells)) {
                var free = true
                for (dr in 0 until pendingWidgetHeightCells) {
                    for (dc in 0 until pendingWidgetWidthCells) {
                        if (occupied.contains(r + dr to c + dc)) { free = false; break }
                    }
                    if (!free) break
                }
                if (free) {
                    pendingWidgetRow = r
                    pendingWidgetCol = c
                    found = true
                    break
                }
            }
            if (found) break
        }
        if (!found) {
            Toast.makeText(this@HomeActivity, "Nessuno spazio libero", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            return
        }

        val info = appWidgetManager.getAppWidgetInfo(pendingWidgetId)
        if (info == null) {
            Toast.makeText(this@HomeActivity, "Widget non valido", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            return
        }

        try {
            appWidgetHost.createView(baseContext, pendingWidgetId, info)
            if (info.configure != null) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                }
                startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET)
            } else {
                saveWidgetToHome()
            }
        } catch (e: Exception) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, Bundle())
            }
            startActivityForResult(intent, REQUEST_BIND_WIDGET)
        }
    }

    private fun saveWidgetToHome() {
        lifecycleScope.launch {
            val widgets = Prefs.widgetSlotsFlow(this@HomeActivity).first().toMutableList()
            widgets.add(
                WidgetSlot(
                    appWidgetId = pendingWidgetId,
                    row = pendingWidgetRow,
                    col = pendingWidgetCol,
                    widthCells = pendingWidgetWidthCells,
                    heightCells = pendingWidgetHeightCells,
                    page = pendingWidgetPage
                )
            )
            Prefs.setWidgetSlots(this@HomeActivity, widgets)
            Toast.makeText(this@HomeActivity, R.string.widget_added, Toast.LENGTH_SHORT).show()
            homeViewPager.post { homeViewPager.post { renderWidgets() } }
        }
    }

    private fun startWidgetPicker() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = appWidgetId
        pendingWidgetPage = homeViewPager.currentItem
        pendingWidgetWidthCells = 2.coerceAtMost(currentCols)
        pendingWidgetHeightCells = 1

        val intent = Intent(this, WidgetPickerActivity::class.java).apply {
            putExtra(WidgetPickerActivity.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(intent, REQUEST_PICK_WIDGET)
    }

    private fun renderWidgets() {
        lifecycleScope.launch {
            val widgets = Prefs.widgetSlotsFlow(this@HomeActivity).first()
            val recycler = homePagerAdapter.getRecycler(homeViewPager.currentItem) ?: return@launch

            if (recycler.width == 0 || recycler.height == 0) {
                homeViewPager.postDelayed({ renderWidgets() }, 100)
                return@launch
            }
            if (widgetOverlay.width == 0 || widgetOverlay.height == 0) {
                homeViewPager.postDelayed({ renderWidgets() }, 100)
                return@launch
            }

            widgetOverlay.removeAllViews()

            val cellW = recycler.width / currentCols
            val cellH = (recycler.height - recycler.paddingTop - recycler.paddingBottom) / currentRows

            val recyclerLoc = IntArray(2)
            recycler.getLocationOnScreen(recyclerLoc)
            val overlayLoc = IntArray(2)
            widgetOverlay.getLocationOnScreen(overlayLoc)
            val offsetX = recyclerLoc[0] - overlayLoc[0]
            val offsetY = recyclerLoc[1] - overlayLoc[1]

            widgets.filter { it.page == homeViewPager.currentItem }.forEach { slot ->
                val info = appWidgetManager.getAppWidgetInfo(slot.appWidgetId) ?: return@forEach
                val hostView = appWidgetHost.createView(baseContext, slot.appWidgetId, info) as LauncherAppWidgetHostView
                hostView.setAppWidget(slot.appWidgetId, info)

                val lp = FrameLayout.LayoutParams(
                    cellW * slot.widthCells,
                    cellH * slot.heightCells
                ).apply {
                    leftMargin = offsetX + slot.col * cellW
                    topMargin = offsetY + recycler.paddingTop + slot.row * cellH
                }
                widgetOverlay.addView(hostView, lp)

                hostView.onLongPress = {
                    showWidgetMenu(slot, info, cellW, cellH, recycler)
                }
            }
        }
    }

    private fun showWidgetMenu(
    slot: WidgetSlot,
    info: AppWidgetProviderInfo,
    cellW: Int,
    cellH: Int,
    recycler: RecyclerView
) {
    val ctx = this
    val density = resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()

    val container = android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        val drawable = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(Color.WHITE)
        }
        background = drawable
        elevation = 8f * density
    }

    fun addItem(text: String, onClick: () -> Unit) {
        val tv = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.BLACK)
            textSize = 15f
            setPadding(dp(20), dp(14), dp(20), dp(14))
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                onClick()
                popupWindow?.dismiss()
            }
        }
        container.addView(tv)
    }

    addItem("Ridimensiona") {
        startResize(slot, info, cellW, cellH, recycler)
    }
    addItem("Rimuovi widget") {
        lifecycleScope.launch {
            val current = Prefs.widgetSlotsFlow(this@HomeActivity).first()
            Prefs.setWidgetSlots(
                this@HomeActivity,
                current.filter { it.appWidgetId != slot.appWidgetId }
            )
            appWidgetHost.deleteAppWidgetId(slot.appWidgetId)
            renderWidgets()
        }
    }

    popupWindow = android.widget.PopupWindow(
        container,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        isOutsideTouchable = true
        isFocusable = true
        setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        showAtLocation(recycler, Gravity.CENTER, 0, 0)
    }
}

    private fun startResize(
        slot: WidgetSlot,
        info: AppWidgetProviderInfo,
        cellW: Int,
        cellH: Int,
        recycler: RecyclerView
    ) {
        resizingWidgetSlot = slot

        val recyclerLoc = IntArray(2)
        recycler.getLocationOnScreen(recyclerLoc)
        val overlayLoc = IntArray(2)
        resizeOverlay.getLocationOnScreen(overlayLoc)
        val offsetX = recyclerLoc[0] - overlayLoc[0]
        val offsetY = recyclerLoc[1] - overlayLoc[1]
        val paddingTop = recycler.paddingTop

        val frameLeftAbs = offsetX + slot.col * cellW
        val frameTopAbs = offsetY + paddingTop + slot.row * cellH
        val frameRightAbs = frameLeftAbs + slot.widthCells * cellW
        val frameBottomAbs = frameTopAbs + slot.heightCells * cellH

        val minCellsW = (info.minResizeWidth / cellW).coerceAtLeast(1).coerceAtMost(currentCols)
        val minCellsH = (info.minResizeHeight / cellH).coerceAtLeast(1).coerceAtMost(currentRows)

        resizeFrame.configure(
            frameLeft = frameLeftAbs,
            frameTop = frameTopAbs,
            frameRight = frameRightAbs,
            frameBottom = frameBottomAbs,
            cellLeft = slot.col,
            cellTop = slot.row,
            cellRight = slot.col + slot.widthCells - 1,
            cellBottom = slot.row + slot.heightCells - 1,
            cellWidthPx = cellW,
            cellHeightPx = cellH,
            minCellsWidth = minCellsW,
            minCellsHeight = minCellsH,
            maxCellsWidth = currentCols,
            maxCellsHeight = currentRows,
            gridOffsetX = offsetX,
            gridOffsetY = offsetY + paddingTop
        )

        resizeOverlay.visibility = View.VISIBLE
        resizeOverlay.bringToFront()
        resizeOverlay.elevation = 20f * resources.displayMetrics.density
        resizeFrame.visibility = View.VISIBLE
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::dragController.isInitialized && dragController.onInterceptTouch(ev)) return true
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
        if (resizeOverlay.visibility == View.VISIBLE) {
            resizeOverlay.visibility = View.GONE
            resizeFrame.visibility = View.GONE
            resizingWidgetSlot = null
            return
        }
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

    private fun setupHomePager() {
        homeViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.setCurrentPage(position)
                homeViewPager.post {
                    recalculateCellHeight()
                    renderWidgets()
                }
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

        homeViewPager.setOnDragListener { _, event -> dragController.handleHomeDragEvent(event) }
        drawerRecyclerView.setOnDragListener { _, event -> dragController.handleDrawerDragEvent(event) }
        drawerOverlay.setOnDragListener { _, event -> dragController.handleDrawerDragEvent(event) }

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
                        if (dist < closestDist) { closestDist = dist; closestIndex = index }
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
                outRect.bottom = (12 * resources.displayMetrics.density).toInt()
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
        contextMenuView.onDismiss = { currentApp = null; currentAnchor = null }
    }

    private fun setupHomePopup() {
        homePopupMenu = HomePopupMenu(this)
        homePopupMenu.onAddWidget = {
            homePopupMenu.dismiss()
            startWidgetPicker()
        }
        homePopupMenu.onAddPage = {
            lifecycleScope.launch {
                currentPageCount += 1
                Prefs.setPageCount(this@HomeActivity, currentPageCount)
                pageIndicator.setPageCount(currentPageCount)
                rebuildPagerAdapter()
                loadHomeApps()
            }
        }
        homePopupMenu.onChangeWallpaper = {
            try { startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) } catch (_: Exception) {}
        }
        homePopupMenu.onRemovePage = {
            if (currentPageCount > 1) {
                val currentPageIndex = homeViewPager.currentItem
                lifecycleScope.launch {
                    val slots = Prefs.homeSlotsFlow(this@HomeActivity).first()
                    val widgets = Prefs.widgetSlotsFlow(this@HomeActivity).first()
                    val itemsOnPage = slots.count { it.page == currentPageIndex } +
                            widgets.count { it.page == currentPageIndex }
                    if (itemsOnPage > 0) {
                        Toast.makeText(this@HomeActivity, "La pagina non è vuota", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val newSlots = slots.map {
                        if (it.page > currentPageIndex) it.copy(page = it.page - 1) else it
                    }
                    val newWidgets = widgets.map {
                        if (it.page > currentPageIndex) it.copy(page = it.page - 1) else it
                    }
                    Prefs.setHomeSlots(this@HomeActivity, newSlots)
                    Prefs.setWidgetSlots(this@HomeActivity, newWidgets)
                    currentPageCount -= 1
                    if (currentPageCount < 1) currentPageCount = 1
                    Prefs.setPageCount(this@HomeActivity, currentPageCount)
                    pageIndicator.setPageCount(currentPageCount)
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

    private fun rebuildPagerAdapter() {
        homePagerAdapter = HomePagerAdapter(
            viewPager = homeViewPager,
            cols = currentCols,
            rows = currentRows,
            onSlotClick = { slot -> AppRepository.launchApp(this, slot.packageName) },
            onSlotLongPress = { slot, view ->
                val app = HomeGridAdapter.AppCache.get(this, slot.packageName)
                if (app != null) dragController.onLongPress(app, view, DragController.SOURCE_HOME, false)
            },
            onEmptyCellLongPress = { view -> showHomePopup(view) },
            onPageCreated = { _, recycler -> setupRecyclerTouchHelper(recycler) }
        )
        homeViewPager.adapter = homePagerAdapter
        pageIndicator.setPageCount(currentPageCount)
        pageIndicator.setCurrentPage(homeViewPager.currentItem.coerceIn(0, currentPageCount - 1))
        homeViewPager.post {
            recalculateCellHeight()
            renderWidgets()
        }
    }

    private fun setupRecyclerTouchHelper(recycler: RecyclerView) {
        if (recycler.tag != null) return
        recycler.tag = "touch_helper_attached"
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
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

    private fun showHomePopup(anchor: View) {
        homePopupMenu.show(anchor, showRemovePage = currentPageCount > 1)
    }

    fun showContextMenuForDrag(app: AppInfo, anchor: View, fromDrawer: Boolean) {
        currentApp = app
        currentAnchor = anchor
        currentFromDrawer = fromDrawer
        contextMenuView.show(
            anchor,
            showRemove = !fromDrawer,
            drawerIsWhite = fromDrawer && (currentDrawerColor == Color.WHITE)
        )
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
        pageIndicator.visibility = View.VISIBLE
        dockLayout.visibility = View.VISIBLE
        loadHomeApps()
    }

    fun onDragFromDrawerEnded() {
        if (!drawerOpen) drawerOverlay.alpha = 1f
    }

    private fun recalculateCellHeight() {
        if (!::homePagerAdapter.isInitialized) return
        if (homeViewPager.height == 0 || currentRows <= 0) return
        val recycler = homePagerAdapter.getRecycler(homeViewPager.currentItem) ?: return
        if (recycler.height == 0) return
        val availableHeight = recycler.height - recycler.paddingTop - recycler.paddingBottom
        if (availableHeight <= 0) return
        val cellHeight = availableHeight / currentRows
        if (cellHeight > 0) homePagerAdapter.setCellHeight(cellHeight)
    }

    private fun handleDropOnHome(pkg: String, x: Float, y: Float): Boolean {
        if (isDropInProgress) return false
        isDropInProgress = true
        val pageIndex = homeViewPager.currentItem
        val recycler = homePagerAdapter.getRecycler(pageIndex)
        if (recycler == null) { isDropInProgress = false; return false }
        val lm = recycler.layoutManager as? GridLayoutManager
        if (lm == null) { isDropInProgress = false; return false }
        val cols = lm.spanCount

        val pagerLoc = IntArray(2); homeViewPager.getLocationOnScreen(pagerLoc)
        val recyclerLoc = IntArray(2); recycler.getLocationOnScreen(recyclerLoc)
        val rvX = x + pagerLoc[0] - recyclerLoc[0]
        val rvY = y + pagerLoc[1] - recyclerLoc[1]

        val child = recycler.findChildViewUnder(rvX, rvY)
        val targetPos = if (child != null) {
            recycler.getChildAdapterPosition(child)
        } else {
            val cellH = (recycler.height - recycler.paddingTop - recycler.paddingBottom) / currentRows
            val cellW = recycler.width / cols.toFloat()
            val col = (rvX / cellW).toInt().coerceIn(0, cols - 1)
            val row = (rvY / cellH).toInt().coerceIn(0, currentRows - 1)
            row * cols + col
        }
        if (targetPos == RecyclerView.NO_POSITION) { isDropInProgress = false; return false }

        val adapter = recycler.adapter as? HomeGridAdapter
        if (adapter == null) { isDropInProgress = false; return false }
        val currentList = adapter.currentList.toMutableList()
        if (targetPos !in currentList.indices) { isDropInProgress = false; return false }
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
                } finally { isDropInProgress = false }
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
                } finally { isDropInProgress = false }
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

    private fun clampSlotsToGrid(slots: List<HomeSlot>): List<HomeSlot> {
        if (slots.isEmpty()) return slots
        val out = mutableListOf<HomeSlot>()
        val occupied = mutableSetOf<Triple<Int, Int, Int>>()
        slots.forEach { s ->
            val p = s.page.coerceAtLeast(0)
            val r = s.row.coerceIn(0, currentRows - 1)
            val c = s.col.coerceIn(0, currentCols - 1)
            val key = Triple(p, r, c)
            if (!occupied.contains(key)) { occupied.add(key); out.add(HomeSlot(s.packageName, r, c, p)) }
        }
        slots.forEach { s ->
            if (out.none { it.packageName == s.packageName }) {
                var placed = false
                for (p in 0 until currentPageCount) {
                    for (r in 0 until currentRows) {
                        for (c in 0 until currentCols) {
                            val key = Triple(p, r, c)
                            if (!occupied.contains(key)) {
                                occupied.add(key); out.add(HomeSlot(s.packageName, r, c, p))
                                placed = true; break
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
            val maxPage = rawSlots.maxOfOrNull { it.page } ?: 0
            val effectivePageCount = maxOf(currentPageCount, maxPage + 1)
            if (effectivePageCount != currentPageCount) {
                currentPageCount = effectivePageCount
                Prefs.setPageCount(this@HomeActivity, currentPageCount)
                pageIndicator.setPageCount(currentPageCount)
            }
            val slots = clampSlotsToGrid(rawSlots)
            if (slots.size != rawSlots.size || slots != rawSlots) Prefs.setHomeSlots(this@HomeActivity, slots)

            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            HomeGridAdapter.AppCache.putAll(allApps)

            val pages = (0 until currentPageCount).map { page ->
                val minSlots = currentCols * currentRows
                val pageSlots = slots.filter { it.page == page }
                val maxIndex = pageSlots.maxOfOrNull { it.row * currentCols + it.col } ?: -1
                val totalSlots = maxOf(maxIndex + 1, minSlots)
                (0 until totalSlots).map { index ->
                    val r = index / currentCols
                    val c = index % currentCols
                    pageSlots.firstOrNull { it.row == r && it.col == c } ?: HomeSlot("", r, c, page)
                }
            }

            homePagerAdapter.setPages(pages)
            pageIndicator.setPageCount(currentPageCount)
            pageIndicator.setCurrentPage(homeViewPager.currentItem.coerceIn(0, currentPageCount - 1))

            if (!drawerOpen) {
                emptyMessage.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
            }

            homeViewPager.post {
                recalculateCellHeight()
                renderWidgets()
            }
        }
    }

    fun reloadHomeGrid() { loadHomeApps() }

    private fun persistHomeSlotsFromAdapter(adapter: HomeGridAdapter) {
        lifecycleScope.launch {
            val pageIndex = homeViewPager.currentItem
            val current = adapter.currentList.filter { it.packageName.isNotBlank() }.map { it.copy(page = pageIndex) }
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

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        homeViewPager.visibility = View.INVISIBLE
        pageIndicator.visibility = View.INVISIBLE
        dockLayout.visibility = View.INVISIBLE
        emptyMessage.visibility = View.INVISIBLE
        widgetOverlay.visibility = View.INVISIBLE
        resizeOverlay.visibility = View.GONE
        resizeFrame.visibility = View.GONE
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
        widgetOverlay.visibility = View.VISIBLE
        loadHomeApps()
    }

    private fun applyDrawerBackground() {
        lifecycleScope.launch {
            val color = Prefs.colorFlow(this@HomeActivity).first()
            val alpha = Prefs.alphaFlow(this@HomeActivity).first()
            currentDrawerColor = color
            drawerContainer.setCardBackgroundColor(
                Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (drawerOpen) closeDrawer()
        lifecycleScope.launch {
            currentCols = Prefs.colsFlow(this@HomeActivity).first()
            currentRows = Prefs.rowsFlow(this@HomeActivity).first()
            currentPageCount = Prefs.pageCountFlow(this@HomeActivity).first()
            currentDrawerColor = Prefs.colorFlow(this@HomeActivity).first()
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
