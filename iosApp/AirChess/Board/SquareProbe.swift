import Combine
import CoreGraphics
import Foundation
import RealityKit
import Shared
import UIKit

/// Projected board squares published only for UI tests.
@MainActor
final class SquareProbe: ObservableObject {
    static let shared = SquareProbe()
    static let isEnabled = ProcessInfo.processInfo.arguments.contains("-AirChessSquareProbe")

    @Published private(set) var rects: [Int: CGRect] = [:]

    private static let cellShrink: CGFloat = 0.6

    private init() {}

    func publish(scene: BoardScene, view: ARView) {
        let viewport = view.bounds.size
        guard viewport.width > 0, viewport.height > 0 else { return }

        let origin = view.convert(.zero, to: view.window)
        let size = scene.layout.squareSize
        var result: [Int: CGRect] = [:]
        for square in 0..<64 {
            let index = Int32(square)
            let local = SIMD3<Float>(scene.layout.centerX(square: index), 0, scene.layout.centerZ(square: index))
            let edge = local + SIMD3<Float>(size, 0, 0)
            let worldCenter = scene.root.convert(position: local, to: nil)
            let worldEdge = scene.root.convert(position: edge, to: nil)
            guard let center = view.project(worldCenter), let side = view.project(worldEdge) else { continue }

            let extent = abs(side.x - center.x) * Self.cellShrink
            result[square] = CGRect(
                x: origin.x + center.x - extent / 2,
                y: origin.y + center.y - extent / 2,
                width: extent,
                height: extent
            )
        }
        rects = result
    }
}
