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
        // Se arriva un intent HOME (tasto Home premuto), chiudi il cassetto
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            drawerOpen.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Quando l'activity torna in primo piano (es. da app recenti), chiudi il cassetto
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
