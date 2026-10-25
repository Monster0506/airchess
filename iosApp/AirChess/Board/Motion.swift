import QuartzCore
import RealityKit
import Shared
import simd

private let hiddenScale: Float = 0.001

/// One running animation of a piece entity: a slide, an appear or a vanish.
@MainActor
final class Motion {
    enum Kind {
        case slide
        case appear
        case vanish
    }

    let kind: Kind
    private let entity: Entity
    private let from: SIMD3<Float>
    private let to: SIMD3<Float>
    private let arcHeight: Float
    private let onFinish: () -> Void

    fileprivate var start: CFTimeInterval?
    private(set) var isDone = false

    init(
        kind: Kind,
        entity: Entity,
        from: SIMD3<Float> = .zero,
        to: SIMD3<Float> = .zero,
        arcHeight: Float = 0,
        onFinish: @escaping () -> Void = {}
    ) {
        self.kind = kind
        self.entity = entity
        self.from = from
        self.to = to
        self.arcHeight = arcHeight
        self.onFinish = onFinish

        if kind == .appear {
            entity.scale = SIMD3<Float>(repeating: hiddenScale)
        }
    }

    fileprivate func write(progress: Float, layout: BoardLayout) {
        let eased = layout.ease(progress: progress)
        switch kind {
        case .slide:
            var position = from + (to - from) * eased
            position.y += layout.arc(progress: progress, height: arcHeight)
            entity.position = position
        case .appear:
            entity.scale = SIMD3<Float>(repeating: max(eased, hiddenScale))
        case .vanish:
            entity.scale = SIMD3<Float>(repeating: max(1 - eased, hiddenScale))
        }
    }

    /// Jumps to the end state and runs the completion; safe to call more than once.
    func finish(layout: BoardLayout) {
        if isDone {
            return
        }

        isDone = true
        write(progress: 1, layout: layout)
        onFinish()
    }
}

/// Steps running motions once per display frame and stops its link when idle.
@MainActor
final class MotionDriver: NSObject {
    private let layout: BoardLayout
    private var running: [Motion] = []
    private var link: CADisplayLink?

    init(layout: BoardLayout) {
        self.layout = layout
    }

    func run(_ motion: Motion) {
        running.append(motion)
        if link == nil {
            let newLink = CADisplayLink(target: self, selector: #selector(step(_:)))
            newLink.add(to: .main, forMode: .common)
            link = newLink
        }
    }

    func finish(_ motion: Motion?) {
        motion?.finish(layout: layout)
    }

    @objc private func step(_ link: CADisplayLink) {
        let now = link.timestamp
        let duration = layout.slideSeconds

        for motion in running where !motion.isDone {
            let begin = motion.start ?? now
            motion.start = begin
            let progress = Float(min(max((now - begin) / duration, 0), 1))
            if progress >= 1 {
                motion.finish(layout: layout)
            } else {
                motion.write(progress: progress, layout: layout)
            }
        }

        running.removeAll { $0.isDone }
        if running.isEmpty {
            link.invalidate()
            self.link = nil
        }
    }
}
