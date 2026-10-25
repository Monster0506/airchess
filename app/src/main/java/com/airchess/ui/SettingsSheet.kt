package com.airchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.airchess.model.GameSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: GameSettings,
    onChange: (GameSettings) -> Unit,
    onDismiss: () -> Unit,
    name: String = "",
    serverUrl: String = "",
    prefer3d: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onServerChange: (String) -> Unit = {},
    onPrefer3dChange: (Boolean) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(name, onNameChange, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(serverUrl, onServerChange, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SettingSwitchRow("Prefer 3D board", prefer3d, onPrefer3dChange)
            Text("Engine depth: ${settings.engineDepth}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = settings.engineDepth.coerceIn(GameSettings.MIN_DEPTH, GameSettings.MAX_DEPTH).toFloat(),
                onValueChange = { onChange(settings.copy(engineDepth = it.roundToInt())) },
                valueRange = GameSettings.MIN_DEPTH.toFloat()..GameSettings.MAX_DEPTH.toFloat(),
                steps = GameSettings.MAX_DEPTH - GameSettings.MIN_DEPTH - 1,
            )
            SettingSwitchRow("Show legal moves", settings.showLegalMoves) { onChange(settings.copy(showLegalMoves = it)) }
            SettingSwitchRow("Show threatened squares", settings.showThreats) { onChange(settings.copy(showThreats = it)) }
            SettingSwitchRow("Haptic feedback", settings.hapticsEnabled) { onChange(settings.copy(hapticsEnabled = it)) }
            SettingSwitchRow("Debug overlay", settings.showDebug) { onChange(settings.copy(showDebug = it)) }
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
