package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
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

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadHomeApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        homeRecyclerView = findViewById(R.id.homeRecyclerView)
        emptyMessage = findViewById(R.id.emptyMessage)
        drawerIcon = findViewById(R.id.drawerIcon)

        // Setup RecyclerView
        homeRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = AppAdapter(
            onClick = { app -> AppRepository.launchApp(this, app.packageName) },
            onLongPress = { app -> showContextMenu(app) }
        )
        homeRecyclerView.adapter = adapter

        // Click sul pulsante cassetto
        drawerIcon.setOnClickListener {
            startActivity(Intent(this, DrawerActivity::class.java))
        }

        // Prima installazione: popola home e dock
        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                loadHomeApps()
            }
        }

        // Chiedi di impostare come launcher (solo prima volta)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    private fun loadHomeApps() {
        lifecycleScope.launch {
            val homeItems = Prefs.homeItemsFlow(this@HomeActivity).first()
            val allApps = AppRepository.loadInstalledApps(this@HomeActivity)
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

    private fun showContextMenu(app: AppInfo) {
        // TODO: implementare menu contestuale
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
