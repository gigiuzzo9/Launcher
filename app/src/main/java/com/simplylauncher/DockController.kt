package com.simplylauncher

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Gestisce i 4 slot della dock + il pulsante drawer al centro.
 * Ogni slot è un target di drop indipendente.
 */
class DockController(
    private val activity: HomeActivity,
    private val slots: List<FrameLayout>,
    private val onAppLongPressed: (AppInfo, View) -> Unit
) {
    private val highlightColor = Color.parseColor("#33FFFFFF")

    fun setup() {
        slots.forEachIndexed { index, slot ->
            // Listener drop
            slot.setOnDragListener { _, event -> handleSlotDrop(event, index) }
        }
    }

    fun refresh() {
        activity.lifecycleScope.launch {
            val dockItems = Prefs.dockItemsFlow(activity).first()
            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(activity)
            val byPkg = allApps.associateBy { it.packageName }

            slots.forEachIndexed { index, slot ->
                slot.removeAllViews()
                val pkg = dockItems.getOrNull(index)
                val app = pkg?.let { byPkg[it] }
                if (app != null) {
                    val imageView = ImageView(activity).apply {
                        setImageDrawable(app.icon)
                        val size = (62 * resources.displayMetrics.density).toInt()
                        layoutParams = FrameLayout.LayoutParams(size, size).apply {
                            gravity = Gravity.CENTER
                        }
                    }
                    slot.addView(imageView)
                    slot.setOnClickListener {
                        AppRepository.launchApp(activity, app.packageName)
                    }
                    slot.setOnLongClickListener { view ->
                        onAppLongPressed(app, view)
                        true
                    }
                } else {
                    slot.setOnClickListener(null)
                    slot.setOnLongClickListener(null)
                }
            }
        }
    }

    private fun handleSlotDrop(event: DragEvent, slotIndex: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                slots[slotIndex].setBackgroundColor(highlightColor)
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                slots[slotIndex].setBackgroundColor(Color.TRANSPARENT)
                return true
            }

            DragEvent.ACTION_DROP -> {
                slots[slotIndex].setBackgroundColor(Color.TRANSPARENT)
                val pkg = event.clipData.getItemAt(0).text.toString()
                val source = event.clipData.description.label?.toString() ?: "UNKNOWN"
                activity.lifecycleScope.launch {
                    if (source == "DOCK") {
                        // riordino dock: scambia slot
                        val dockItems = Prefs.dockItemsFlow(activity).first().toMutableList()
                        val fromIndex = dockItems.indexOf(pkg)
                        if (fromIndex != -1 && fromIndex != slotIndex) {
                            // swap
                            val a = dockItems.getOrNull(fromIndex)
                            val b = dockItems.getOrNull(slotIndex)
                            if (a != null && b != null) {
                                dockItems[fromIndex] = b
                                dockItems[slotIndex] = a
                            } else if (a != null) {
                                dockItems.removeAt(fromIndex)
                                while (dockItems.size < slotIndex) dockItems.add("")
                                if (dockItems.size == slotIndex) dockItems.add(a)
                                else dockItems[slotIndex] = a
                            }
                            Prefs.setDockItems(activity, dockItems.filter { it.isNotBlank() })
                            refresh()
                        }
                    } else {
                        // da HOME o DRAWER: aggiungi allo slot
                        addToDockAt(pkg, slotIndex)
                    }
                }
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                slots[slotIndex].setBackgroundColor(Color.TRANSPARENT)
                return true
            }
        }
        return false
    }

    private suspend fun addToDockAt(pkg: String, slotIndex: Int) {
        val dockItems = Prefs.dockItemsFlow(activity).first().toMutableList()

        // rimuovi se già presente (evita duplicati)
        val existingIndex = dockItems.indexOf(pkg)
        if (existingIndex != -1) dockItems.removeAt(existingIndex)

        // riempi fino a slotIndex con placeholder vuoti
        while (dockItems.size < slotIndex) dockItems.add("")

        if (dockItems.size == slotIndex) {
            dockItems.add(pkg)
        } else {
            // slot occupato: swap (l'occupante va in fondo)
            val previous = dockItems[slotIndex]
            dockItems[slotIndex] = pkg
            if (previous.isNotBlank()) {
                dockItems.add(previous)
            }
        }

        Prefs.setDockItems(activity, dockItems.filter { it.isNotBlank() })

        // se veniva dalla home, rimuovi dalla home
        val homeSlots = Prefs.homeSlotsFlow(activity).first()
        val filtered = homeSlots.filter { it.packageName != pkg }
        if (filtered.size != homeSlots.size) {
            Prefs.setHomeSlots(activity, filtered)
            activity.reloadHomeGrid()
        }

        refresh()
    }
}
