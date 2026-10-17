package com.simplylauncher

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DrawerActivity : AppCompatActivity() {

    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerContainer: View
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawer)

        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)
        drawerContainer = findViewById(R.id.drawerContainer)

        // Setup RecyclerView
        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(
            onClick = { app ->
                AppRepository.launchApp(this, app.packageName)
                finish()
            },
            onLongPress = { app ->
                showContextMenu(app)
            }
        )
        drawerRecyclerView.adapter = adapter

        loadDrawerApps()
    }

    private fun loadDrawerApps() {
        lifecycleScope.launch {
            val apps = AppRepository.loadInstalledApps(this@DrawerActivity)
            adapter.submitList(apps)
        }
    }

    private fun showContextMenu(app: AppInfo) {
        // TODO: implementare menu contestuale
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }
}
