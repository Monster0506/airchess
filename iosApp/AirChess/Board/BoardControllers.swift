import ARKit
import AVFoundation
import RealityKit
import Shared
import SwiftUI
import UIKit

/// One live board view hosted for Kotlin: the 3D scene or the AR scene.
@MainActor
final class BoardControllerImpl: NSObject, BoardController {
    private let scene: BoardScene
    private let arSession: ArSession?
    private(set) var renderView: ARView?
    var onRenderViewChanged: ((ARView?) -> Void)?

    lazy var viewController: UIViewController = {
        if let arSession {
            return UIHostingController(rootView: ArBoardView(session: arSession))
        }
        return UIHostingController(
            rootView: Board3DView(
                scene: scene,
                onSquareTapped: { [weak self] square in
                    self?.listener?.onSquareTapped(square: square)
                },
                onViewReady: { [weak self] in self?.setRenderView($0) }
            )
        )
    }()
    private var listener: BoardListener?

    init(mode: BoardMode, layout: BoardLayout, listener: BoardListener) {
        self.listener = listener
        let scene = BoardScene(layout: layout) { message in
            listener.onSessionFailed(message: message)
        }
        self.scene = scene
        self.arSession = mode == .ar ? ArSession(scene: scene, listener: listener) : nil
        arSession?.onViewReady = { [weak self] in self?.setRenderView($0) }
    }

    func render(frame: BoardFrame) {
        scene.apply(frame)
    }

    func pause() {
        arSession?.pause()
    }

    func resume() {
        arSession?.resume()
    }

    func dispose() {
        arSession?.detach()
        setRenderView(nil)
        listener = nil
    }

    func resetPlacement() {
        arSession?.reset()
    }

    private func setRenderView(_ view: ARView?) {
        guard renderView !== view else { return }
        renderView = view
        onRenderViewChanged?(view)
    }
}

/// Everything the shared AR gate asks the device, plus board view creation.
@MainActor
final class BoardControllerFactoryImpl: NSObject, BoardControllerFactory {
    var onRenderViewChanged: ((ARView?) -> Void)?

    var arSupported: Bool {
        ARWorldTrackingConfiguration.isSupported
    }

    func create(mode: BoardMode, layout: BoardLayout, listener: BoardListener) -> BoardController {
        let controller = BoardControllerImpl(mode: mode, layout: layout, listener: listener)
        controller.onRenderViewChanged = onRenderViewChanged
        if let renderView = controller.renderView {
            onRenderViewChanged?(renderView)
        }
        return controller
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
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
