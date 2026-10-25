package com.airchess.ar

import com.airchess.chess.ChessSquare
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.inverse
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Transform
import io.github.sceneview.math.worldToLocalDirection
import io.github.sceneview.math.worldToLocalPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The renderer's SceneView transforms must agree with the shared board math. */
class BoardSceneMathTest {

    private val geometry = BoardGeometry()

    @Test
    fun blackPerspectiveRotatedRootStillMapsToTheTappedSquare() {
        val root = Transform(Position(0f, 0f, 0f), Rotation(0f, 180f, 0f))
        val toLocal = inverse(root)

        ChessSquare.ALL.forEach { square ->
            val local = geometry.squareToLocalPosition(square)
            val world = root * Float4(local.x, local.y, local.z, 1f)
            val worldOrigin = Float3(world.x, 0.5f, world.z)
            val worldDirection = Float3(0f, -1f, 0f)

            val hit = BoardPicker.pick(
                geometry,
                worldToLocalPosition(worldOrigin, toLocal).toVec3(),
                worldToLocalDirection(worldDirection, toLocal).toVec3(),
                emptyMap(),
            )

            assertEquals(square, hit)
        }
    }

    @Test
    fun blackPerspectivePutsA1AtFarRightFromTheWhiteSide() {
        val root = Transform(Position(0f, 0f, 0f), Rotation(0f, 180f, 0f))
        val a1 = geometry.squareToLocalPosition(ChessSquare.A1)

        val world = root * Float4(a1.x, a1.y, a1.z, 1f)

        assertEquals(0.175f, world.x, 1e-4f)
        assertEquals(-0.175f, world.z, 1e-4f)
    }
}
