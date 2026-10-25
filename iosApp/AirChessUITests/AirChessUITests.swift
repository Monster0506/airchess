import XCTest

final class AirChessUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["-AirChessSquareProbe", "-AirChessUITestReset"]
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
    func testOnlineSharingSmoke() throws {
        XCTAssertTrue(app.staticTexts["Chess belongs anywhere."].waitForExistence(timeout: 15))
        tapControl("Continue")
        tapControl("Get started")
        XCTAssertTrue(app.staticTexts["Your move."].waitForExistence(timeout: 15))
        tapControl("Play a friend online")
        tapControl("White")
        tapControl("Create game")

        let code = app.staticTexts.matching(
            NSPredicate(format: "label MATCHES %@", "^[A-HJ-NP-Z2-9]{6}$")
        ).firstMatch
        XCTAssertTrue(code.waitForExistence(timeout: 60), "server did not create a room")
        let roomCode = code.label
        let roomFile = try smokeFile("AIRCHESS_SMOKE_ROOM_FILE")
        try roomCode.write(to: roomFile, atomically: true, encoding: .utf8)

        XCTAssertTrue(app.buttons["Play in 3D"].waitForExistence(timeout: 120), "browser peer did not join")
        tapControl("Play in 3D")
        tapControl("Share view")
        XCTAssertTrue(app.staticTexts["Views connected"].waitForExistence(timeout: 90))
        let initialFrame = waitForRemoteFrameEvidence(timeout: 90)
        XCTAssertNotNil(initialFrame, "iOS did not decode a nonempty browser video frame")
        XCTAssertGreaterThan(initialFrame?.frames ?? 0, 0)
        XCTAssertGreaterThan(initialFrame?.width ?? 0, 0)
        XCTAssertGreaterThan(initialFrame?.height ?? 0, 0)
        XCTAssertGreaterThan(initialFrame?.lumaMax ?? 0, initialFrame?.lumaMin ?? 0)
        XCTAssertFalse(initialFrame?.checksum.isEmpty ?? true)
        snapshot("09-sharing-connected")

        let ready = waitForSmokePhase("ready-for-native-e4", timeout: 120)
        XCTAssertNotNil(ready, "browser peer did not verify initial native video")
        XCTAssertEqual(ready?["roomCode"] as? String, roomCode)
        tapSquare("e2")
        tapSquare("e4")
        XCTAssertTrue(app.staticTexts["Black to move"].waitForExistence(timeout: 15))
        snapshot("10-online-e4")

        let remoteFrame = waitForSmokePhase("remote-frame-verified", timeout: 120)
        XCTAssertNotNil(remoteFrame, "browser did not verify remote native frames")
        XCTAssertEqual(remoteFrame?["remoteFrameVerified"] as? Bool, true)
        XCTAssertEqual(remoteFrame?["moves"] as? [String], ["e2e4"])

        XCTAssertTrue(app.staticTexts["White to move"].waitForExistence(timeout: 90), "browser peer did not play e7e5")
        XCTAssertTrue(app.staticTexts["1. e4 e5"].waitForExistence(timeout: 15))
        let updatedFrame = waitForRemoteFrameEvidence(
            timeout: 60,
            differentFrom: initialFrame?.checksum
        )
        XCTAssertNotNil(updatedFrame, "native renderer did not receive the changed browser board")
        XCTAssertGreaterThan(updatedFrame?.frames ?? 0, initialFrame?.frames ?? 0)
        XCTAssertNotEqual(updatedFrame?.checksum, initialFrame?.checksum)
        snapshot("11-online-e4-e5")
        let browserMoved = waitForSmokePhase("awaiting-native-g1f3", timeout: 30)
        XCTAssertNotNil(browserMoved, "browser peer did not publish e7e5")
        XCTAssertEqual(browserMoved?["moves"] as? [String], ["e2e4", "e7e5"])
        tapSquare("g1")
        tapSquare("f3")


        let complete = waitForSmokePhase("complete", timeout: 120)
        XCTAssertNotNil(complete, "browser peer did not verify native g1f3")
        XCTAssertEqual(complete?["moves"] as? [String], ["e2e4", "e7e5", "g1f3"])
        snapshot("12-online-g1f3")
    }

    private func smokeFile(_ key: String) throws -> URL {
        guard let path = ProcessInfo.processInfo.environment[key] else {
            throw XCTSkip("Missing workflow-provided \(key)")
        }
        return URL(fileURLWithPath: path)
    }

    private func waitForSmokePhase(_ phase: String, timeout: TimeInterval) -> [String: Any]? {
        guard let path = ProcessInfo.processInfo.environment["AIRCHESS_SMOKE_RESULT_FILE"] else {
            XCTFail("Missing AIRCHESS_SMOKE_RESULT_FILE")
            return nil
        }
        let url = URL(fileURLWithPath: path)
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let data = try? Data(contentsOf: url),
               let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               result["phase"] as? String == phase {
                return result
            }
            Thread.sleep(forTimeInterval: 0.25)
        }
        return nil
    }


    private func waitForRemoteFrameEvidence(
        timeout: TimeInterval,
        differentFrom checksum: String? = nil
    ) -> RemoteFrameEvidence? {
        let element = app.descendants(matching: .any)
            .matching(identifier: "iosRemoteFrameEvidence")
            .firstMatch
        let pattern = #"frames=(\d+);\s*size=(\d+)x(\d+);\s*luma=(\d+)-(\d+);\s*checksum=([0-9a-fA-F]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let deadline = Date().addingTimeInterval(timeout)

        while Date() < deadline {
            let value = element.value as? String ?? ""
            let range = NSRange(value.startIndex..., in: value)
            if let match = regex.firstMatch(in: value, range: range),
               let frames = Self.number(match, group: 1, value: value),
               let width = Self.number(match, group: 2, value: value),
               let height = Self.number(match, group: 3, value: value),
               let lumaMin = Self.number(match, group: 4, value: value),
               let lumaMax = Self.number(match, group: 5, value: value),
               let checksumRange = Range(match.range(at: 6), in: value) {
                let evidence = RemoteFrameEvidence(
                    frames: frames,
                    width: width,
                    height: height,
                    lumaMin: lumaMin,
                    lumaMax: lumaMax,
                    checksum: String(value[checksumRange])
                )
                if evidence.frames > 0,
                   evidence.lumaMax > evidence.lumaMin,
                   checksum == nil || evidence.checksum != checksum {
                    return evidence
                }
            }
            Thread.sleep(forTimeInterval: 0.25)
        }
        return nil
    }

    private static func number(_ match: NSTextCheckingResult, group: Int, value: String) -> Int? {
        guard let range = Range(match.range(at: group), in: value) else { return nil }
        return Int(value[range])
    }

    private func tapSquare(_ name: String) {
        let element = app.otherElements["square-\(name)"]
        XCTAssertTrue(element.waitForExistence(timeout: 30), "square \(name) was not projected")
        let frame = element.frame
        XCTAssertGreaterThan(frame.width, 0)
        app.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: frame.midX, dy: frame.midY))
            .tap()
        Thread.sleep(forTimeInterval: 0.5)
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
private struct RemoteFrameEvidence {
    let frames: Int
    let width: Int
    let height: Int
    let lumaMin: Int
    let lumaMax: Int
    let checksum: String
}

