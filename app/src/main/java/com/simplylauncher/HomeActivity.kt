package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var homeRecyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var drawerIcon: ImageView
    private lateinit var adapter: AppAdapter
    private lateinit var dockSlots: List<FrameLayout>

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadHomeApps()
            loadDockApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        homeRecyclerView = findViewById(R.id.homeRecyclerView)
        emptyMessage = findViewById(R.id.emptyMessage)
        drawerIcon = findViewById(R.id.drawerIcon)

        dockSlots = listOf(
            findViewById(R.id.dockSlot1),
            findViewById(R.id.dockSlot2),
            findViewById(R.id.dockSlot3),
            findViewById(R.id.dockSlot4)
        )

        homeRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(
            onClick = { app -> AppRepository.launchApp(this, app.packageName) },
            onLongPress = { app, view -> showContextMenu(app, view) }
        )
        homeRecyclerView.adapter = adapter

        drawerIcon.setOnClickListener {
            startActivity(Intent(this, DrawerActivity::class.java))
        }

        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                loadHomeApps()
                loadDockApps()
            }
        }

        askLauncherIfNeeded()

        registerReceiver(
            reloadReceiver,
            IntentFilter("com.simplylauncher.RELOAD"),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        loadHomeApps()
        loadDockApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    private fun loadHomeApps() {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(this@HomeActivity)
            val homeApps = homeItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }

            adapter.submitList(homeApps)

            if (homeApps.isEmpty()) {
                emptyMessage.visibility = TextView.VISIBLE
                homeRecyclerView.visibility = RecyclerView.GONE
            } else {
                emptyMessage.visibility = TextView.GONE
                homeRecyclerView.visibility = RecyclerView.VISIBLE
            }
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
                        val size = (65 * resources.displayMetrics.density).toInt()
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

    private fun showContextMenu(app: AppInfo, anchor: View) {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
            val dockItems = Prefs.dockItemsFlow(this@HomeActivity).first()

            AppContextMenu.show(
                context = this@HomeActivity,
                anchor = anchor,
                app = app,
                isInHome = homeItems.contains(app.packageName),
                isInDock = dockItems.contains(app.packageName),
                onRemove = {
                    lifecycleScope.launch {
                        Prefs.setHomeItems(this@HomeActivity, homeItems - app.packageName)
                        Prefs.setDockItems(this@HomeActivity, dockItems - app.packageName)
                        loadHomeApps()
                        loadDockApps()
                    }
                },
                onRename = {
                    RenameDialog.show(this@HomeActivity, app) {
                        loadHomeApps()
                        loadDockApps()
                    }
                }
            )
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
