package com.airchess.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class ArGateDecisionTest {

    private fun decide(
        support: ArSupport = ArSupport.READY,
        camera: CameraAccess = CameraAccess.GRANTED,
        prefer3d: Boolean = false,
        failed: Boolean = false,
    ) = decideArGate(support, camera, prefer3d, failed)

    @Test
    fun supportedDeviceWithCameraGetsAr() {
        assertEquals(ArGateScreen.AR, decide())
    }

    @Test
    fun checkingAvailabilityWaits() {
        assertEquals(ArGateScreen.CHECKING, decide(support = ArSupport.CHECKING))
    }

    @Test
    fun unsupportedDeviceExplainsAndOffers3d() {
        assertEquals(ArGateScreen.UNSUPPORTED, decide(support = ArSupport.UNSUPPORTED))
    }

    @Test
    fun missingArCoreAsksForInstall() {
        assertEquals(ArGateScreen.INSTALL_ARCORE, decide(support = ArSupport.NEEDS_INSTALL))
    }

    @Test
    fun cameraPermissionStatesMapToTheirScreens() {
        assertEquals(ArGateScreen.CAMERA_RATIONALE, decide(camera = CameraAccess.NOT_ASKED))
        assertEquals(ArGateScreen.CAMERA_DENIED, decide(camera = CameraAccess.DENIED))
        assertEquals(ArGateScreen.CAMERA_BLOCKED, decide(camera = CameraAccess.DENIED_PERMANENTLY))
    }

    @Test
    fun playIn3dOverridesEverything() {
        assertEquals(ArGateScreen.BOARD_3D, decide(support = ArSupport.UNSUPPORTED, prefer3d = true))
        assertEquals(ArGateScreen.BOARD_3D, decide(camera = CameraAccess.DENIED_PERMANENTLY, prefer3d = true))
    }

    @Test
    fun sessionFailureFallsBackToExplanation() {
        assertEquals(ArGateScreen.UNSUPPORTED, decide(failed = true))
    }
}
