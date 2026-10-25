import Shared
import SwiftUI
import UIKit

@main
struct AirChessApp: App {
    @State private var viewModelOwner: IosViewModelOwner
    private let boardFactory: BoardControllerFactoryImpl
    private let sharingBridge: IosViewSharingBridgeImpl


    init() {
        if ProcessInfo.processInfo.arguments.contains("-AirChessUITestReset"),
           let bundleID = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: bundleID)
        }

        guard
            let serverURL = Bundle.main.object(forInfoDictionaryKey: "AirChessServerUrl") as? String,
            !serverURL.isEmpty
        else {
            fatalError("AirChessServerUrl is not configured")
        }

        let engine = IosStockfishEngine()
        let bridge = IosViewSharingBridgeImpl()
        let boardFactory = BoardControllerFactoryImpl()
        boardFactory.onRenderViewChanged = { [weak bridge] view in
            bridge?.renderView = view
        }

        _viewModelOwner = State(
            initialValue: IosViewModelOwner(
                serverUrl: serverURL,
                engineProvider: { engine },
                viewSharing: IosViewSharing(bridge: bridge)
            )
        )
        self.boardFactory = boardFactory
        self.sharingBridge = bridge
    }

    var body: some Scene {
        WindowGroup {
            IosAppRoot(
                viewModel: viewModelOwner.viewModel,
                boardFactory: boardFactory,
                sharingBridge: sharingBridge
            )
            .onOpenURL { url in
                guard let code = GameLinksKt.parseJoinCode(uri: url.absoluteString) else { return }
                viewModelOwner.viewModel.handleDeepLink(code: code)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willTerminateNotification)) { _ in
                viewModelOwner.clear()
            }
        }
    }
}

private struct IosAppRoot: View {
    let viewModel: ChessViewModel
    let boardFactory: BoardControllerFactoryImpl
    @ObservedObject var sharingBridge: IosViewSharingBridgeImpl
    @State private var expandedSharing = false
    @State private var showOpponent = true

    var body: some View {
        ZStack {
            SharedAppController(viewModel: viewModel, boardFactory: boardFactory)
                .ignoresSafeArea()
            if SquareProbe.isEnabled {
                SquareProbeOverlay()
            }
            if sharingBridge.isActive {
                SharingOverlay(
                    bridge: sharingBridge,
                    expanded: $expandedSharing,
                    showOpponent: $showOpponent
                )
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

private struct SharingOverlay: View {
    @ObservedObject var bridge: IosViewSharingBridgeImpl
    @Binding var expanded: Bool
    @Binding var showOpponent: Bool

    var body: some View {
        ZStack(alignment: .topTrailing) {
            SharingVideoSurface(bridge: bridge, showOpponent: $showOpponent)
                .frame(width: expanded ? nil : 148, height: expanded ? nil : 194)
                .frame(maxWidth: expanded ? .infinity : nil, maxHeight: expanded ? .infinity : nil)
                .clipShape(RoundedRectangle(cornerRadius: expanded ? 0 : 14))
                .shadow(radius: expanded ? 0 : 12)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: expanded ? .center : .topTrailing)
                .padding(.top, expanded ? 0 : 112)
                .padding(.trailing, expanded ? 0 : 12)

            VStack(alignment: .trailing, spacing: 6) {
                Button(expanded ? "Close view" : "Expand view") {
                    expanded.toggle()
                }
                .buttonStyle(.borderedProminent)
                Text(bridge.statusText)
                    .font(.caption2)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(.black.opacity(0.7), in: Capsule())
                    .foregroundStyle(.white)
            }
            .padding(.top, expanded ? 54 : 116)
            .padding(.trailing, expanded ? 12 : 20)
        }
        .ignoresSafeArea(edges: expanded ? .all : [])
    }
}

private struct SharingVideoSurface: UIViewRepresentable {
    let bridge: IosViewSharingBridgeImpl
    @Binding var showOpponent: Bool

    func makeUIView(context: Context) -> UIView {
        let showOpponent = $showOpponent
        return bridge.makeView(onSwap: { showOpponent.wrappedValue.toggle() })
    }

    func updateUIView(_ view: UIView, context: Context) {
        bridge.showOpponent(opponent: showOpponent, view: view)
    }
}
