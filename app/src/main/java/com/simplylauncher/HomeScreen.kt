package com.simplylauncher 

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LauncherRoot(
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        if (!drawerOpen) {
            HomeScreen(
                onOpenDrawer = { onDrawerOpenChange(true) },
                onOpenSettings = onOpenSettings
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            DrawerScreen(
                onClose = { onDrawerOpenChange(false) },
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    val cols by Prefs.colsFlow(context).collectAsState(initial = 4)
    val rows by Prefs.rowsFlow(context).collectAsState(initial = 6)
    val homeItems by Prefs.homeItemsFlow(context).collectAsState(initial = emptyList())
    val dockItems by Prefs.dockItemsFlow(context).collectAsState(initial = emptyList())

    val allApps = remember { AppRepository.loadInstalledApps(context) }
    val homeApps = remember(homeItems, allApps) {
        homeItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }
    val dockApps = remember(dockItems, allApps) {
        dockItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }

    val screenHeight = config.screenHeightDp.dp
    val screenWidth = config.screenWidthDp.dp
    val cellWidth = screenWidth / cols
    val cellHeight = (screenHeight / rows) * 0.8f

    Box(Modifier.fillMaxSize()) {

        if (homeApps.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 130.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Apri il cassetto e aggiungi le tue app",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 130.dp)
            ) {
                items(homeApps, key = { it.packageName }) { app ->
                    AppIcon(
                        app = app,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        onClick = { AppRepository.launchApp(context, app.packageName) },
                        onLongPress = { }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 53.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockSlot(dockApps.getOrNull(0), Modifier.weight(1f))
            DockSlot(dockApps.getOrNull(1), Modifier.weight(1f))
            DockDrawerButton(onOpenDrawer, Modifier.weight(1f))
            DockSlot(dockApps.getOrNull(2), Modifier.weight(1f))
            DockSlot(dockApps.getOrNull(3), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockSlot(app: AppInfo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier.height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        if (app != null) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .pointerInput(app.packageName) {
                        detectTapGestures(
                            onTap = { AppRepository.launchApp(context, app.packageName) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(app.packageName) {
                    AppIconCache.get(app.packageName, app)
                }
                Image(
                    bitmap = bmp,
                    contentDescription = app.label,
                    modifier = Modifier.size(60.dp)
                )
            }
        }
    }
}

@Composable
private fun DockDrawerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(80.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_drawer),
            contentDescription = "Cassetto",
            modifier = Modifier.size(65.dp)
        )
    }
}
