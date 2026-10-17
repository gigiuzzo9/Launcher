package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AppContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    app: AppInfo,
    isInHome: Boolean,
    isInDock: Boolean,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    context: Context
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Disinstalla") },
            onClick = {
                val i = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${app.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Rinomina") },
            onClick = {
                onRename()
                onDismiss()
            }
        )

        if (isInHome || isInDock) {
            DropdownMenuItem(
                text = { Text("Rimuovi") },
                onClick = {
                    onRemove()
                    onDismiss()
                }
            )
        }
    }
}
