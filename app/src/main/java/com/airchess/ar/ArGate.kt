package com.airchess.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.airchess.R
import com.airchess.chess.ChessSquare
import com.airchess.model.ArStatus
import com.airchess.model.BoardViewState
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableException
import kotlinx.coroutines.delay

private const val AVAILABILITY_POLL_MS = 200L

/**
 * Chooses between AR and the 3D board. Checks ARCore availability (offering install or update),
 * walks the camera permission flow (rationale, retry, settings when blocked) and falls back to
 * [Board3DScreen] when AR cannot run or the user prefers 3D. The decision itself lives in
 * [decideArGate].
 */
@Composable
fun ArGate(
    board: BoardViewState,
    repositionToken: Int,
    onSquareTapped: (ChessSquare) -> Unit,
    onStatusChanged: (ArStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var support by remember { mutableStateOf(ArSupport.CHECKING) }
    var recheck by remember { mutableIntStateOf(0) }
    var cameraAsked by rememberSaveable { mutableStateOf(false) }
    var camera by remember { mutableStateOf(readCameraAccess(context, activity, cameraAsked)) }
    var prefer3d by rememberSaveable { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraAsked = true
        camera = readCameraAccess(context, activity, asked = true)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        camera = readCameraAccess(context, activity, cameraAsked)
        recheck++
    }
    LaunchedEffect(recheck) { support = awaitSupport(context) }

    val screen = decideArGate(support, camera, prefer3d, sessionError != null)

    Box(modifier) {
        when (screen) {
            ArGateScreen.AR -> ArChessScreen(
                board = board,
                repositionToken = repositionToken,
                onSquareTapped = onSquareTapped,
                onStatusChanged = onStatusChanged,
                modifier = Modifier.fillMaxSize(),
                onSessionFailed = { sessionError = it },
            )
            ArGateScreen.BOARD_3D -> Board3DScreen(board, onSquareTapped, Modifier.fillMaxSize())
            ArGateScreen.CHECKING -> GateCard(
                title = stringResource(R.string.ar_gate_checking),
                busy = true,
            )
            ArGateScreen.UNSUPPORTED -> GateCard(
                title = stringResource(R.string.ar_unsupported_title),
                body = sessionError?.let { stringResource(R.string.ar_session_failed_body, it) }
                    ?: stringResource(R.string.ar_unsupported_body),
                secondary = stringResource(R.string.ar_play_in_3d) to { prefer3d = true },
            )
            ArGateScreen.INSTALL_ARCORE -> GateCard(
                title = stringResource(R.string.ar_install_title),
                body = stringResource(if (installError) R.string.ar_install_declined else R.string.ar_install_body),
                primary = stringResource(R.string.ar_install_action) to {
                    installError = !requestInstall(activity)
                    recheck++
                },
                secondary = stringResource(R.string.ar_play_in_3d) to { prefer3d = true },
            )
            ArGateScreen.CAMERA_RATIONALE -> GateCard(
                title = stringResource(R.string.ar_camera_title),
                body = stringResource(R.string.ar_camera_rationale),
                primary = stringResource(R.string.ar_camera_grant) to { cameraLauncher.launch(Manifest.permission.CAMERA) },
                secondary = stringResource(R.string.ar_play_in_3d) to { prefer3d = true },
            )
            ArGateScreen.CAMERA_DENIED -> GateCard(
                title = stringResource(R.string.ar_camera_title),
                body = stringResource(R.string.ar_camera_denied_body),
                primary = stringResource(R.string.ar_camera_grant) to { cameraLauncher.launch(Manifest.permission.CAMERA) },
                secondary = stringResource(R.string.ar_play_in_3d) to { prefer3d = true },
            )
            ArGateScreen.CAMERA_BLOCKED -> GateCard(
                title = stringResource(R.string.ar_camera_title),
                body = stringResource(R.string.ar_camera_blocked_body),
                primary = stringResource(R.string.ar_camera_open_settings) to { openAppSettings(context) },
                secondary = stringResource(R.string.ar_play_in_3d) to { prefer3d = true },
            )
        }
    }
}

@Composable
private fun GateCard(
    title: String,
    body: String? = null,
    busy: Boolean = false,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (body != null) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                if (busy) {
                    CircularProgressIndicator()
                }
                primary?.let { (label, action) ->
                    Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                secondary?.let { (label, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        }
    }
}

/** Polls ARCore until it stops answering "checking", then reduces the verdict to [ArSupport]. */
private suspend fun awaitSupport(context: Context): ArSupport {
    while (true) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            delay(AVAILABILITY_POLL_MS)
            continue
        }

        return when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupport.READY
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            -> ArSupport.NEEDS_INSTALL
            else -> ArSupport.UNSUPPORTED
        }
    }
}

/** Returns false when ARCore reports that installing is impossible or was declined. */
private fun requestInstall(activity: Activity?): Boolean {
    if (activity == null) {
        return false
    }

    return try {
        ArCoreApk.getInstance().requestInstall(activity, true)
        true
    } catch (error: UnavailableException) {
        false
    }
}

private fun readCameraAccess(context: Context, activity: Activity?, asked: Boolean): CameraAccess {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    return when {
        granted -> CameraAccess.GRANTED
        !asked -> CameraAccess.NOT_ASKED
        activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) -> CameraAccess.DENIED
        else -> CameraAccess.DENIED_PERMANENTLY
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
