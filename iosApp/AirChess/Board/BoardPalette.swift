import RealityKit
import Shared
import UIKit

/// Colors and layer heights of the board overlays; values mirror the Android renderer.
enum BoardPalette {
    static let moveDot = UIColor(hex: 0x2E7D32, alpha: 0.55)
    static let captureRing = UIColor(hex: 0xD32F2F, alpha: 0.85)
    static let hintArrow = UIColor(hex: 0xFFEB3B, alpha: 0.92)
    static let reticle = UIColor(white: 1, alpha: 0.9)

    static func tile(_ kind: MarkKind) -> UIColor {
        switch kind {
        case .selected: return UIColor(hex: 0x29B6F6, alpha: 0.60)
        case .check: return UIColor(hex: 0xE53935, alpha: 0.65)
        case .hint: return UIColor(hex: 0xFFEB3B, alpha: 0.45)
        case .lastMove: return UIColor(hex: 0xFFB300, alpha: 0.35)
        case .threatened: return UIColor(hex: 0xE53935, alpha: 0.30)
        default: return UIColor(hex: 0xE53935, alpha: 0.30)
        }
    }
}

/// Heights of the flat overlays above the board surface, 1 mm apart so nothing z-fights.
enum OverlayLayer {
    static let thickness: Float = 0.001
    static let tile: Float = 0.0005
    static let dot: Float = 0.0025
    static let ring: Float = 0.0035
    static let arrow: Float = 0.0050
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

extension UnlitMaterial {
    /// A flat color that alpha-blends with whatever is behind it.
    static func overlay(_ color: UIColor) -> UnlitMaterial {
        var alpha: CGFloat = 1
        color.getRed(nil, green: nil, blue: nil, alpha: &alpha)
        var material = UnlitMaterial(color: color.withAlphaComponent(1))
        material.blending = .transparent(opacity: .init(floatLiteral: Float(alpha)))
        return material
    }
}
