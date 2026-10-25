import RealityKit
import Shared
import simd

/// One piece on the board: a container entity holding the model, a tap body and the motion state.
@MainActor
final class PieceNode {
    let id: Int32
    let entity = Entity()
    let side: PieceSide

    private(set) var kind: PieceKind
    private(set) var square: Int32

    private let layout: BoardLayout
    private var model: Entity?
    private var motion: Motion?

    private static let bodyWidthFactor: Float = 0.6

    init(frame: PieceFrame, layout: BoardLayout) {
        self.id = frame.id
        self.side = frame.side
        self.kind = frame.kind
        self.square = frame.square
        self.layout = layout

        entity.name = BoardScene.pieceName(frame.id)
        entity.position = Self.position(of: frame.square, layout: layout)
    }

    static func position(of square: Int32, layout: BoardLayout) -> SIMD3<Float> {
        SIMD3<Float>(layout.centerX(square: square), 0, layout.centerZ(square: square))
    }

    /// Replaces the displayed model; also resizes the tap body to the piece's height.
    func show(kind: PieceKind, assets: BoardAssets) {
        self.kind = kind
        model?.removeFromParent()

        if let fresh = assets.makePiece(kind: kind, side: side) {
            entity.addChild(fresh)
            model = fresh
        }

        let height = layout.pieceHeight(kind: kind)
        let width = layout.squareSize * Self.bodyWidthFactor
        let body = ShapeResource.generateBox(size: SIMD3<Float>(width, height, width))
            .offsetBy(translation: SIMD3<Float>(0, height / 2, 0))
        entity.components.set(CollisionComponent(shapes: [body]))
        entity.components.set(InputTargetComponent())
    }

    /// Moves to the frame's square, sliding when [animate]; a new kind shows once the slide ends.
    func update(to frame: PieceFrame, animate: Bool, assets: BoardAssets, driver: MotionDriver) {
        driver.finish(motion)
        motion = nil

        let moved = frame.square != square
        let promoted = frame.kind != kind
        let from = entity.position
        let to = Self.position(of: frame.square, layout: layout)
        let arc: Float = kind == .knight ? layout.knightArcHeight : 0
        square = frame.square

        if !moved {
            if promoted {
                show(kind: frame.kind, assets: assets)
            }
            return
        }

        if !animate {
            entity.position = to
            if promoted {
                show(kind: frame.kind, assets: assets)
            }
            return
        }

        let next = Motion(kind: .slide, entity: entity, from: from, to: to, arcHeight: arc) { [weak self] in
            guard let self, promoted else {
                return
            }
            self.show(kind: frame.kind, assets: assets)
        }
        motion = next
        driver.run(next)
    }

    /// Takes the piece off the board; it stops catching taps at once.
    func remove(animate: Bool, driver: MotionDriver) {
        driver.finish(motion)
        motion = nil
        entity.components.remove(CollisionComponent.self)
        entity.components.remove(InputTargetComponent.self)

        let host = entity
        if !animate {
            host.removeFromParent()
            return
        }

        let next = Motion(kind: .vanish, entity: host) {
            host.removeFromParent()
        }
        motion = next
        driver.run(next)
    }
}
