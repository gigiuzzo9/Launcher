package com.simplylauncher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun LauncherRoot(
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            onOpenDrawer = { onDrawerOpenChange(true) },
            onOpenSettings = onOpenSettings
        )

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

    val allApps = remember { AppRepository.loadInstalledApps(context) }
    val homeApps = remember(homeItems, allApps) {
        homeItems.mapNotNull { pkg -> allApps.find { it.packageName == pkg } }
    }

    val screenHeight = config.screenHeightDp.dp
    val screenWidth = config.screenWidthDp.dp
    val cellWidth = screenWidth / cols
    val cellHeight = (screenHeight / rows) * 0.8f

    Box(Modifier.fillMaxSize()) {

        // GRIGLIA APP DELLA HOME (parte alta, lascia spazio in basso per il dock)
        if (homeApps.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp, bottom = 120.dp)
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
                    .padding(top = 60.dp, bottom = 120.dp)
            ) {
                items(homeApps, key = { it.packageName }) { app ->
                    AppIcon(
                        app = app,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        onClick = { AppRepository.launchApp(context, app.packageName) },
                        onLongPress = { },
                        onDragStart = { },
                        onDrag = { _, _ -> },
                        onDragEnd = { }
                    )
                }
            }
        }

        // DOCK IN BASSO — 5 slot: 2 app + cassetto + 2 app
        Dock(
            onOpenDrawer = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun Dock(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 5 slot. Lo slot centrale (indice 2) è il pulsante cassetto.
    // Gli altri 4 sono vuoti per ora (li riempiremo col menu "Aggiungi al dock").
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockSlotEmpty()
        DockSlotEmpty()
        DockDrawerButton(onOpenDrawer)
        DockSlotEmpty()
        DockSlotEmpty()
    }
}

@Composable
private fun DockSlotEmpty() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(Color.White.copy(alpha = 0.08f), shape = androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
private fun DockDrawerButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_drawer),
            contentDescription = "Cassetto",
            modifier = Modifier.size(32.dp)
        )
    }
}
