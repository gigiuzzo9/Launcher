package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.ClipData
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
    }

    // ─────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────

    private fun setupDragController() {
        dragController = DragController(
            activity = this,
            rootView = findViewById(android.R.id.content),
            homeRecyclerView = homeRecyclerView,
            drawerOverlay = drawerOverlay,
            onDropOnHome = { pkg, x, y -> handleDropOnHome(pkg, x, y) },
            onDropOnDrawer = { pkg -> handleDropOnDrawer(pkg) },
            onLongPressWithoutMove = { app, anchor, fromDrawer ->
                showContextMenu(app, anchor, showRemove = !fromDrawer, fromDrawer = fromDrawer)
            }
        )

        // collega il touch listener al root per intercettare i move durante il pending
        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            dragController.onTouchEvent(event)
            false
        }

        // listener drop sulla home
        homeRecyclerView.setOnDragListener { _, event ->
            dragController.handleHomeDragEvent(event)
        }

        // listener drop sul drawer (rimuove dalla home)
        drawerRecyclerView.setOnDragListener { _, event ->
            dragController.handleDrawerDragEvent(event)
        }

        // anche l'overlay deve accettare il drop per rimuovere
        drawerOverlay.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) {
                dragController.handleDrawerDragEvent(event)
            } else {
                false
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
                if (swapped) {
                    persistHomeSlots()
                }
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

        contextMenuView.onMove = {
            // non usato: il drag parte direttamente
            contextMenuView.dismiss()
        }

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
                    Prefs.setDockItems(this@HomeActivity, dockItems - app.packageName)
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

    private fun showContextMenu(app: AppInfo, anchor: View, showRemove: Boolean, fromDrawer: Boolean) {
        currentApp = app
        currentAnchor = anchor
        currentFromDrawer = fromDrawer
        contextMenuView.show(anchor, showRemove)
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
    // DROP
    // ─────────────────────────────────────────────────────────────

    private fun handleDropOnHome(pkg: String, x: Float, y: Float): Boolean {
        val lm = homeRecyclerView.layoutManager as? GridLayoutManager ?: return false
        val cols = lm.spanCount

        // trova item sotto il dito
        val child = homeRecyclerView.findChildViewUnder(x, y)
        val targetPos = if (child != null) {
            homeRecyclerView.getChildAdapterPosition(child)
        } else {
            // calcola cella in base alla posizione
            val cellW = homeRecyclerView.width / cols.toFloat()
            val cellH = cellW
            val col = (x / cellW).toInt().coerceIn(0, cols - 1)
            val row = (y / cellH).toInt()
            row * cols + col
        }

        lifecycleScope.launch {
            val slots = Prefs.homeSlotsFlow(this@HomeActivity).first().toMutableList()
            // rimuovi il pkg esistente se c'è
            slots.removeAll { it.packageName == pkg }

            // posizione target
            val row = targetPos / cols
            val col = targetPos % cols

            // se c'è già un item in quella posizione, swap
            val occupant = slots.firstOrNull { it.row == row && it.col == col }
            if (occupant != null) {
                // l'occupante va dove eravamo
                val original = Prefs.homeSlotsFlow(this@HomeActivity).first()
                    .firstOrNull { it.packageName == pkg }
                if (original != null) {
                    slots.removeAll { it.packageName == occupant.packageName }
                    slots.add(HomeSlot(occupant.packageName, original.row, original.col))
                }
            }

            slots.removeAll { it.row == row && it.col == col }
            slots.add(HomeSlot(pkg, row, col))
            Prefs.setHomeSlots(this@HomeActivity, slots)

            // se il pkg era nella dock, rimuovilo
            val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
            if (dockItems.contains(pkg)) {
                Prefs.setDockItems(this@HomeActivity, dockItems - pkg)
                dockController.refresh()
            }

            loadHomeApps()
        }
        return true
    }

    private fun handleDropOnDrawer(pkg: String) {
        lifecycleScope.launch {
            val slots = Prefs.homeSlotsFlow(this@HomeActivity).first()
            Prefs.setHomeSlots(this@HomeActivity, slots.filter { it.packageName != pkg })
            val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
            Prefs.setDockItems(this@HomeActivity, dockItems - pkg)
            loadHomeApps()
            dockController.refresh()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────

    private fun applyGridSettings(cols: Int) {
        homeRecyclerView.layoutManager = GridLayoutManager(this, cols)
    }

    private fun loadHomeApps() {
        lifecycleScope.launch {
            val slots = Prefs.homeSlotsFlow(this@HomeActivity).first()
            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            HomeGridAdapter.AppCache.putAll(allApps)

            // crea la lista con TUTTI gli slot fino alla posizione massima
            val maxIndex = slots.maxOfOrNull { it.row * currentCols + it.col } ?: -1
            // assicurati che la griglia abbia almeno N slot per riempire lo schermo
            val minSlots = currentCols * currentRows
            val totalSlots = maxOf(maxIndex + 1, minSlots)

            val fullList = (0 until totalSlots).map { index ->
                val row = index / currentCols
                val col = index % currentCols
                slots.firstOrNull { it.row == row && it.col == col }
                    ?: HomeSlot("", row, col) // slot vuoto
            }
            homeAdapter.submitList(fullList)

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
            val current = homeAdapter.currentList
                .filter { it.packageName.isNotBlank() }
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
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
