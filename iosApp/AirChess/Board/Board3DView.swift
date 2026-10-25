import RealityKit
import SwiftUI
import UIKit
import simd

/// Non-AR board with a native RealityKit camera and the same render surface as AR.
struct Board3DView: View {
    let scene: BoardScene
    let onSquareTapped: (Int32) -> Void
    let onViewReady: (ARView?) -> Void

    var body: some View {
        Board3DRepresentable(
            scene: scene,
            onSquareTapped: onSquareTapped,
            onViewReady: onViewReady
        )
        .ignoresSafeArea()
    }
}

private struct Board3DRepresentable: UIViewRepresentable {
    let scene: BoardScene
    let onSquareTapped: (Int32) -> Void
    let onViewReady: (ARView?) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(scene: scene, onSquareTapped: onSquareTapped, onViewReady: onViewReady)
    }

    func makeUIView(context: Context) -> ARView {
        let view = ARView(frame: .zero, cameraMode: .nonAR, automaticallyConfigureSession: false)
        context.coordinator.attach(to: view)
        return view
    }

    func updateUIView(_ view: ARView, context: Context) {
        context.coordinator.onViewReady = onViewReady
        context.coordinator.refreshProbe()
    }

    static func dismantleUIView(_ view: ARView, coordinator: Coordinator) {
        coordinator.detach()
    }

    @MainActor
    final class Coordinator: NSObject {
        private let scene: BoardScene
        private let anchor = AnchorEntity(world: .zero)
        private let cameraAnchor = AnchorEntity(world: .zero)
        private let camera = PerspectiveCamera()
        private var arView: ARView?
        private var probeTask: Task<Void, Never>?
        private var yaw: Float = 0
        private var elevation: Float = 0.87
        private var distance: Float = 0.82
        var onViewReady: (ARView?) -> Void

        private let onSquareTapped: (Int32) -> Void

        init(scene: BoardScene, onSquareTapped: @escaping (Int32) -> Void, onViewReady: @escaping (ARView?) -> Void) {
            self.scene = scene
            self.onSquareTapped = onSquareTapped
            self.onViewReady = onViewReady
        }

        func attach(to view: ARView) {
            arView = view
            anchor.addChild(scene.root)
            cameraAnchor.addChild(camera)
            view.scene.addAnchor(anchor)
            view.scene.addAnchor(cameraAnchor)
            updateCamera()

            let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
            view.addGestureRecognizer(tap)
            let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
            pan.maximumNumberOfTouches = 1
            view.addGestureRecognizer(pan)
            let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
            view.addGestureRecognizer(pinch)

            onViewReady(view)
            startProbe()
        }

        func detach() {
            probeTask?.cancel()
            probeTask = nil
            onViewReady(nil)
            arView = nil
            scene.root.removeFromParent()
        }

        func refreshProbe() {
            guard SquareProbe.isEnabled, let arView else { return }
            SquareProbe.shared.publish(scene: scene, view: arView)
        }

        private func startProbe() {
            guard SquareProbe.isEnabled else { return }
            probeTask = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: 500_000_000)
                    self?.refreshProbe()
                }
            }
        }

        private func updateCamera() {
            let horizontal = distance * cos(elevation)
            camera.position = SIMD3<Float>(horizontal * sin(yaw), distance * sin(elevation), horizontal * cos(yaw))
            camera.look(at: .zero, from: camera.position, relativeTo: cameraAnchor)
        }

        @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let arView, gesture.state == .ended else { return }
            if let entity = arView.entity(at: gesture.location(in: arView)),
               let square = scene.square(for: entity) {
                onSquareTapped(square)
            }
        }

        @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
            guard let arView, gesture.state == .changed else { return }
            let delta = gesture.translation(in: arView)
            yaw -= Float(delta.x) * 0.008
            elevation = min(max(elevation + Float(delta.y) * 0.006, 0.25), 1.35)
            gesture.setTranslation(.zero, in: arView)
            updateCamera()
            refreshProbe()
        }

        @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            guard gesture.state == .changed else { return }
            distance = min(max(distance / Float(gesture.scale), 0.45), 1.8)
            gesture.scale = 1
            updateCamera()
            refreshProbe()
        }
    }
}
