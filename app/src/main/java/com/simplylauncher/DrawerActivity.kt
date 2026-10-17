package com.simplylauncher

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DrawerActivity : AppCompatActivity() {

    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerContainer: CardView
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)

        window.statusBarColor = Color.parseColor("#80000000")
        window.navigationBarColor = Color.parseColor("#80000000")

        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)
        drawerContainer = findViewById(R.id.drawerContainer)

        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(
            onClick = { app ->
                AppRepository.launchApp(this, app.packageName)
                finish()
            },
            onLongPress = { app, view -> showContextMenu(app, view) }
        )
        drawerRecyclerView.adapter = adapter

        loadDrawerApps()
    }

    override fun onResume() {
        super.onResume()
        applyBackgroundColor()
    }

    private fun applyBackgroundColor() {
        lifecycleScope.launch {
            val color = Prefs.colorFlow(this@DrawerActivity).first()
            val alpha = Prefs.alphaFlow(this@DrawerActivity).first()

            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val a = (alpha * 255).toInt()

            drawerContainer.setCardBackgroundColor(Color.argb(a, r, g, b))
        }
    }

    private fun loadDrawerApps() {
        lifecycleScope.launch {
            val apps = AppRepository.loadInstalledAppsWithCustomLabels(this@DrawerActivity)
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

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_down)
    }
}
