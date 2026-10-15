package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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

        // Setup iniziale: popola home e dock la prima volta
        if (FirstRunSetup.isFirstRun(this)) {
            lifecycleScope.launch {
                FirstRunSetup.populateHome(this@HomeActivity)
                FirstRunSetup.markDone(this@HomeActivity)
                reloadTick.value++
            }
        }

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
}
