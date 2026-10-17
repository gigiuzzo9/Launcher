package com.simplylauncher

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DrawerActivity : AppCompatActivity() {

    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var currentApps: List<AppInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)

        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)

        // Setup RecyclerView
        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(
            onClick = { app ->
                AppRepository.launchApp(this, app.packageName)
                finish()
            },
            onLongPress = { app, view ->
                showContextMenu(app, view)
            }
        )
        drawerRecyclerView.adapter = adapter

        loadDrawerApps()
    }

    private fun loadDrawerApps() {
        lifecycleScope.launch {
            val apps = AppRepository.loadInstalledAppsWithCustomLabels(this@DrawerActivity)
            currentApps = apps
            adapter.submitList(apps)
        }
    }

    private fun showContextMenu(app: AppInfo, anchor: View) {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@DrawerActivity).first()
            val dockItems = Prefs.dockItemsFlow(this@DrawerActivity).first()

            AppContextMenu.show(
                context = this@DrawerActivity,
                anchor = anchor,
                app = app,
                isInHome = homeItems.contains(app.packageName),
                isInDock = dockItems.contains(app.packageName),
                onRemove = {
                    lifecycleScope.launch {
                        Prefs.setHomeItems(this@DrawerActivity, homeItems - app.packageName)
                        Prefs.setDockItems(this@DrawerActivity, dockItems - app.packageName)
                        loadDrawerApps()
                    }
                },
                onRename = {
                    RenameDialog.show(this@DrawerActivity, app) {
                        loadDrawerApps()
                    }
                }
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}
