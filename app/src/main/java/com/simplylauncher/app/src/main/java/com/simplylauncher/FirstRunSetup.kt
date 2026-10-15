package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object FirstRunSetup {

    // Package name "noti" per le app comuni, proviamo in ordine
    private val dialerCandidates = listOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.oneplus.dialer",
        "com.oppo.dialer",
        "com.coloros.phonemanager"
    )
    private val messagesCandidates = listOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.oneplus.mms",
        "com.oppo.mms"
    )
    private val storeCandidates = listOf(
        "com.android.vending",
        "com.google.android.apps.store",
        "com.heytap.market",
        "com.oppo.market",
        "com.coloros.appstore"
    )
    private val filesCandidates = listOf(
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.coloros.filemanager",
        "com.oppo.filemanager"
    )
    private val cameraCandidates = listOf(
        "com.android.camera",
        "com.google.android.GoogleCamera",
        "com.oppo.camera",
        "com.coloros.camera",
        "com.oneplus.camera"
    )
    private val calculatorCandidates = listOf(
        "com.android.calculator2",
        "com.google.android.calculator",
        "com.coloros.calculator",
        "com.oppo.calculator"
    )
    private val clockCandidates = listOf(
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.coloros.alarmclock",
        "com.oppo.alarmclock"
    )
    private val settingsCandidates = listOf(
        "com.android.settings"
    )
    private val browserCandidates = listOf(
        "com.android.chrome",
        "com.heytap.browser",
        "com.oppo.browser",
        "com.coloros.browser"
    )

    fun isFirstRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences("launcher_flags", Context.MODE_PRIVATE)
        return !prefs.getBoolean("first_run_done", false)
    }

    fun markDone(context: Context) {
        val prefs = context.getSharedPreferences("launcher_flags", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("first_run_done", true).apply()
    }

    suspend fun populateHome(context: Context) {
        val homeDefaults = listOf(
            findFirstInstalled(context, calculatorCandidates),
            findFirstInstalled(context, clockCandidates),
            findFirstInstalled(context, cameraCandidates),
            findFirstInstalled(context, browserCandidates),
            findFirstInstalled(context, settingsCandidates)
        ).filterNotNull()

        val dockDefaults = listOf(
            findFirstInstalled(context, dialerCandidates),
            findFirstInstalled(context, messagesCandidates),
            findFirstInstalled(context, storeCandidates),
            findFirstInstalled(context, filesCandidates)
        ).filterNotNull()

        Prefs.setHomeItems(context, homeDefaults)
        Prefs.setDockItems(context, dockDefaults)
    }

    private fun findFirstInstalled(context: Context, candidates: List<String>): String? {
        for (pkg in candidates) {
            try {
                context.packageManager.getApplicationInfo(pkg, 0)
                return pkg
            } catch (e: PackageManager.NameNotFoundException) {
                // Continua col prossimo
            }
        }
        return null
    }
}
