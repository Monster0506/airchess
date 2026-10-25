import ARKit
import Combine
import RealityKit
import Shared
import SwiftUI
import UIKit
import simd

/// Placement and tracking of the AR board. The reticle follows the screen center until a tap
/// anchors the board; tracking facts go to Kotlin every frame, where ArStatusTracker folds them.
@MainActor
final class ArSession: NSObject, ObservableObject {
    @Published private(set) var coaching: String?

    private let scene: BoardScene
    private let listener: BoardListener
    private weak var arView: ARView?

    private let reticleAnchor = AnchorEntity(world: .zero)
    private var boardAnchor: AnchorEntity?
    private var arAnchor: ARAnchor?

    private var lastHit: simd_float4x4?
    private var frames = 0
    private var planeCount: Int32 = 0
    private var trackingLost = false
    private var everTracked = false

    private static let minHitDistance: Float = 0.25
    private static let planeCountInterval = 15
    private static let nanosPerSecond = 1_000_000_000.0
    private static let reticleBar: Float = 0.004
    private static let reticleThickness: Float = 0.001
    private static let reticleLift: Float = 0.002

    init(scene: BoardScene, listener: BoardListener) {
        self.scene = scene
        self.listener = listener
        super.init()
        buildReticle()
    }

    private var isPlacing: Bool { boardAnchor == nil }

    func attach(_ arView: ARView) {
        self.arView = arView
        reticleAnchor.isEnabled = false
        arView.scene.addAnchor(reticleAnchor)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        arView.addGestureRecognizer(tap)
    }

    func fail(_ error: Error) {
        listener.onSessionFailed(message: error.localizedDescription)
    }

    /// Drops the anchor and returns to surface placement.
    func reset() {
        guard let arView else {
            return
        }

        scene.root.removeFromParent()
        if let boardAnchor {
            arView.scene.removeAnchor(boardAnchor)
        }
        if let arAnchor {
            arView.session.remove(anchor: arAnchor)
        }
        boardAnchor = nil
        arAnchor = nil
        lastHit = nil
        updateCoaching()
    }

    func onFrame(_ frame: ARFrame, in arView: ARView) {
        frames += 1
        if frames % Self.planeCountInterval == 1 {
            planeCount = Int32(frame.anchors.filter { ($0 as? ARPlaneAnchor)?.alignment == .horizontal }.count)
        }

        if isPlacing {
            updateReticle(in: arView, frame: frame)
        }

        let camera = frame.camera
        let tracking = camera.trackingState == .normal
        everTracked = everTracked || tracking
        trackingLost = !tracking && everTracked

        listener.onArFrame(
            timestampNanos: Int64(frame.timestamp * Self.nanosPerSecond),
            cameraTracking: tracking,
            failure: Self.failureText(camera.trackingState),
            planeCount: planeCount,
            anchorState: anchorState(),
            anchored: !isPlacing
        )
        updateCoaching()
    }

    // MARK: Placement

    private func updateReticle(in arView: ARView, frame: ARFrame) {
        let center = CGPoint(x: arView.bounds.midX, y: arView.bounds.midY)
        lastHit = surfaceHit(at: center, in: arView, frame: frame)

        if let lastHit {
            reticleAnchor.transform = Transform(matrix: lastHit)
            reticleAnchor.isEnabled = true
        } else {
            reticleAnchor.isEnabled = false
        }
    }

    private func surfaceHit(at point: CGPoint, in arView: ARView, frame: ARFrame) -> simd_float4x4? {
        let camera = frame.camera.transform.columns.3
        let eye = SIMD3<Float>(camera.x, camera.y, camera.z)

        for result in arView.raycast(from: point, allowing: .existingPlaneGeometry, alignment: .horizontal) {
            let column = result.worldTransform.columns.3
            let position = SIMD3<Float>(column.x, column.y, column.z)
            if simd_distance(eye, position) >= Self.minHitDistance {
                return result.worldTransform
            }
        }
        return nil
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        guard let arView else {
            return
        }

        let point = gesture.location(in: arView)
        if isPlacing {
            place(at: point, in: arView)
            return
        }

        if let entity = arView.entity(at: point), let square = scene.square(for: entity) {
            listener.onSquareTapped(square: square)
        }
    }

    /// Anchors the board at the tapped surface point, falling back to what the reticle shows.
    private func place(at point: CGPoint, in arView: ARView) {
        let tapped = arView.session.currentFrame.flatMap { surfaceHit(at: point, in: arView, frame: $0) }
        guard let hit = tapped ?? lastHit else {
            return
        }

        let column = hit.columns.3
        let position = SIMD3<Float>(column.x, column.y, column.z)

        // Turn about the up axis so the board's +Z (white's side) points at the camera.
        let eye = arView.cameraTransform.translation
        let yaw = atan2(eye.x - position.x, eye.z - position.z)
        let pose = Transform(
            scale: .one,
            rotation: simd_quatf(angle: yaw, axis: SIMD3<Float>(0, 1, 0)),
            translation: position
        )

        let anchor = ARAnchor(name: "airchess-board", transform: pose.matrix)
        arView.session.add(anchor: anchor)

        let entity = AnchorEntity(anchor: anchor)
        entity.addChild(scene.root)
        arView.scene.addAnchor(entity)

        arAnchor = anchor
        boardAnchor = entity
        reticleAnchor.isEnabled = false
        updateCoaching()
    }

    private func buildReticle() {
        let size = scene.layout.boardSize
        let half = size / 2 - Self.reticleBar / 2
        let material = UnlitMaterial.overlay(BoardPalette.reticle)
        let long = MeshResource.generateBox(size: SIMD3<Float>(size, Self.reticleThickness, Self.reticleBar))
        let short = MeshResource.generateBox(size: SIMD3<Float>(Self.reticleBar, Self.reticleThickness, size))

        let bars: [(MeshResource, SIMD3<Float>)] = [
            (long, SIMD3<Float>(0, Self.reticleLift, half)),
            (long, SIMD3<Float>(0, Self.reticleLift, -half)),
            (short, SIMD3<Float>(half, Self.reticleLift, 0)),
            (short, SIMD3<Float>(-half, Self.reticleLift, 0)),
        ]
        for (mesh, offset) in bars {
            let bar = ModelEntity(mesh: mesh, materials: [material])
            bar.position = offset
            reticleAnchor.addChild(bar)
        }
    }

    // MARK: Status

    private func anchorState() -> String {
        guard let boardAnchor else {
            return "none"
        }
        return boardAnchor.isAnchored ? "tracked" : "pending"
    }

    private func updateCoaching() {
        let text: String?
        if trackingLost {
            text = "Tracking lost. Move your phone slowly."
        } else if !isPlacing {
            text = nil
        } else if lastHit != nil {
            text = "Tap to place the board"
        } else {
            text = "Move your phone to find a table"
        }

        if text != coaching {
            coaching = text
        }
    }

    private static func failureText(_ state: ARCamera.TrackingState) -> String? {
        switch state {
        case .normal:
            return nil
        case .notAvailable:
            return "Tracking unavailable"
        case .limited(let reason):
            switch reason {
            case .excessiveMotion: return "Moving too fast"
            case .insufficientFeatures: return "Surface has too little detail"
            case .relocalizing: return "Relocalizing"
            default: return nil
            }
        }
    }
}
