package com.simplylauncher

import android.content.ClipData
import android.content.ClipDescription
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
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var homeRecyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var dockDrawerButton: FrameLayout
    private lateinit var dockLayout: View
    private lateinit var adapter: AppAdapter
    private lateinit var dockSlots: List<FrameLayout>

    private lateinit var drawerOverlay: FrameLayout
    private lateinit var drawerContainer: CardView
    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerAdapter: AppAdapter
    private var drawerOpen = false

    private lateinit var contextMenuView: AppContextMenuView
    private var currentApp: AppInfo? = null
    private var currentAnchor: View? = null

    private var popupOpened = false
    private var popupStartX = 0f
    private var popupStartY = 0f

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadHomeApps()
            loadDockApps()
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

        setupDrawerButton()
        setupContextMenu()
        applyGridSettings()

        adapter = AppAdapter(
            onClick = { app -> AppRepository.launchApp(this, app.packageName) },
            onLongPress = { app, view, x, y -> showContextMenu(app, view) },
            onMoveAfterLongPress = { app, view -> startDrag(app, view) }
        )
        homeRecyclerView.adapter = adapter

        homeRecyclerView.setOnDragListener { _, event -> handleDrop(event, isDock = false, isHomeGrid = true) }

        drawerOverlay = findViewById(R.id.drawerOverlay)
        drawerContainer = findViewById(R.id.drawerContainer)
        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)

        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        drawerRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val spacing = (12 * resources.displayMetrics.density).toInt()
                outRect.bottom = spacing
            }
        })
        drawerAdapter = AppAdapter(
            onClick = { app ->
                AppRepository.launchApp(this, app.packageName)
                closeDrawer()
            },
            onLongPress = { app, view, x, y -> showContextMenu(app, view) },
            onMoveAfterLongPress = { app, view -> startDrag(app, view) }
        )
        drawerRecyclerView.adapter = drawerAdapter

        drawerRecyclerView.setOnDragListener { _, event -> handleDrop(event, isDock = false, isHomeGrid = false) }

        dockLayout.setOnDragListener { _, event -> handleDrop(event, isDock = true, isHomeGrid = false) }

        drawerOverlay.setOnClickListener {
            closeDrawer()
        }

        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                loadHomeApps()
                loadDockApps()
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
        if (popupOpened) {
            when (ev.action) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(ev.rawX - popupStartX)
                    val dy = Math.abs(ev.rawY - popupStartY)
                    if (dx > 80 || dy > 80) {  // soglia 80px
                        popupOpened = false
                        contextMenuView.dismiss()
                        currentApp?.let { app ->
                            currentAnchor?.let { anchor ->
                                startDrag(app, anchor)
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    popupOpened = false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupContextMenu() {
        contextMenuView = AppContextMenuView(this)

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
                    loadDockApps()
                    loadDrawerApps()
                }
            }
        }

        contextMenuView.onRemove = {
            currentApp?.let { app ->
                lifecycleScope.launch {
                    val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
                    val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
                    Prefs.setHomeItems(this@HomeActivity, homeItems - app.packageName)
                    Prefs.setDockItems(this@HomeActivity, dockItems - app.packageName)
                    loadHomeApps()
                    loadDockApps()
                    loadDrawerApps()
                }
            }
        }

        contextMenuView.onDismiss = {
            if (!popupOpened) {
                currentApp = null
                currentAnchor = null
            }
        }
    }

    private fun showContextMenu(app: AppInfo, anchor: View) {
        currentApp = app
        currentAnchor = anchor
        popupOpened = true
        popupStartX = anchor.x
        popupStartY = anchor.y
        contextMenuView.show(anchor)
    }

    private fun startDrag(app: AppInfo, view: View) {
        if (contextMenuView.isShowing()) {
            contextMenuView.dismiss()
        }

        val clip = ClipData.newPlainText("package", app.packageName)
        val shadow = View.DragShadowBuilder(view)
        view.startDragAndDrop(clip, shadow, null, 0)

        if (drawerOpen) {
            drawerOverlay.animate().alpha(0.15f).setDuration(300).start()
        }
    }

    private fun handleDrop(event: DragEvent, isDock: Boolean, isHomeGrid: Boolean): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                if (isDock) dockLayout.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                if (isDock) dockLayout.setBackgroundColor(Color.TRANSPARENT)
                return true
            }
            DragEvent.ACTION_DROP -> {
                val pkg = event.clipData.getItemAt(0).text.toString()
                if (isDock) {
                    addToDock(pkg)
                } else if (isHomeGrid) {
                    val cols = getCurrentCols()
                    val cellWidth = homeRecyclerView.width / cols.toFloat()
                    val cellHeight = homeRecyclerView.height / 6f
                    val col = (event.x / cellWidth).toInt().coerceIn(0, cols - 1)
                    val row = (event.y / cellHeight).toInt().coerceIn(0, 5)
                    val index = row * cols + col
                    addToHomeAt(pkg, index)
                } else {
                    addToHome(pkg)
                }
                drawerOverlay.animate().alpha(1f).setDuration(200).start()
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                if (isDock) dockLayout.setBackgroundColor(Color.TRANSPARENT)
                if (drawerOpen && !event.result) {
                    closeDrawer()
                }
                drawerOverlay.animate().alpha(1f).setDuration(200).start()
                return true
            }
        }
        return false
    }

    private fun getCurrentCols(): Int {
        val lm = homeRecyclerView.layoutManager as? GridLayoutManager
        return lm?.spanCount ?: 4
    }

    private fun addToHome(pkg: String) {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
            if (!homeItems.contains(pkg)) {
                Prefs.setHomeItems(this@HomeActivity, homeItems + pkg)
                loadHomeApps()
            }
        }
    }

    private fun addToHomeAt(pkg: String, index: Int) {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first().toMutableList()
            homeItems.remove(pkg)
            val targetIndex = index.coerceIn(0, homeItems.size)
            homeItems.add(targetIndex, pkg)
            Prefs.setHomeItems(this@HomeActivity, homeItems)
            loadHomeApps()
        }
    }

    private fun addToDock(pkg: String) {
        lifecycleScope.launch {
            val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
            if (!dockItems.contains(pkg) && dockItems.size < 4) {
                Prefs.setDockItems(this@HomeActivity, dockItems + pkg)
                loadDockApps()
            }
        }
    }

    private fun applyGridSettings() {
        lifecycleScope.launch {
            val cols = Prefs.colsFlow(this@HomeActivity).first()
            homeRecyclerView.layoutManager = GridLayoutManager(this@HomeActivity, cols)
        }
    }

    private fun setupDrawerButton() {
        dockDrawerButton.removeAllViews()

        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_drawer)
            val size = (62 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }
        dockDrawerButton.addView(imageView)

        dockDrawerButton.setOnClickListener {
            openDrawer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (drawerOpen) {
            closeDrawer()
        }
        applyGridSettings()
        loadHomeApps()
        loadDockApps()
        loadDrawerApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) {
            closeDrawer()
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true

        homeRecyclerView.visibility = View.INVISIBLE
        dockLayout.visibility = View.INVISIBLE
        emptyMessage.visibility = View.INVISIBLE

        applyDrawerBackground()
        drawerOverlay.alpha = 1f
        drawerOverlay.visibility = View.VISIBLE
        drawerOverlay.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.slide_in_up)
        )
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

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val a = (alpha * 255).toInt()

            drawerContainer.setCardBackgroundColor(Color.argb(a, r, g, b))
        }
    }

    private fun loadHomeApps() {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            val homeApps = homeItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }

            adapter.submitList(homeApps)

            if (!drawerOpen) {
                if (homeApps.isEmpty()) {
                    emptyMessage.visibility = TextView.VISIBLE
                    homeRecyclerView.visibility = RecyclerView.GONE
                } else {
                    emptyMessage.visibility = TextView.GONE
                    homeRecyclerView.visibility = RecyclerView.VISIBLE
                }
            }
        }
    }

    private fun loadDrawerApps() {
        lifecycleScope.launch {
            val apps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            drawerAdapter.submitList(apps)
        }
    }

    private fun loadDockApps() {
        lifecycleScope.launch {
            val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            val dockApps = dockItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }

            dockSlots.forEachIndexed { index, slot ->
                slot.removeAllViews()

                val app = dockApps.getOrNull(index)
                if (app != null) {
                    val imageView = ImageView(this@HomeActivity).apply {
                        setImageDrawable(app.icon)
                        val size = (62 * resources.displayMetrics.density).toInt()
                        layoutParams = FrameLayout.LayoutParams(size, size).apply {
                            gravity = Gravity.CENTER
                        }
                    }
                    slot.addView(imageView)

                    slot.setOnClickListener {
                        AppRepository.launchApp(this@HomeActivity, app.packageName)
                    }
                    slot.setOnLongClickListener { view ->
                        showContextMenu(app, view)
                        true
                    }
                }
            }
        }
    }

    private fun askLauncherIfNeeded() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(homeIntent, 0)
        val currentHome = res?.activityInfo?.packageName

        if (currentHome == packageName) return

        val prefs = getSharedPreferences("launcher_flags", MODE_PRIVATE)
        val alreadyAsked = prefs.getBoolean("asked_launcher", false)
        if (alreadyAsked) return

        prefs.edit().putBoolean("asked_launcher", true).apply()

        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Exception) { }
        }
    }
}
