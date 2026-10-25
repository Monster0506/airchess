import RealityKit
import Shared
import UIKit
import simd

/// The RealityKit content of one board: model, pieces, tap bodies and indicators.
/// The same scene is parented under the 3D view's content root or under the AR anchor.
@MainActor
final class BoardScene {
    /// Rotated about +Y for the black perspective; add this to a scene or an anchor.
    let root = Entity()

    let layout: BoardLayout

    private let assets: BoardAssets
    private let driver: MotionDriver
    private let pieceLayer = Entity()
    private let indicatorLayer = Entity()
    private let tapLayer = Entity()

    private var nodes: [Int32: PieceNode] = [:]
    private var latest: BoardFrame?
    private var assetsReady = false
    private var hasShown = false
    private(set) var perspective: PieceSide = .white

    private static let tilePrefix = "tile-"
    private static let piecePrefix = "piece-"
    private static let tapDepth: Float = 0.004
    private static let tileFill: Float = 0.98
    private static let dotRadius: Float = 0.3
    private static let ringHalf: Float = 0.46
    private static let ringBar: Float = 0.07

    private lazy var tileMesh = MeshResource.generateBox(
        size: SIMD3<Float>(layout.squareSize * Self.tileFill, OverlayLayer.thickness, layout.squareSize * Self.tileFill)
    )
    private lazy var dotMesh = MeshResource.generateCylinder(
        height: OverlayLayer.thickness,
        radius: layout.squareSize * Self.dotRadius
    )
    private lazy var ringLongMesh = MeshResource.generateBox(
        size: SIMD3<Float>(ringSpan, OverlayLayer.thickness, ringBarWidth)
    )
    private lazy var ringShortMesh = MeshResource.generateBox(
        size: SIMD3<Float>(ringBarWidth, OverlayLayer.thickness, ringSpan - 2 * ringBarWidth)
    )
    private lazy var dotMaterial = UnlitMaterial.overlay(BoardPalette.moveDot)
    private lazy var ringMaterial = UnlitMaterial.overlay(BoardPalette.captureRing)
    private lazy var arrowMaterial = UnlitMaterial.overlay(BoardPalette.hintArrow)
    private var tileMaterials: [MarkKind: UnlitMaterial] = [:]

    private var ringSpan: Float { layout.squareSize * Self.ringHalf * 2 }
    private var ringBarWidth: Float { layout.squareSize * Self.ringBar }

    init(layout: BoardLayout, assets: BoardAssets = .shared) {
        self.layout = layout
        self.assets = assets
        self.driver = MotionDriver(layout: layout)

        root.addChild(tapLayer)
        root.addChild(pieceLayer)
        root.addChild(indicatorLayer)
        buildTapBodies()

        Task {
            await assets.ready()
            self.attachBoardModel()
            self.assetsReady = true
            if let frame = self.latest {
                self.sync(frame)
            }
        }
    }

    static func pieceName(_ id: Int32) -> String {
        "\(piecePrefix)\(id)"
    }

    func apply(_ frame: BoardFrame) {
        latest = frame
        if assetsReady {
            sync(frame)
        }
    }

    /// Square under a tapped entity: a tile directly, or the square a piece currently stands on.
    func square(for entity: Entity) -> Int32? {
        var current: Entity? = entity
        while let candidate = current {
            let name = candidate.name
            if name.hasPrefix(Self.tilePrefix), let index = Int32(name.dropFirst(Self.tilePrefix.count)) {
                return index
            }
            if name.hasPrefix(Self.piecePrefix),
               let id = Int32(name.dropFirst(Self.piecePrefix.count)),
               let node = nodes[id] {
                return node.square
            }
            current = candidate.parent
        }
        return nil
    }

    // MARK: Setup

    private func attachBoardModel() {
        if let board = assets.makeBoard() {
            root.addChild(board)
        }
    }

    private func buildTapBodies() {
        let side = layout.squareSize
        let body = ShapeResource.generateBox(size: SIMD3<Float>(side, Self.tapDepth, side))
            .offsetBy(translation: SIMD3<Float>(0, -Self.tapDepth / 2, 0))

        for square in 0..<64 {
            let index = Int32(square)
            let tile = Entity()
            tile.name = "\(Self.tilePrefix)\(index)"
            tile.position = PieceNode.position(of: index, layout: layout)
            tile.components.set(CollisionComponent(shapes: [body]))
            tile.components.set(InputTargetComponent())
            tapLayer.addChild(tile)
        }
    }

    // MARK: Sync

    private func sync(_ frame: BoardFrame) {
        perspective = frame.perspective
        let yaw: Float = frame.perspective == .black ? .pi : 0
        root.orientation = simd_quatf(angle: yaw, axis: SIMD3<Float>(0, 1, 0))

        syncPieces(frame, animate: frame.animate && hasShown)
        syncIndicators(frame)
        hasShown = true
    }

    private func syncPieces(_ frame: BoardFrame, animate: Bool) {
        var seen = Set<Int32>()

        for piece in frame.pieces {
            seen.insert(piece.id)
            if let node = nodes[piece.id] {
                node.update(to: piece, animate: animate, assets: assets, driver: driver)
                continue
            }

            let node = PieceNode(frame: piece, layout: layout)
            node.show(kind: piece.kind, assets: assets)
            pieceLayer.addChild(node.entity)
            nodes[piece.id] = node
            if animate {
                driver.run(Motion(kind: .appear, entity: node.entity))
            }
        }

        let gone = nodes.keys.filter { !seen.contains($0) }
        for id in gone {
            nodes.removeValue(forKey: id)?.remove(animate: animate, driver: driver)
        }
    }

    private func syncIndicators(_ frame: BoardFrame) {
        for child in Array(indicatorLayer.children) {
            child.removeFromParent()
        }

        for mark in frame.marks {
            addTile(square: mark.square, kind: mark.kind)
        }
        for destination in frame.destinations {
            if destination.capture {
                addRing(square: destination.square)
            } else {
                addDot(square: destination.square)
            }
        }
        if frame.hintFrom >= 0 && frame.hintTo >= 0 {
            addArrow(from: frame.hintFrom, to: frame.hintTo)
        }
    }

    // MARK: Indicators

    private func place(_ entity: Entity, square: Int32, height: Float) {
        entity.position = SIMD3<Float>(layout.centerX(square: square), height, layout.centerZ(square: square))
        indicatorLayer.addChild(entity)
    }

    private func addTile(square: Int32, kind: MarkKind) {
        let material = tileMaterials[kind] ?? UnlitMaterial.overlay(BoardPalette.tile(kind))
        tileMaterials[kind] = material
        place(ModelEntity(mesh: tileMesh, materials: [material]), square: square, height: OverlayLayer.tile)
    }

    private func addDot(square: Int32) {
        place(ModelEntity(mesh: dotMesh, materials: [dotMaterial]), square: square, height: OverlayLayer.dot)
    }

    /// Four thin bars forming a square outline inside the tile.
    private func addRing(square: Int32) {
        let ring = Entity()
        let offset = ringSpan / 2 - ringBarWidth / 2

        let sides: [(Float, Float, MeshResource)] = [
            (0, offset, ringLongMesh),
            (0, -offset, ringLongMesh),
            (offset, 0, ringShortMesh),
            (-offset, 0, ringShortMesh),
        ]
        for (x, z, mesh) in sides {
            let bar = ModelEntity(mesh: mesh, materials: [ringMaterial])
            bar.position = SIMD3<Float>(x, 0, z)
            ring.addChild(bar)
        }
        place(ring, square: square, height: OverlayLayer.ring)
    }

    private func addArrow(from: Int32, to: Int32) {
        for bar in layout.arrow(from: from, to: to) {
            let mesh = MeshResource.generateBox(
                size: SIMD3<Float>(bar.length, OverlayLayer.thickness, layout.arrowBarWidth)
            )
            let entity = ModelEntity(mesh: mesh, materials: [arrowMaterial])
            entity.position = SIMD3<Float>(bar.centerX, OverlayLayer.arrow, bar.centerZ)
            entity.orientation = simd_quatf(
                angle: bar.yawDegrees * .pi / 180,
                axis: SIMD3<Float>(0, 1, 0)
            )
            indicatorLayer.addChild(entity)
        }
    }
}
