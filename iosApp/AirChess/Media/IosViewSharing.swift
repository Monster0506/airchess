import Combine
import Foundation
import RealityKit
import Shared
import UIKit
import WebRTC

@MainActor
final class IosViewSharingBridgeImpl: NSObject, IosViewSharingBridge, ObservableObject {
    @Published private(set) var isActive = false
    @Published private(set) var statusText = "Camera off"
    weak var renderView: ARView? {
        didSet {
            session?.capture.renderView = renderView
            if renderView == nil { session?.renderLost() }
        }
    }

    private var session: IosViewSharingSessionImpl?

    var isReady: Bool { renderView != nil }

    func open() -> IosViewSharingSessionBridge {
        if let session { return session }
        let next = makeSession()
        session = next
        return next
    }

    func makeView(onSwap: @escaping () -> Void) -> UIView {
        let current = session ?? makeSession()
        session = current
        return current.makeVideoView(onSwap: onSwap)
    }

    private func makeSession() -> IosViewSharingSessionImpl {
        let next = IosViewSharingSessionImpl()
        next.capture.renderView = renderView
        next.onStateChanged = { [weak self] active, _, message in
            self?.isActive = active
            self?.statusText = message
        }
        return next
    }

    func showOpponent(opponent: Bool, view: UIView) {
        guard let view = view as? IosSharingVideoView else { return }
        session?.showOpponent(opponent, in: view)
    }
}

@MainActor
final class IosViewSharingSessionImpl: NSObject, IosViewSharingSessionBridge {
    fileprivate let capture = BoardFrameCapturer()
    private static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory(
            encoderFactory: RTCDefaultVideoEncoderFactory(),
            decoderFactory: RTCDefaultVideoDecoderFactory()
        )
    }()
    private var source: RTCVideoSource?
    private var localTrack: RTCVideoTrack?
    private var remoteTrack: RTCVideoTrack?
    private var peer: RTCPeerConnection?
    private var views: [IosSharingVideoView] = []
    private var pendingCandidates: [RTCIceCandidate] = []
    private var remoteDescriptionSet = false
    private var remoteReady = false
    private var active = false
    private var closed = false
    private var generation = 0
    private var localSide: Side = .white
    private var signal: ((CameraSignalKind, String) -> Void)?
    private var status: ((CameraPhase, String) -> Void)?
    private var iceServers: [RTCIceServer] = []
    var onStateChanged: ((Bool, CameraPhase, String) -> Void)?


    func start(
        servers: [IceServer],
        side: Side,
        signal: @escaping (CameraSignalKind, String) -> Void,
        status: @escaping (CameraPhase, String) -> Void
    ) {
        precondition(!closed)
        stopCapture()
        iceServers = servers.map {
            RTCIceServer(urlStrings: $0.urls, username: $0.username, credential: $0.credential)
        }
        localSide = side
        self.signal = signal
        self.status = status
        active = true
        publish(.starting, "Starting view sharing")
        guard capture.renderView != nil else {
            fail("Shared view is unavailable")
            return
        }
        let videoSource = Self.factory.videoSource()
        source = videoSource
        capture.source = videoSource
        let track = Self.factory.videoTrack(with: videoSource, trackId: "airchess-view")
        localTrack = track
        views.forEach { $0.setLocalTrack(track) }
        capture.start { [weak self] message in self?.fail(message) }
        publish(.waiting, "Waiting for opponent view")
        signal(.ready, "")
    }

    func receive(kind: CameraSignalKind, payload: String) {
        if kind == .stop {
            stopCapture()
            return
        }
        guard active else { return }
        do {
            switch kind {
            case .ready:
                remoteReady = true
                publish(.connecting, "Connecting views")
                try createPeer()
            case .offer:
                try setRemote(type: .offer, sdp: payload)
            case .answer:
                try setRemote(type: .answer, sdp: payload)
            case .ice:
                let data = Data(payload.utf8)
                let value = try JSONDecoder().decode(CandidatePayload.self, from: data)
                let candidate = RTCIceCandidate(
                    sdp: value.candidate,
                    sdpMLineIndex: Int32(value.sdpMLineIndex),
                    sdpMid: value.sdpMid
                )
                if remoteDescriptionSet { peer?.add(candidate, completionHandler: { _ in }) }
                else { pendingCandidates.append(candidate) }
            case .stop:
                break
            @unknown default:
                break
            }
        } catch {
            fail("View connection failed: \(error.localizedDescription)")
        }
    }

    func stop() {
        if active { signal?(.stop, "") }
        stopCapture()
    }

    func close() {
        guard !closed else { return }
        stop()
        views.forEach { $0.release() }
        views.removeAll()
        closed = true
        status = nil
        signal = nil
    }

    func makeVideoView(onSwap: @escaping () -> Void) -> IosSharingVideoView {
        let view = IosSharingVideoView(onSwap: onSwap)
        views.append(view)
        view.setRemoteTrack(remoteTrack)
        return view
    }

    func showOpponent(_ opponent: Bool, in view: IosSharingVideoView) {
        view.showOpponent(opponent)
        view.setLocalTrack(opponent ? localTrack : nil)
    }

    private func createPeer() throws {
        guard active, remoteReady, peer == nil else { return }
        let config = RTCConfiguration()
        config.iceServers = iceServers
        config.sdpSemantics = .unifiedPlan
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let connection = Self.factory.peerConnection(with: config, constraints: constraints, delegate: self) else {
            throw SharingError.connectionUnavailable
        }
        peer = connection
        if let localTrack { connection.add(localTrack, streamIds: ["airchess-view"]) }
        if localSide == .white { createDescription(.offer) }
    }

    private func setRemote(type: RTCSdpType, sdp: String) throws {
        try createPeer()
        guard let peer else { return }
        let description = RTCSessionDescription(type: type, sdp: sdp)
        peer.setRemoteDescription(description) { [weak self, weak peer] error in
            guard let self, let peer else { return }
            Task { @MainActor in
                guard self.active else { return }
                if let error { self.fail("View connection failed: \(error.localizedDescription)"); return }
                self.remoteDescriptionSet = true
                self.pendingCandidates.forEach { peer.add($0, completionHandler: { _ in }) }
                self.pendingCandidates.removeAll()
                if type == .offer { self.createDescription(.answer) }
            }
        }
    }

    private func createDescription(_ type: RTCSdpType) {
        guard let peer else { return }
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        let completion: (RTCSessionDescription?, Error?) -> Void = { [weak self, weak peer] description, error in
            guard let self, let peer else { return }
            Task { @MainActor in
                guard self.active else { return }
                guard let description, error == nil else {
                    self.fail("View connection failed: \(error?.localizedDescription ?? "Could not create session description")")
                    return
                }
                peer.setLocalDescription(description) { [weak self] error in
                    guard let self else { return }
                    Task { @MainActor in
                        guard self.active else { return }
                        if let error { self.fail("View connection failed: \(error.localizedDescription)"); return }
                        self.signal?(type == .offer ? .offer : .answer, description.sdp)
                    }
                }
            }
        }
        if type == .offer { peer.offer(for: constraints, completionHandler: completion) }
        else { peer.answer(for: constraints, completionHandler: completion) }
    }

    private func stopCapture() {
        generation += 1
        active = false
        remoteReady = false
        remoteDescriptionSet = false
        pendingCandidates.removeAll()
        capture.stop()
        capture.source = nil
        setRemoteTrack(nil)
        let connection = peer
        peer = nil
        connection?.close()
        views.forEach { $0.setLocalTrack(nil) }
        localTrack = nil
        source = nil
        signal = nil
        publish(.off, "Camera off")
    }

    private func setRemoteTrack(_ track: RTCVideoTrack?) {
        guard remoteTrack !== track else { return }
        remoteTrack = track
        views.forEach { $0.setRemoteTrack(track) }
    }

    private func fail(_ message: String) {
        stop()
        publish(.error, message)
    }
    fileprivate func renderLost() {
        if active { fail("Shared view is unavailable") }
    }

    private func publish(_ phase: CameraPhase, _ message: String) {
        onStateChanged?(active, phase, message)
        status?(phase, message)
    }

    fileprivate func peerFailed(_ message: String, generation epoch: Int) {
        guard active, generation == epoch else { return }
        fail(message)
    }

    fileprivate func peerConnected(generation epoch: Int) {
        guard active, generation == epoch else { return }
        publish(.connected, "Views connected")
    }

    fileprivate func candidate(_ candidate: RTCIceCandidate, generation epoch: Int) {
        guard active, generation == epoch else { return }
        let value: [String: Any] = [
            "candidate": candidate.sdp,
            "sdpMid": candidate.sdpMid as Any? ?? NSNull(),
            "sdpMLineIndex": Int(candidate.sdpMLineIndex)
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: value), let json = String(data: data, encoding: .utf8) else { return }
        signal?(.ice, json)
    }

    fileprivate func didReceive(_ track: RTCVideoTrack) {
        setRemoteTrack(track)
    }
}

private struct CandidatePayload: Codable {
    let candidate: String
    let sdpMid: String?
    let sdpMLineIndex: Int
}

private enum SharingError: Error {
    case connectionUnavailable
}

private final class BoardFrameCapturer: NSObject {
    weak var renderView: ARView?
    weak var source: RTCVideoSource? {
        didSet { videoCapturer = source.map { RTCVideoCapturer(delegate: $0) } }
    }
    private var videoCapturer: RTCVideoCapturer?
    private var timer: Timer?
    private var onFailure: ((String) -> Void)?
    private var capturing = false
    private var snapshotInFlight = false
    private var lastFrame = CACurrentMediaTime()
    private static let interval: TimeInterval = 1.0 / 8.0

    func start(onFailure: @escaping (String) -> Void) {
        self.onFailure = onFailure
        capturing = true
        NotificationCenter.default.addObserver(self, selector: #selector(background), name: UIApplication.didEnterBackgroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.willEnterForegroundNotification, object: nil)
        foreground()
    }

    func stop() {
        capturing = false
        timer?.invalidate()
        timer = nil
        NotificationCenter.default.removeObserver(self)
        onFailure = nil
    }

    @objc private func background() {
        timer?.invalidate()
        timer = nil
    }

    @objc private func foreground() {
        guard capturing, timer == nil else { return }
        timer = Timer.scheduledTimer(withTimeInterval: Self.interval, repeats: true) { [weak self] _ in
            self?.snapshot()
        }
        timer?.fire()
    }

    private func snapshot() {
        guard capturing, !snapshotInFlight, let renderView else { return }
        snapshotInFlight = true
        renderView.snapshot(saveToHDR: false) { [weak self] image in
            guard let self else { return }
            self.snapshotInFlight = false
            guard self.capturing, let image, let cgImage = image.cgImage else {
                if self.capturing { self.onFailure?("Shared view snapshot failed") }
                return
            }
            let now = CACurrentMediaTime()
            guard now - self.lastFrame >= Self.interval else { return }
            self.lastFrame = now
            self.send(cgImage)
        }
    }

    private func send(_ image: CGImage) {
        guard let source, let videoCapturer else { return }
        var buffer: CVPixelBuffer?
        let attributes: [String: Any] = [
            kCVPixelBufferCGImageCompatibilityKey as String: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]
        let result = CVPixelBufferCreate(kCFAllocatorDefault, image.width, image.height, kCVPixelFormatType_32ARGB, attributes as CFDictionary, &buffer)
        guard result == kCVReturnSuccess, let buffer else { return }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let context = CGContext(
            data: CVPixelBufferGetBaseAddress(buffer),
            width: image.width,
            height: image.height,
            bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue
        ) else { return }
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        let frame = RTCVideoFrame(
            buffer: RTCCVPixelBuffer(pixelBuffer: buffer),
            rotation: ._0,
            timeStampNs: Int64(CACurrentMediaTime() * 1_000_000_000)
        )
        source.capturer(videoCapturer, didCapture: frame)
    }
}


final class IosSharingVideoView: UIView {
    fileprivate let remoteView = RTCMTLVideoView(frame: .zero)
    fileprivate let localView = RTCMTLVideoView(frame: .zero)
    private let onSwap: () -> Void
    private var showingOpponent = false
    private var localTrack: RTCVideoTrack?
    private var remoteTrack: RTCVideoTrack?

    init(onSwap: @escaping () -> Void) {
        self.onSwap = onSwap
        super.init(frame: .zero)
        clipsToBounds = true
        remoteView.videoContentMode = .scaleAspectFit
        remoteView.accessibilityLabel = "Opponent view"
        localView.videoContentMode = .scaleAspectFill
        addSubview(remoteView)
        addSubview(localView)
        let tap = UITapGestureRecognizer(target: self, action: #selector(swap))
        localView.addGestureRecognizer(tap)
        localView.isUserInteractionEnabled = true
        localView.accessibilityLabel = "Return to your view"
    }

    required init?(coder: NSCoder) { nil }

    fileprivate func setLocalTrack(_ track: RTCVideoTrack?) {
        localTrack?.remove(localView)
        localTrack = track
        track?.add(localView)
    }

    fileprivate func setRemoteTrack(_ track: RTCVideoTrack?) {
        remoteTrack?.remove(remoteView)
        remoteTrack = track
        track?.add(remoteView)
    }

    func showOpponent(_ opponent: Bool) {
        showingOpponent = opponent
        localView.isHidden = !opponent
        setNeedsLayout()
    }

    fileprivate func release() {
        setRemoteTrack(nil)
        setLocalTrack(nil)
        removeFromSuperview()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        remoteView.frame = bounds
        guard showingOpponent else { return }
        let size = CGSize(width: 112, height: 154)
        localView.frame = CGRect(x: bounds.maxX - size.width - 14, y: bounds.minY + 42, width: size.width, height: size.height)
        localView.layer.cornerRadius = 8
        localView.clipsToBounds = true
    }

    @objc private func swap() { onSwap() }
}

extension IosViewSharingSessionImpl: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        let epoch = generation
        switch newState {
        case .connected, .completed: Task { @MainActor in peerConnected(generation: epoch) }
        case .failed, .disconnected: Task { @MainActor in peerFailed("View connection lost", generation: epoch) }
        default: break
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        let epoch = generation
        Task { @MainActor in self.candidate(candidate, generation: epoch) }
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) { dataChannel.close() }
    func peerConnection(_ peerConnection: RTCPeerConnection, didStartReceivingOn transceiver: RTCRtpTransceiver) {
        if let track = transceiver.receiver.track as? RTCVideoTrack { Task { @MainActor in didReceive(track) } }
    }
}
