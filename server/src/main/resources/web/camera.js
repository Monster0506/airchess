export class CameraCall {
  constructor({ send, onState = () => {}, localVideo, remoteVideo, capture }) {
    this.send = send;
    this.onState = onState;
    this.localVideo = localVideo;
    this.remoteVideo = remoteVideo;
    this.capture = capture;
    this.online = false;
    this.config = null;
    this.configWaiter = null;
    this.generation = 0;
    this.side = null;
    this.enabled = false;
    this.peerReady = false;
    this.joinedRoom = false;
    this.pc = null;
    this.localStream = null;
    this.remoteStream = null;
    this.pendingIce = [];
    this.makingOffer = false;
    this.status = 'off';
  }

  setOnline(online) {
    if (!online) {
      this.joinedRoom = false;
      this.stop(false);
    }
    this.online = online;
  }

  joined(side) {
    if (this.enabled) this.stop(false);
    this.config = null;
    this.side = side;
    this.joinedRoom = true;
    this.peerReady = false;
    this.resetPeer();
  }

  setConfig(config) {
    this.config = config;
    this.configWaiter?.();
    this.configWaiter = null;
    if (config.relayError) {
      if (this.enabled) this.stop();
      this.publish('error', config.relayError);
      return;
    }
    if (this.pc) {
      try {
        this.pc.setConfiguration({ iceServers: config.iceServers || [] });
      } catch {
        this.stop();
        this.publish('error', 'View-sharing configuration could not be applied.');
      }
    }
  }

  async enable() {
    if (!this.online || !this.joinedRoom || this.enabled) return;
    if (this.config?.relayError) {
      this.publish('error', this.config.relayError);
      return;
    }
    if (!this.capture || !globalThis.RTCPeerConnection) {
      this.publish('error', 'View sharing is unavailable in this browser.');
      return;
    }
    const generation = ++this.generation;
    this.enabled = true;
    this.publish('starting', 'Starting view sharing…');
    let stream;
    try {
      stream = await this.capture();
      if (generation !== this.generation || !this.enabled) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      this.bindStream(stream, generation);
      if (generation !== this.generation || !this.enabled) return;
      if (!this.config) {
        await new Promise(resolve => { this.configWaiter = resolve; });
        if (generation !== this.generation || !this.enabled) return;
      }
      if (this.config?.relayError) throw new Error(this.config.relayError);
      this.ensurePeer();
      for (const track of this.localStream.getVideoTracks()) this.pc.addTrack(track, this.localStream);
      this.publish('waiting', 'Waiting for opponent view');
      this.send({ type: 'camera_signal', kind: 'READY', payload: '' });
      await this.maybeOffer();
    } catch (error) {
      if (generation !== this.generation) {
        stream?.getTracks().forEach(track => track.stop());
        return;
      }
      this.stop(false);
      this.publish('error', error?.message || 'View sharing could not be started.');
    }
  }

  bindStream(stream, generation) {
    this.localStream = stream;
    for (const track of stream.getVideoTracks()) {
      track.contentHint = 'detail';
      track.onended = () => {
        if (generation !== this.generation || !this.enabled) { return; }
        this.stop();
        this.publish('error', 'View sharing stopped. Share your view to reconnect.');
      };
    }
    this.localVideo.srcObject = stream;
    this.localVideo.play().catch(() => {});
  }

  async replaceStream(stream) {
    const generation = this.generation, pc = this.pc;
    if (!this.enabled) {
      stream.getTracks().forEach(track => track.stop());
      return;
    }
    try {
      const sender = pc?.getSenders().find(item => item.track?.kind === 'video');
      if (sender) { await sender.replaceTrack(stream.getVideoTracks()[0]); }
      if (generation !== this.generation || !this.enabled || this.pc !== pc) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      const previous = this.localStream;
      this.bindStream(stream, generation);
      previous?.getTracks().forEach(track => { track.onended = null; track.stop(); });
    } catch {
      stream.getTracks().forEach(track => track.stop());
      if (generation !== this.generation || !this.enabled) { return; }
      this.stop();
      this.publish('error', 'Could not switch the shared view.');
    }
  }

  async signal(message) {
    if (message.kind === 'STOP') {
      this.stop(false);
      return;
    }
    if (message.kind === 'READY') {
      this.peerReady = true;
      if (this.enabled) this.publish('waiting', 'Waiting for opponent view');
      await this.maybeOffer();
      return;
    }
    if (!this.enabled) return;
    const generation = this.generation;
    let pc;
    try {
      this.ensurePeer();
      pc = this.pc;
      const current = () => generation === this.generation && this.enabled && this.pc === pc;
      if (message.kind === 'OFFER') {
        await pc.setRemoteDescription({ type: 'offer', sdp: message.payload });
        if (!current()) return;
        await this.flushIce(pc);
        if (!current()) return;
        const answer = await pc.createAnswer();
        if (!current()) return;
        await pc.setLocalDescription(answer);
        if (current()) this.send({ type: 'camera_signal', kind: 'ANSWER', payload: pc.localDescription.sdp });
      } else if (message.kind === 'ANSWER') {
        await pc.setRemoteDescription({ type: 'answer', sdp: message.payload });
        if (current()) await this.flushIce(pc);
      } else if (message.kind === 'ICE') {
        const candidate = JSON.parse(message.payload);
        if (pc.remoteDescription) await pc.addIceCandidate(candidate);
        else this.pendingIce.push(candidate);
      }
    } catch {
      if (generation !== this.generation || !this.enabled || (pc && this.pc !== pc)) return;
      this.stop();
      this.publish('error', 'View connection failed.');
    }
  }

  async maybeOffer() {
    if (!this.enabled || !this.peerReady || !this.pc || this.side !== 'w' || this.pc.localDescription || this.makingOffer) return;
    const generation = this.generation;
    const pc = this.pc;
    this.makingOffer = true;
    try {
      const offer = await pc.createOffer();
      if (generation !== this.generation || !this.enabled || this.pc !== pc) return;
      await pc.setLocalDescription(offer);
      if (generation === this.generation && this.enabled && this.pc === pc) {
        this.send({ type: 'camera_signal', kind: 'OFFER', payload: pc.localDescription.sdp });
      }
    } catch {
      if (generation !== this.generation || !this.enabled || this.pc !== pc) return;
      this.stop();
      this.publish('error', 'Could not start the view connection.');
    } finally {
      if (this.pc === pc) this.makingOffer = false;
    }
  }

  ensurePeer() {
    if (this.pc) return;
    const pc = new RTCPeerConnection({ iceServers: this.config?.iceServers || [] });
    this.pc = pc;
    pc.onicecandidate = event => {
      if (this.pc === pc && this.enabled && event.candidate) {
        this.send({ type: 'camera_signal', kind: 'ICE', payload: JSON.stringify(event.candidate.toJSON()) });
      }
    };
    pc.ontrack = event => {
      if (this.pc !== pc || !this.enabled) return;
      const stream = event.streams[0];
      if (!stream) return;
      this.remoteStream = stream;
      this.remoteVideo.srcObject = stream;
      this.remoteVideo.play().catch(() => {});
      this.publish(pc.connectionState === 'connected' ? 'connected' : 'connecting', pc.connectionState === 'connected' ? 'Views connected' : 'Connecting views');
    };
    pc.onconnectionstatechange = () => {
      if (pc !== this.pc) return;
      if (pc.connectionState === 'connected') this.publish('connected', 'Views connected');
      else if (['failed', 'disconnected'].includes(pc.connectionState)) {
        this.stop();
        this.publish('error', 'View connection lost.');
      }
    };
  }

  async flushIce(pc = this.pc) {
    for (const candidate of this.pendingIce.splice(0)) {
      await pc.addIceCandidate(candidate);
      if (pc !== this.pc) return;
    }
  }

  stop(signal = true) {
    const shouldSignal = signal && this.enabled;
    this.generation++;
    this.enabled = false;
    this.configWaiter?.();
    this.configWaiter = null;
    if (shouldSignal) this.send({ type: 'camera_signal', kind: 'STOP', payload: '' });
    this.peerReady = false;
    this.disposeMedia();
    this.resetPeer();
    this.publish('off', 'off');
  }

  disposeMedia() {
    this.localStream?.getTracks().forEach(track => { track.onended = null; track.stop(); });
    this.localStream = null;
    this.localVideo.srcObject = null;
    this.remoteVideo.srcObject = null;
    this.remoteStream = null;
  }

  resetPeer() {
    if (this.pc) {
      this.pc.onicecandidate = null;
      this.pc.ontrack = null;
      this.pc.onconnectionstatechange = null;
      this.pc.close();
    }
    this.pc = null;
    this.pendingIce.length = 0;
    this.makingOffer = false;
  }

  publish(state, message) {
    this.status = state;
    this.onState(state, message);
  }
}
