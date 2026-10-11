package com.simplylauncher

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf

class HomeActivity : ComponentActivity() {

    private val drawerOpen = mutableStateOf(false)
    private val reloadTick = mutableStateOf(0)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadTick.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        maybeAskLauncherRole()

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

    private fun maybeAskLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                // Già impostato come launcher, non fare nulla
                return
            }
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    val intent = rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    roleLauncher.launch(intent)
                    return
                } catch (e: Exception) {
                    // Fallisce, usa il fallback
                }
            }
        }
        // Fallback: apri direttamente la pagina delle impostazioni launcher
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // Niente, lasciamo stare
        }
    }
}
