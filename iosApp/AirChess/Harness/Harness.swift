import Shared
import SwiftUI
import UIKit

/// Local-iteration harness: a bare pawn-push board without the shared UI.
/// Removed once the shared Compose UI hosts the board.
struct HarnessView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        HarnessController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

final class HarnessController: UIViewController, BoardListener {
    private let factory = BoardControllerFactoryImpl()
    private let layout = BoardLayout()
    private var board: BoardController?
    private var pieces: [PieceFrame] = []
    private var selected: Int32 = -1
    private var destinations: [Int32] = []
    private var animate = false

    override func viewDidLoad() {
        super.viewDidLoad()
        pieces = Self.initialPieces()

        let controller = factory.create(mode: .threeD, layout: layout, listener: self)
        addChild(controller.viewController)
        controller.viewController.view.frame = view.bounds
        controller.viewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(controller.viewController.view)
        controller.viewController.didMove(toParent: self)
        board = controller
        publish()
    }

    func onSquareTapped(square: Int32) {
        if selected >= 0, destinations.contains(square) {
            move(to: square)
            return
        }

        guard pieces.contains(where: { $0.square == square && $0.side == .white && $0.kind == .pawn }) else {
            selected = -1
            destinations = []
            publish()
            return
        }

        selected = square
        destinations = [square + 8, square + 16]
        publish()
    }

    func onArFrame(
        timestampNanos: Int64,
        cameraTracking: Bool,
        failure: String?,
        planeCount: Int32,
        anchorState: String,
        anchored: Bool
    ) {}

    func onSessionFailed(message: String) {}

    private func move(to square: Int32) {
        pieces = pieces.map { piece in
            piece.square == selected
                ? PieceFrame(id: piece.id, square: square, kind: piece.kind, side: piece.side)
                : piece
        }
        selected = -1
        destinations = []
        animate = true
        publish()
    }

    private func publish() {
        let marks = selected >= 0 ? [MarkFrame(square: selected, kind: .selected)] : []
        let dots = destinations.map { DestinationFrame(square: $0, capture: false) }
        board?.render(
            frame: BoardFrame(
                pieces: pieces,
                marks: marks,
                destinations: dots,
                hintFrom: -1,
                hintTo: -1,
                perspective: .white,
                animate: animate
            )
        )
    }

    private static func initialPieces() -> [PieceFrame] {
        let back: [PieceKind] = [.rook, .knight, .bishop, .queen, .king, .bishop, .knight, .rook]
        var result: [PieceFrame] = []
        var id: Int32 = 0

        for file in 0..<8 {
            let sides: [(PieceSide, Int32, Int32)] = [(.white, 0, 1), (.black, 7, 6)]
            for (side, backRank, pawnRank) in sides {
                result.append(PieceFrame(id: id, square: backRank * 8 + Int32(file), kind: back[file], side: side))
                id += 1
                result.append(PieceFrame(id: id, square: pawnRank * 8 + Int32(file), kind: .pawn, side: side))
                id += 1
            }
        }
        return result
    }
}
