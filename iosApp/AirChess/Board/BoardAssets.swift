import Foundation
import RealityKit
import SceneViewSwift
import Shared

/// The 13 USDZ models, loaded once. Scenes take clones, so the geometry is shared.
@MainActor
final class BoardAssets {
    static let shared = BoardAssets()

    private var prototypes: [String: Entity] = [:]
    private var loading: Task<Void, Error>?

    private static let boardName = "board"

    private init() {}

    /// Starts loading on first call; every caller waits for the same load.
    func ready() async throws {
        if let loading {
            try await loading.value
            return
        }

        let task = Task { try await self.loadAll() }
        loading = task
        try await task.value
    }

    func makeBoard() -> Entity? {
        prototypes[Self.boardName]?.clone(recursive: true)
    }

    func makePiece(kind: PieceKind, side: PieceSide) -> Entity? {
        prototypes[Self.modelName(kind: kind, side: side)]?.clone(recursive: true)
    }

    private func loadAll() async throws {
        var names = [Self.boardName]
        for side in [PieceSide.white, PieceSide.black] {
            for kind in Self.allKinds {
                names.append(Self.modelName(kind: kind, side: side))
            }
        }

        for name in names {
            try await load(name)
        }
    }

    private func load(_ name: String) async throws {
        guard let url = Self.url(for: name) else {
            throw BoardAssetError.missing(name)
        }

        do {
            let node = try await ModelNode.load(contentsOf: url, enableCollision: false)
            prototypes[name] = node.entity
        } catch {
            throw BoardAssetError.load(name, error)
        }
    }

    private enum BoardAssetError: LocalizedError {
        case missing(String)
        case load(String, Error)

        var errorDescription: String? {
            switch self {
            case .missing(let name):
                return "Missing board model: \(name).usdz"
            case .load(let name, let error):
                return "Could not load \(name).usdz: \(error.localizedDescription)"
            }
        }
    }

    private static let allKinds: [PieceKind] = [.pawn, .knight, .bishop, .rook, .queen, .king]

    private static func modelName(kind: PieceKind, side: PieceSide) -> String {
        let color = side == .white ? "white" : "black"
        let type: String
        switch kind {
        case .pawn: type = "pawn"
        case .knight: type = "knight"
        case .bishop: type = "bishop"
        case .rook: type = "rook"
        case .queen: type = "queen"
        default: type = "king"
        }
        return "\(color)_\(type)"
    }

    /// The Models folder is a folder reference; fall back to the bundle root for a flattened copy.
    private static func url(for name: String) -> URL? {
        Bundle.main.url(forResource: name, withExtension: "usdz", subdirectory: "Models")
            ?? Bundle.main.url(forResource: name, withExtension: "usdz")
    }
}
