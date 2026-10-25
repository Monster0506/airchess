package com.airchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airchess.model.GameUiState

@Composable
fun Onboarding(name: String, onComplete: (String) -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    var player by rememberSaveable(name) { mutableStateOf(name.ifBlank { "Player" }) }
    val foreground = Color.White
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("AIRCHESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(28.dp))
        Text(
            if (page == 0) "Chess belongs anywhere." else "Meet your next opponent.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (page == 0) "Set a board on any table and play face to face, or invite a friend online."
            else "Choose the name friends will see when you play online.",
            style = MaterialTheme.typography.bodyLarge,
            color = foreground,
        )
        if (page == 1) {
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(player, { player = it.take(20) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onComplete(player) }) { Text("Skip") }
            Button(onClick = { if (page == 0) page = 1 else onComplete(player) }) {
                Text(if (page == 0) "Continue" else "Get started")
            }
        }
    }
}

@Composable
fun HomeScreen(
    state: GameUiState,
    onLocal: () -> Unit,
    onComputer: () -> Unit,
    onOnline: () -> Unit,
    onResume: () -> Unit,
    onSettings: () -> Unit,
) {
    val foreground = Color.White
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("AIRCHESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Your move.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = foreground)
        Text("Play on a table, across the board, or with a friend.", style = MaterialTheme.typography.bodyLarge, color = foreground)
        Spacer(Modifier.height(4.dp))
        if (state.hasResumeGame) {
            HomeCard("Resume game", "Continue your saved ${if (state.online != null) "online" else state.gameMode} game", onResume)
        }
        HomeCard("Play on the table", "Pass and play with someone beside you", onLocal)
        HomeCard("Play the computer", "Choose a side and difficulty", onComputer)
        HomeCard("Play a friend online", "Create a game or join with a code", onOnline)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
    }
}

@Composable
private fun HomeCard(title: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
