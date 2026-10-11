package com.simplylauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val color by Prefs.colorFlow(context).collectAsState(initial = 0xFF000000.toInt())
    val alpha by Prefs.alphaFlow(context).collectAsState(initial = 0.9f)
    val cols by Prefs.colsFlow(context).collectAsState(initial = 4)
    val rows by Prefs.rowsFlow(context).collectAsState(initial = 6)

    val palette = listOf(
        0xFF000000.toInt(), // nero
        0xFF1E1E1E.toInt(), // grigio scuro
        0xFF2E3A59.toInt(), // blu scuro
        0xFF3A2E59.toInt(), // viola scuro
        0xFF593A2E.toInt(), // marrone
        0xFF2E593A.toInt(), // verde scuro
        0xFFFFFFFF.toInt(), // bianco
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {

        // Titolo pagina
        Text(
            "Impostazioni",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Divider()

        // COLORE SFONDO
        SettingsSection(title = "Colore sfondo") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { c ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { scope.launch { Prefs.setColor(context, c) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (c == color) {
                            Text(
                                "✓",
                                color = if (c == 0xFFFFFFFF.toInt()) Color.Black else Color.White,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        }

        Divider()

        // TRASPARENZA
        SettingsSection(title = "Trasparenza: ${(alpha * 100).toInt()}%") {
            Slider(
                value = alpha,
                onValueChange = { scope.launch { Prefs.setAlpha(context, it) } },
                valueRange = 0.3f..1f
            )
        }

        Divider()

        // COLONNE
        SettingsSection(title = "Colonne: $cols") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(3, 4).forEach { n ->
                    FilterChip(
                        selected = cols == n,
                        onClick = { scope.launch { Prefs.setCols(context, n) } },
                        label = { Text("$n") }
                    )
                }
            }
        }

        Divider()

        // RIGHE
        SettingsSection(title = "Righe: $rows") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(5, 6, 7).forEach { n ->
                    FilterChip(
                        selected = rows == n,
                        onClick = { scope.launch { Prefs.setRows(context, n) } },
                        label = { Text("$n") }
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
    )
}
