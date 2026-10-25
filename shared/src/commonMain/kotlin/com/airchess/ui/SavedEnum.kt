package com.airchess.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

// Enums are not auto-saveable outside Android, so they are stored by name.
@Composable
internal inline fun <reified T : Enum<T>> rememberSavedEnum(initial: T): MutableState<T> =
    rememberSaveable(
        stateSaver = Saver<T, Any>(save = { it.name }, restore = { enumValueOf<T>(it as String) }),
    ) { mutableStateOf(initial) }
