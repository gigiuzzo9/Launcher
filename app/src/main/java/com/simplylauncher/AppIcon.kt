package com.simplylauncher

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(
    app: AppInfo,
    cellWidth: Dp,
    cellHeight: Dp,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    textColor: Color = Color.White
) {
    val iconSize = cellWidth * 0.6f
    val iconSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { iconSize.roundToPx() }

    // Cache del bitmap, calcolato una sola volta per app + dimensione
    val bmp = remember(app.packageName, iconSizePx) {
        app.icon.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(cellWidth, cellHeight)
            .pointerInput(app.packageName) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(app.packageName) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, _ ->
                        change.consume()
                        onDrag(change.position.x, change.position.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
    ) {
        Image(
            bitmap = bmp,
            contentDescription = app.label,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            color = textColor,
            fontSize = (cellWidth.value * 0.11f).sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
