import Combine
import CoreGraphics
import Foundation
import RealityKit
import SceneViewSwift
import Shared
import simd

/// Screen rectangles of the 64 squares, published only for UI tests. SceneView exposes no
/// world-to-screen projection, so this repeats its orbit camera math from the reported pose.
@MainActor
final class SquareProbe: ObservableObject {
    static let shared = SquareProbe()
    static let isEnabled = ProcessInfo.processInfo.arguments.contains("-AirChessSquareProbe")

    @Published private(set) var rects: [Int: CGRect] = [:]

    private var last: (scene: BoardScene, pose: SceneCameraPose, viewport: CGSize, origin: CGPoint)?

    /// SceneView's vertical field of view; its camera never changes it in orbit mode.
    private static let fovDegrees: Float = 60
    private static let cellShrink: CGFloat = 0.6

    private init() {}

    /// Recomputes from the last camera report; the content bounds change as models finish loading.
    func refresh() {
        guard let last else {
            return
        }

        publish(scene: last.scene, pose: last.pose, viewport: last.viewport, origin: last.origin)
    }

    func publish(scene: BoardScene, pose: SceneCameraPose, viewport: CGSize, origin: CGPoint) {
        guard viewport.width > 0, viewport.height > 0 else {
            return
        }

        last = (scene, pose, viewport, origin)

        let contentCenter = scene.root.visualBounds(relativeTo: scene.root.parent).center
        let yaw = scene.perspective == .black ? Float.pi : 0
        let rotation = simd_quatf(angle: yaw, axis: SIMD3<Float>(0, 1, 0))
        let size = scene.layout.squareSize

        var result: [Int: CGRect] = [:]
        for square in 0..<64 {
            let index = Int32(square)
            let local = SIMD3<Float>(scene.layout.centerX(square: index), 0, scene.layout.centerZ(square: index))
            let world = rotation.act(local) - contentCenter
            let edge = rotation.act(local + SIMD3<Float>(size, 0, 0)) - contentCenter

            guard let center = Self.project(world, pose: pose, viewport: viewport),
                  let side = Self.project(edge, pose: pose, viewport: viewport) else {
                continue
            }

            let extent = abs(side.x - center.x) * Self.cellShrink
            result[square] = CGRect(
                x: origin.x + center.x - extent / 2,
                y: origin.y + center.y - extent / 2,
                width: extent,
                height: extent
            )
        }
        rects = result
    }

    /// Pinhole projection of a world point for the orbit camera described by [pose].
    private static func project(_ point: SIMD3<Float>, pose: SceneCameraPose, viewport: CGSize) -> CGPoint? {
        let eye = pose.cameraPosition()
        let forward = simd_normalize(pose.target - eye)
        let right = simd_normalize(simd_cross(forward, SIMD3<Float>(0, 1, 0)))
        let up = simd_cross(right, forward)

        let offset = point - eye
        let depth = simd_dot(offset, forward)
        guard depth > 0.001 else {
            return nil
        }

        let tanY = tan(fovDegrees * .pi / 360)
        let tanX = tanY * Float(viewport.width / viewport.height)
        let ndcX = simd_dot(offset, right) / (depth * tanX)
        let ndcY = simd_dot(offset, up) / (depth * tanY)

        return CGPoint(
            x: CGFloat((ndcX + 1) / 2) * viewport.width,
            y: CGFloat((1 - ndcY) / 2) * viewport.height
        )
    }
}
