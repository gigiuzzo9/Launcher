package com.simplylauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun DrawerScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartDrag: (AppInfo, Offset) -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    val color by Prefs.colorFlow(context).collectAsState(initial = 0xFF000000.toInt())
    val alpha by Prefs.alphaFlow(context).collectAsState(initial = 0.9f)
    val cols by Prefs.colsFlow(context).collectAsState(initial = 4)
    val rows by Prefs.rowsFlow(context).collectAsState(initial = 6)
    val homeItems by Prefs.homeItemsFlow(context).collectAsState(initial = emptyList())
    val dockItems by Prefs.dockItemsFlow(context).collectAsState(initial = emptyList())

    val apps = remember { AppRepository.loadInstalledApps(context) }

    val screenHeight = config.screenHeightDp.dp
    val screenWidth = config.screenWidthDp.dp
    val cellWidth = screenWidth / cols
    val cellHeight = (screenHeight / rows) * 0.85f

    val isDarkBackground = isColorDark(Color(color))
    val textColor = if (isDarkBackground) Color.White else Color.Black

    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f)
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(color).copy(alpha = alpha))
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Box(
                        modifier = Modifier
                            .size(cellWidth, cellHeight)
                            .pointerInput(app.packageName) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val longPress = awaitLongPressOrCancellation(down.id)

                                    if (longPress != null) {
                                        // Long press rilevato: aspetta se muove o resta fermo
                                        var isDrag = false
                                        var dragPos = longPress.position

                                        // Aspetta 300ms per vedere se muove
                                        val moveEvent = withTimeoutOrNull(300L) {
                                            awaitPointerEvent()
                                        }

                                        if (moveEvent != null) {
                                            val change = moveEvent.changes.firstOrNull { it.id == down.id }
                                            if (change != null && (change.position - down.position).getDistance() > 15f) {
                                                isDrag = true
                                                dragPos = change.position
                                                change.consume()
                                            }
                                        }

                                        if (isDrag) {
                                            // DRAG: chiudi cassetto, inizia drag
                                            menuApp = null
                                            onStartDrag(app, dragPos)
                                            onClose()
                                        } else {
                                            // MENU: mostra menu contestuale
                                            menuApp = app
                                        }
                                    } else {
                                        // TAP normale: apri app
                                        AppRepository.launchApp(context, app.packageName)
                                        onClose()
                                    }
                                }
                            }
                    ) {
                        AppIcon(
                            app = app,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            textColor = textColor
                        )
                    }
                }
            }
        }

        menuApp?.let { app ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                AppContextMenu(
                    expanded = true,
                    onDismiss = { menuApp = null },
                    app = app,
                    isInHome = homeItems.contains(app.packageName),
                    isInDock = dockItems.contains(app.packageName),
                    onRemoveFromHome = {
                        GlobalScope.launch {
                            Prefs.setHomeItems(context, homeItems - app.packageName)
                        }
                    },
                    onAddToDock = { },
                    onRemoveFromDock = {
                        GlobalScope.launch {
                            Prefs.setDockItems(context, dockItems - app.packageName)
                        }
                    },
                    onRename = { },
                    context = context
                )
            }
        }
    }
}

fun isColorDark(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance < 0.5
}
