import RealityKit
import SceneViewSwift
import SwiftUI

/// Non-AR board: SceneView with an orbit camera looking at white's side.
/// The black perspective rotates the board, never the camera, exactly as in AR.
struct Board3DView: View {
    let scene: BoardScene
    let onSquareTapped: (Int32) -> Void

    /// Radians above the table; about 50 degrees shows the pieces and the whole board.
    private static let homeElevation: Float = 0.87
    private static let framingMargin: Float = 0.9
    private static let probeIntervalNanos: UInt64 = 500_000_000

    var body: some View {
        GeometryReader { proxy in
            SceneView { root in
                root.addChild(scene.root)
            }
            .cameraControls(.orbit)
            .cameraOrbit(azimuth: 0, elevation: Self.homeElevation)
            .framingMargin(Self.framingMargin)
            .onCameraChanged { pose in
                report(pose, in: proxy)
            }
            .onEntityTapped { entity in
                if let square = scene.square(for: entity) {
                    onSquareTapped(square)
                }
            }
        }
        .ignoresSafeArea()
        .task {
            await refreshProbe()
        }
    }

    private func refreshProbe() async {
        guard SquareProbe.isEnabled else {
            return
        }

        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: Self.probeIntervalNanos)
            SquareProbe.shared.refresh()
        }
    }

    private func report(_ pose: SceneCameraPose, in proxy: GeometryProxy) {
        guard SquareProbe.isEnabled else {
            return
        }

        let size = proxy.size
        let origin = proxy.frame(in: .global).origin
        Task { @MainActor in
            SquareProbe.shared.publish(scene: scene, pose: pose, viewport: size, origin: origin)
        }
    }
}
