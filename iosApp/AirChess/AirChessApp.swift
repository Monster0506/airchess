import SwiftUI

@main
struct AirChessApp: App {
    var body: some Scene {
        WindowGroup {
            ZStack {
                HarnessView()
                    .ignoresSafeArea()
                if SquareProbe.isEnabled {
                    SquareProbeOverlay()
                }
            }
        }
    }
}
