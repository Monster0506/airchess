import Shared
import SwiftUI
import UIKit

@main
struct AirChessApp: App {
    @State private var viewModel: ChessViewModel
    private let boardFactory: BoardControllerFactoryImpl

    init() {

        guard
            let serverURL = Bundle.main.object(forInfoDictionaryKey: "AirChessServerUrl") as? String,
            !serverURL.isEmpty
        else {
            fatalError("AirChessServerUrl is not configured")
        }
        let engine = IosStockfishEngine()
        _viewModel = State(
            initialValue: ChessViewModelFactoryKt.createChessViewModel(serverUrl: serverURL) {
                engine
            }
        )
        boardFactory = BoardControllerFactoryImpl()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                SharedAppController(viewModel: viewModel, boardFactory: boardFactory)
                    .ignoresSafeArea()
                if SquareProbe.isEnabled {
                    SquareProbeOverlay()
                }
            }
            .onOpenURL { url in
                guard let code = GameLinksKt.parseJoinCode(uri: url.absoluteString) else { return }
                viewModel.handleDeepLink(code: code)
            }
        }
    }
}

private struct SharedAppController: UIViewControllerRepresentable {
    let viewModel: ChessViewModel
    let boardFactory: BoardControllerFactoryImpl

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController(
            viewModel: viewModel,
            boardSurface: IosBoardSurface(factory: boardFactory)
        )
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
