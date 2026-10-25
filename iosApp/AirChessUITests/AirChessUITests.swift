import XCTest

final class AirChessUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-AirChessSquareProbe"]
        app.launch()
    }

    func testHarnessBoard() throws {
        snapshot("01-board")
        tapSquare("e2")
        snapshot("02-e2-selected")
        tapSquare("e4")
        snapshot("03-e4-played")
    }

    private func tapSquare(_ name: String) {
        let element = app.otherElements["square-\(name)"]
        XCTAssertTrue(element.waitForExistence(timeout: 30), "square \(name) not published")
        let frame = element.frame
        let origin = app.coordinate(withNormalizedOffset: .zero)
        origin.withOffset(CGVector(dx: frame.midX, dy: frame.midY)).tap()
        Thread.sleep(forTimeInterval: 1)
    }

    private func snapshot(_ name: String) {
        Thread.sleep(forTimeInterval: 1.5)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
