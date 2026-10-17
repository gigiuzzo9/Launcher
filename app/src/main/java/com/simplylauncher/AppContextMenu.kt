package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
    onRemoveFromHome: () -> Unit,
    onAddToDock: () -> Unit,
    onRemoveFromDock: () -> Unit,
    onRename: () -> Unit,
    context: Context
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // Disinstalla (sempre presente)
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

        // Rinomina (solo nel cassetto — quando non è nella home)
        if (!isInHome) {
            DropdownMenuItem(
                text = { Text("Rinomina") },
                onClick = {
                    onRename()
                    onDismiss()
                }
            )
        }

        // Rimuovi dalla home (solo se è nella home)
        if (isInHome) {
            DropdownMenuItem(
                text = { Text("Rimuovi dalla home") },
                onClick = {
                    onRemoveFromHome()
                    onDismiss()
                }
            )
        }

        // Dock
        if (isInDock) {
            DropdownMenuItem(
                text = { Text("Rimuovi dal dock") },
                onClick = {
                    onRemoveFromDock()
                    onDismiss()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Aggiungi al dock") },
                onClick = {
                    onAddToDock()
                    onDismiss()
                }
            )
        }
    }
}
