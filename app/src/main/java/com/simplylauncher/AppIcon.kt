package com.simplylauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppIcon(
    app: AppInfo,
    cellWidth: Dp,
    cellHeight: Dp,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    textColor: Color = Color.White
) {
    val iconSize = cellWidth * 0.6f

    val bmp = remember(app.packageName) {
        AppIconCache.get(app.packageName, app)
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
