import ARKit
import RealityKit
import SceneViewSwift
import SwiftUI

/// The AR board: ARSceneView for the camera and plane detection, ArSession for placement.
struct ArBoardView: View {
    @ObservedObject var session: ArSession

    private static let chipOffset: CGFloat = 140

    var body: some View {
        ZStack {
            ARSceneView(
                planeDetection: .horizontal,
                showPlaneOverlay: false,
                showCoachingOverlay: false,
                showPlacementReticle: false,
                groundingShadows: false
            )
            .onSessionStarted { arView in
                MainActor.assumeIsolated { session.attach(arView) }
            }
            .onFrame { frame, arView in
                MainActor.assumeIsolated { session.onFrame(frame, in: arView) }
            }
            .onSessionError { error, _ in
                MainActor.assumeIsolated { session.fail(error) }
            }
            .ignoresSafeArea()

            if let text = session.coaching {
                Text(text)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(.black.opacity(0.65), in: Capsule())
                    .offset(y: Self.chipOffset)
                    .allowsHitTesting(false)
            }
        }
    }
}
