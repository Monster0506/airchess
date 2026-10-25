package com.airchess.ar

import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Transform
import io.github.sceneview.math.times
import org.junit.Assert.assertEquals
import org.junit.Test

/** SceneView's yaw rotation must turn a bar's +X axis the way the shared layout assumes. */
class ArrowYawSceneTest {

    private fun axisAfterYaw(yaw: Float): Float3 =
        Transform(Position(0f, 0f, 0f), Rotation(0f, yaw, 0f)) * Float3(1f, 0f, 0f)

    @Test
    fun yawTurnsLocalXOntoGroundDirection() {
        listOf(0f to 1f, 1f to 0f, 0f to -1f, -1f to 0f, 0.6f to -0.8f).forEach { (x, z) ->
            val axis = axisAfterYaw(ArrowLayout.yawOf(x, z))

            assertEquals("x for ($x,$z)", x, axis.x, 1e-4f)
            assertEquals("z for ($x,$z)", z, axis.z, 1e-4f)
        }
    }
}
