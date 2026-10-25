import ARKit
import AVFoundation
import Shared
import SwiftUI
import UIKit

/// One live board view hosted for Kotlin: the 3D scene or the AR scene.
@MainActor
final class BoardControllerImpl: NSObject, BoardController {
    let viewController: UIViewController

    private let scene: BoardScene
    private let arSession: ArSession?

    init(mode: BoardMode, layout: BoardLayout, listener: BoardListener) {
        let scene = BoardScene(layout: layout)
        self.scene = scene

        if mode == .ar {
            let session = ArSession(scene: scene, listener: listener)
            arSession = session
            viewController = UIHostingController(rootView: ArBoardView(session: session))
        } else {
            arSession = nil
            viewController = UIHostingController(
                rootView: Board3DView(scene: scene) { square in
                    listener.onSquareTapped(square: square)
                }
            )
        }
        super.init()
    }

    func render(frame: BoardFrame) {
        scene.apply(frame)
    }

    func resetPlacement() {
        arSession?.reset()
    }
}

/// Everything the shared AR gate asks the device, plus board view creation.
@MainActor
final class BoardControllerFactoryImpl: NSObject, BoardControllerFactory {
    var arSupported: Bool {
        ARWorldTrackingConfiguration.isSupported
    }

    override init() {
        super.init()
        Task {
            await BoardAssets.shared.ready()
        }
    }

    func create(mode: BoardMode, layout: BoardLayout, listener: BoardListener) -> BoardController {
        BoardControllerImpl(mode: mode, layout: layout, listener: listener)
    }

    func cameraAccess() -> CameraAccess {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: return .granted
        case .notDetermined: return .notAsked
        default: return .deniedPermanently
        }
    }

    func requestCamera(callback: CameraPromptCallback) {
        AVCaptureDevice.requestAccess(for: .video) { _ in
            DispatchQueue.main.async {
                callback.onAnswered()
            }
        }
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        UIApplication.shared.open(url)
    }
}
