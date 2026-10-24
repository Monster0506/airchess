package com.airchess.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchess.model.GameUiState
import com.airchess.protocol.ColorChoice
import com.airchess.protocol.Protocol
import com.airchess.protocol.TimeControl

private const val TAB_CREATE = 0
private const val TAB_JOIN = 1
private const val CODE_FONT_SP = 44
private const val CODE_SPACING_SP = 6

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayOnlineSheet(
    state: GameUiState,
    onCreate: (name: String, serverUrl: String, timeControl: TimeControl, color: ColorChoice) -> Unit,
    onJoin: (code: String, name: String, serverUrl: String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val online = state.online
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Play online", style = MaterialTheme.typography.titleLarge)
            when {
                online == null -> LobbyForm(state, onCreate, onJoin)
                online.code == null -> ConnectingPanel(onCancel = onLeave)
                else -> GamePanel(code = online.code, waiting = online.waitingForOpponent, onLeave = onLeave)
            }
        }
    }
}

@Composable
private fun ConnectingPanel(onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text("Connecting to server...", style = MaterialTheme.typography.bodyLarge)
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
}

@Composable
private fun GamePanel(code: String, waiting: Boolean, onLeave: () -> Unit) {
    val context = LocalContext.current
    Text(
        text = if (waiting) "Waiting for your opponent. Share this code:" else "Game code",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = code,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = CODE_FONT_SP.sp,
            letterSpacing = CODE_SPACING_SP.sp,
        ),
        textAlign = TextAlign.Center,
    )
    Button(onClick = { shareCode(context, code) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Share, contentDescription = null)
        Text("Share code", modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text(if (waiting) "Cancel game" else "Leave game")
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LobbyForm(
    state: GameUiState,
    onCreate: (String, String, TimeControl, ColorChoice) -> Unit,
    onJoin: (String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(state.playerName) }
    var url by rememberSaveable { mutableStateOf(state.serverUrl) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_CREATE) }
    var timeIndex by rememberSaveable { mutableIntStateOf(1) }
    var color by rememberSaveable { mutableStateOf(ColorChoice.RANDOM) }
    var code by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(MAX_NAME_LENGTH) },
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Server URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    PrimaryTabRow(selectedTabIndex = tab) {
        Tab(selected = tab == TAB_CREATE, onClick = { tab = TAB_CREATE }, text = { Text("Create") })
        Tab(selected = tab == TAB_JOIN, onClick = { tab = TAB_JOIN }, text = { Text("Join") })
    }

    state.lobbyError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    val canSubmit = name.isNotBlank() && url.isNotBlank()
    if (tab == TAB_CREATE) {
        Text("Time control", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeControl.PRESETS.forEachIndexed { index, preset ->
                FilterChip(
                    selected = index == timeIndex,
                    onClick = { timeIndex = index },
                    label = { Text(preset.label) },
                )
            }
        }
        Text("Play as", style = MaterialTheme.typography.labelLarge)
        ColorPicker(selected = color, onSelect = { color = it })
        Button(
            onClick = { onCreate(name, url, TimeControl.PRESETS[timeIndex], color) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create game") }
    } else {
        OutlinedTextField(
            value = code,
            onValueChange = { code = sanitizeCode(it) },
            label = { Text("Game code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onJoin(code, name, url) },
            enabled = canSubmit && code.length == Protocol.CODE_LENGTH,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Join game") }
    }
}

@Composable
private fun ColorPicker(selected: ColorChoice, onSelect: (ColorChoice) -> Unit) {
    val options = listOf(ColorChoice.WHITE to "White", ColorChoice.RANDOM to "Random", ColorChoice.BLACK to "Black")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (choice, label) ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

private const val MAX_NAME_LENGTH = 20

internal fun sanitizeCode(raw: String): String =
    raw.uppercase().filter { it in Protocol.CODE_ALPHABET }.take(Protocol.CODE_LENGTH)

private fun shareCode(context: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join my AirChess game! Code: $code")
    }
    context.startActivity(Intent.createChooser(send, "Share game code").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
