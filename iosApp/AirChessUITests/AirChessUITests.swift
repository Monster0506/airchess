import XCTest

final class AirChessUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-AirChessSquareProbe"]
        app.launch()
    }

    func testLocalGameBoardAndEngineHint() throws {
        XCTAssertTrue(app.staticTexts["Chess belongs anywhere."].waitForExistence(timeout: 15))
        snapshot("01-onboarding")
        tapControl("Continue")
        tapControl("Get started")
        XCTAssertTrue(app.staticTexts["Your move."].waitForExistence(timeout: 15))
        snapshot("02-home")

        tapControl("Play on the table")
        XCTAssertTrue(app.buttons["Play in 3D"].waitForExistence(timeout: 30))
        snapshot("03-ar-fallback")
        tapControl("Play in 3D")

        tapSquare("e2")
        snapshot("04-e2-selected")
        tapSquare("e4")
        XCTAssertTrue(app.staticTexts["Black to move"].waitForExistence(timeout: 15))
        snapshot("05-e4-played")

        tapControl("History")
        XCTAssertTrue(app.staticTexts["1. e4"].waitForExistence(timeout: 10))
        snapshot("06-history-e4")
        app.swipeDown()

        tapControl("Hint")
        let hint = app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Suggested move:'")).firstMatch
        XCTAssertTrue(hint.waitForExistence(timeout: 90), "Stockfish did not produce a hint")
        snapshot("07-hint")

        tapControl("Undo")
        tapControl("Reset")
        XCTAssertTrue(app.staticTexts["White to move"].waitForExistence(timeout: 15))
        snapshot("08-reset")
    }

    private func tapSquare(_ name: String) {
        let element = app.otherElements["square-\(name)"]
        XCTAssertTrue(element.waitForExistence(timeout: 30), "square \(name) was not projected")
        let frame = element.frame
        XCTAssertGreaterThan(frame.width, 0)
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX, dy: frame.midY))
            .tap()
    }

    private func tapControl(_ label: String) {
        let button = app.buttons[label]
        if button.waitForExistence(timeout: 5) {
            button.tap()
            return
        }

        let element = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", label))
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 15), "control \(label) was not available")
        element.tap()
    }

    private func snapshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

private extension XCUIElement {
    func tapIfPresent() {
        if exists && isHittable {
            tap()
        }
    }
}
