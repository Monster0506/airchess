import SwiftUI

/// Invisible accessibility elements over the board, one per square, for XCUITest to find.
/// It sits above the Compose host in the SwiftUI root because Compose hides native
/// accessibility children of embedded views. Never hit-testable: taps reach the real board.
struct SquareProbeOverlay: View {
    @ObservedObject private var probe = SquareProbe.shared

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(probe.rects.keys.sorted(), id: \.self) { square in
                if let rect = probe.rects[square] {
                    Color.clear
                        .frame(width: rect.width, height: rect.height)
                        .position(x: rect.midX, y: rect.midY)
                        .accessibilityElement()
                        .accessibilityIdentifier("square-\(Self.name(of: square))")
                        .accessibilityLabel(Self.name(of: square))
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    private static func name(of square: Int) -> String {
        let file = Character(UnicodeScalar(UInt8(97 + square % 8)))
        return "\(file)\(square / 8 + 1)"
    }
}
