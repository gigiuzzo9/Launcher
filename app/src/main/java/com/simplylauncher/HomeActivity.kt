package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.DragEvent
import android.view.Gravity
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
            onLongPress = { app, view -> showContextMenu(app, view, showRemove = true) },
            onStartDrag = { holder -> startDrag(holder) }
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
            onLongPress = { app, view -> showContextMenu(app, view, showRemove = false) },
            onStartDrag = { holder -> startDrag(holder) }
        )
        drawerRecyclerView.adapter = drawerAdapter
        drawerRecyclerView.setOnDragListener { _, event -> handleDrop(event, isDock = false, isHomeGrid = false) }

        dockLayout.setOnDragListener { _, event -> handleDrop(event, isDock = true, isHomeGrid = false) }

        drawerOverlay.setOnClickListener { closeDrawer() }

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

    private fun startDrag(holder: RecyclerView.ViewHolder) {
        val app = adapter.currentList.getOrNull(holder.adapterPosition) ?: return
        val clip = ClipData.newPlainText("package", app.packageName)
        val shadow = View.DragShadowBuilder(holder.itemView)
        holder.itemView.startDragAndDrop(clip, shadow, null, 0)

        if (drawerOpen) {
            drawerOverlay.animate().alpha(0.15f).setDuration(300).start()
        }
    }

    private fun handleDrop(event: DragEvent, isDock: Boolean, isHomeGrid: Boolean): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
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
                if (isDock) addToDock(pkg)
                else if (isHomeGrid) {
                    val cols = getCurrentCols()
                    val cellWidth = homeRecyclerView.width / cols.toFloat()
                    val cellHeight = homeRecyclerView.height / 6f
                    val col = (event.x / cellWidth).toInt().coerceIn(0, cols - 1)
                    val row = (event.y / cellHeight).toInt().coerceIn(0, 5)
                    addToHomeAt(pkg, row * cols + col)
                } else addToHome(pkg)
                drawerOverlay.animate().alpha(1f).setDuration(200).start()
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                if (isDock) dockLayout.setBackgroundColor(Color.TRANSPARENT)
                drawerOverlay.animate().alpha(1f).setDuration(200).start()
                return true
            }
        }
        return false
    }

    private fun getCurrentCols(): Int = (homeRecyclerView.layoutManager as? GridLayoutManager)?.spanCount ?: 4

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
            homeItems.add(index.coerceIn(0, homeItems.size), pkg)
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
                RenameDialog.show(this, app) { loadHomeApps(); loadDockApps(); loadDrawerApps() }
            }
        }
        contextMenuView.onRemove = {
            currentApp?.let { app ->
                lifecycleScope.launch {
                    val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
                    val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()
                    Prefs.setHomeItems(this@HomeActivity, homeItems - app.packageName)
                    Prefs.setDockItems(this@HomeActivity, dockItems - app.packageName)
                    loadHomeApps(); loadDockApps(); loadDrawerApps()
                }
            }
        }
        contextMenuView.onDismiss = { currentApp = null }
    }

    private fun showContextMenu(app: AppInfo, anchor: View, showRemove: Boolean) {
        currentApp = app
        contextMenuView.show(anchor, showRemove)
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
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        }
        dockDrawerButton.addView(imageView)
        dockDrawerButton.setOnClickListener { openDrawer() }
    }

    override fun onResume() {
        super.onResume()
        if (drawerOpen) closeDrawer()
        applyGridSettings()
        loadHomeApps(); loadDockApps(); loadDrawerApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
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
            drawerContainer.setCardBackgroundColor(Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color)))
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
            drawerAdapter.submitList(AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity))
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
                        layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
                    }
                    slot.addView(imageView)
                    slot.setOnClickListener { AppRepository.launchApp(this@HomeActivity, app.packageName) }
                    slot.setOnLongClickListener { view -> showContextMenu(app, view, showRemove = true); true }
                }
            }
        }
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
