package com.airchess.ar

/** ARCore availability reduced to what the gate needs. */
enum class ArSupport {
    CHECKING,
    READY,
    NEEDS_INSTALL,
    UNSUPPORTED,
}

/** Camera permission as the gate sees it. */
enum class CameraAccess {
    GRANTED,
    NOT_ASKED,
    DENIED,
    DENIED_PERMANENTLY,
}

enum class ArGateScreen {
    CHECKING,
    UNSUPPORTED,
    INSTALL_ARCORE,
    CAMERA_RATIONALE,
    CAMERA_DENIED,
    CAMERA_BLOCKED,
    AR,
    BOARD_3D,
}

/** Single place that decides which screen the user gets. */
fun decideArGate(support: ArSupport, camera: CameraAccess, prefer3d: Boolean, sessionFailed: Boolean): ArGateScreen {
    if (prefer3d) {
        return ArGateScreen.BOARD_3D
    }

    return when {
        sessionFailed -> ArGateScreen.UNSUPPORTED
        support == ArSupport.CHECKING -> ArGateScreen.CHECKING
        support == ArSupport.UNSUPPORTED -> ArGateScreen.UNSUPPORTED
        support == ArSupport.NEEDS_INSTALL -> ArGateScreen.INSTALL_ARCORE
        camera == CameraAccess.NOT_ASKED -> ArGateScreen.CAMERA_RATIONALE
        camera == CameraAccess.DENIED -> ArGateScreen.CAMERA_DENIED
        camera == CameraAccess.DENIED_PERMANENTLY -> ArGateScreen.CAMERA_BLOCKED
        else -> ArGateScreen.AR
    }
}
