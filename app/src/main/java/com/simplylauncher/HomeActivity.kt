package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {

    private val drawerOpen = mutableStateOf(false)
    private val reloadTick = mutableStateOf(0)

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadTick.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                reloadTick.value++
            }
        }

        askLauncherIfNeeded()

        registerReceiver(
            reloadReceiver,
            IntentFilter("com.simplylauncher.RELOAD"),
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            reloadTick.value
            LauncherRoot(
                drawerOpen = drawerOpen.value,
                onDrawerOpenChange = { drawerOpen.value = it },
                onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
                onStartDrag = { app, position ->
                    DragDropState.startDrag(app, position)
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            drawerOpen.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        drawerOpen.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(reloadReceiver) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen.value) {
            drawerOpen.value = false
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
