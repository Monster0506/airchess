import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { access, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../..');
const port = Number(process.env.PORT || 18080);
const serverUrl = new URL(process.env.AIRCHESS_SERVER_URL || `http://127.0.0.1:${port}/play`);
const roomFile = process.env.AIRCHESS_SMOKE_ROOM_FILE;
const resultFile = resolve(process.env.AIRCHESS_SMOKE_RESULT_FILE || join(root, 'build/ios-smoke/browser-peer.json'));
const screenshotFile = resolve(process.env.AIRCHESS_SMOKE_SCREENSHOT || join(root, 'build/ios-smoke/browser-peer.png'));
const timeoutMs = Number(process.env.AIRCHESS_SMOKE_TIMEOUT_MS || 180_000);
const serverBin = resolve(process.env.AIRCHESS_SERVER_BIN || join(root, `server/build/install/server/bin/server${process.platform === 'win32' ? '.bat' : ''}`));
let serverProcess;
let page;
let roomCode;
let phase = 'starting';
let frameProof;
let peerStats;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function fileExists(path) {
  try { await access(path); return true; }
  catch { return false; }
}

async function atomicJson(path, data) {
  await mkdir(dirname(path), { recursive: true });
  const temp = `${path}.${process.pid}.tmp`;
  await writeFile(temp, `${JSON.stringify(data, null, 2)}\n`);
  await rename(temp, path);
}
function gameUrl(code) {
  const url = new URL(serverUrl);
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/`;
  if (code) url.searchParams.set('code', code);
  return url.href;
}

async function enableSharing(page) {
  await page.locator('#cameraToggle').click();
  await page.waitForFunction(() => document.querySelector('#cameraToggle')?.textContent.includes('Stop sharing'), null, { timeout: timeoutMs });
}

async function playMove(page, from, to) {
  await page.locator(`[data-square="${from}"]`).click();
  await page.locator(`[data-square="${to}"]`).click();
}

async function readRoomCode() {
  if (process.env.AIRCHESS_SMOKE_ROOM_CODE) return process.env.AIRCHESS_SMOKE_ROOM_CODE.trim().toUpperCase();
  assert(roomFile, 'Set AIRCHESS_SMOKE_ROOM_FILE or AIRCHESS_SMOKE_ROOM_CODE.');
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    if (await fileExists(roomFile)) {
      const raw = (await readFile(roomFile, 'utf8')).trim();
      try {
        const parsed = JSON.parse(raw);
        const code = typeof parsed === 'string' ? parsed : parsed.code;
        if (typeof code === 'string' && code.trim()) return code.trim().toUpperCase();
      } catch {
        if (raw) return raw.toUpperCase();
      }
    }
    await delay(250);
  }
  throw new Error(`Timed out waiting for native room code file: ${roomFile}`);
}

async function startServer() {
  if (process.env.AIRCHESS_SERVER_URL) return;
  assert(await fileExists(serverBin), `Server installDist binary not found: ${serverBin}; run ./gradlew :server:installDist first.`);
  serverProcess = spawn(serverBin, [], {
    cwd: root,
    env: { ...process.env, PORT: String(port) },
    stdio: 'inherit',
    shell: process.platform === 'win32',
  });
  serverProcess.on('exit', code => {
    if (code !== null && code !== 0 && phase !== 'complete') {
      console.error(`AirChess server exited with ${code}`);
    }
  });
  const end = Date.now() + 30_000;
  while (Date.now() < end) {
    try {
      const response = await fetch(new URL('/', serverUrl));
      if (response.ok) return;
    } catch {}
    if (serverProcess.exitCode !== null) throw new Error(`AirChess server exited with ${serverProcess.exitCode}`);
    await delay(250);
  }
  throw new Error('Timed out waiting for the local AirChess server.');
}

async function waitForBrowserPeer(page) {
  await page.waitForFunction(() => {
    const video = document.querySelector('#stageRemoteVideo');
    return video?.srcObject && video.readyState >= 2 && video.videoWidth >= 100 && video.videoHeight >= 100 && video.currentTime > 0;
  }, null, { timeout: timeoutMs });
}

async function readPeerStats(page) {
  return page.evaluate(async () => {
    const peers = window.__airChessPeerConnections || [];
    const stats = await Promise.all(peers.map(peer => peer.getStats()));
    const inbound = stats.flatMap(report => [...report.values()])
      .filter(row => row.type === 'inbound-rtp' && (row.kind === 'video' || row.mediaType === 'video'));
    return {
      peerCount: peers.length,
      connectionStates: peers.map(peer => peer.connectionState),
      iceConnectionStates: peers.map(peer => peer.iceConnectionState),
      inboundVideo: inbound.map(row => ({
        framesDecoded: row.framesDecoded || 0,
        framesReceived: row.framesReceived || 0,
        bytesReceived: row.bytesReceived || 0,
        frameWidth: row.frameWidth || 0,
        frameHeight: row.frameHeight || 0,
        packetsReceived: row.packetsReceived || 0,
      })),
    };
  });
}

async function frameSample(page) {
  return page.evaluate(() => {
    const video = document.querySelector('#stageRemoteVideo');
    const width = 160;
    const height = Math.max(90, Math.round(width * video.videoHeight / video.videoWidth));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    context.drawImage(video, 0, 0, width, height);
    const pixels = context.getImageData(0, 0, width, height).data;
    const sample = [];
    const colors = new Set();
    let visible = 0;
    for (let y = 0; y < height; y += 2) {
      for (let x = 0; x < width; x += 2) {
        const offset = (y * width + x) * 4;
        const r = pixels[offset], g = pixels[offset + 1], b = pixels[offset + 2];
        sample.push(r, g, b);
        if (r + g + b > 45) visible++;
        colors.add(`${r >> 4},${g >> 4},${b >> 4}`);
      }
    }
    let hash = 2166136261;
    for (const value of sample) hash = Math.imul(hash ^ value, 16777619);
    return {
      width: video.videoWidth,
      height: video.videoHeight,
      sampleWidth: width,
      sampleHeight: height,
      visibleRatio: visible / (width / 2 * Math.ceil(height / 2)),
      distinctColors: colors.size,
      hash: (hash >>> 0).toString(16).padStart(8, '0'),
      sample,
    };
  });
}

function frameDifference(before, after) {
  assert(before.sample.length === after.sample.length, 'Remote frame sample dimensions changed.');
  let changed = 0;
  let total = 0;
  for (let index = 0; index < before.sample.length; index += 3) {
    const diff = Math.max(
      Math.abs(before.sample[index] - after.sample[index]),
      Math.abs(before.sample[index + 1] - after.sample[index + 1]),
      Math.abs(before.sample[index + 2] - after.sample[index + 2]),
    );
    total++;
    if (diff >= 24) changed++;
  }
  return { changedPixels: changed, sampledPixels: total, changedRatio: changed / total };
}

async function waitForBoardChange(page, before) {
  const end = Date.now() + 30_000;
  let best = { frame: before, difference: { changedPixels: 0, sampledPixels: 0, changedRatio: 0 } };
  while (Date.now() < end) {
    const frame = await frameSample(page);
    const difference = frameDifference(before, frame);
    if (difference.changedRatio > best.difference.changedRatio) best = { frame, difference };
    if (difference.changedRatio >= 0.008) return best;
    await delay(200);
  }
  return best;
}
async function roomState(page) {
  return page.evaluate(() => ({
    side: document.querySelector('#mySide')?.textContent.trim(),
    status: document.querySelector('#status')?.textContent.trim(),
    moves: [...document.querySelectorAll('#moves .move-cell')].map(cell => cell.textContent.trim()),
    uciMoves: window.__airChessSnapshots?.at(-1)?.moves ?? [],
    whiteClock: document.querySelector('#mySide')?.textContent.includes('White')
      ? document.querySelector('#myClock')?.textContent.trim()
      : document.querySelector('#oppClock')?.textContent.trim(),
    blackClock: document.querySelector('#mySide')?.textContent.includes('Black')
      ? document.querySelector('#myClock')?.textContent.trim()
      : document.querySelector('#oppClock')?.textContent.trim(),
    activeClock: document.querySelector('#myClock')?.classList.contains('active') ? 'BLACK'
      : document.querySelector('#oppClock')?.classList.contains('active') ? 'WHITE' : null,
  }));
}

async function waitForMove(page, move) {
  await page.waitForFunction(expected => {
    const moves = [...document.querySelectorAll('#moves .move-cell')].map(cell => cell.textContent.trim().toLowerCase());
    return moves.includes(expected);
  }, move, { timeout: timeoutMs });
}

async function readResult() {
  const stats = await readPeerStats(page);
  const state = await roomState(page);
  assert(state.uciMoves.length === state.moves.length, `Snapshot/UCI move list mismatch: ${state.uciMoves.length} server moves vs ${state.moves.length} browser moves.`);
  return {
    version: 1,
    phase,
    roomCode,
    browserSide: state.side?.toUpperCase(),
    moves: state.uciMoves,
    sanMoves: state.moves,
    turn: state.activeClock,
    status: state.status,
    whiteClock: state.whiteClock,
    blackClock: state.blackClock,
    remoteFrameVerified: Boolean(frameProof?.remoteFrameVerified),
    peer: stats,
    frame: frameProof,
    screenshot: screenshotFile,
  };
}

async function publishResult() {
  await atomicJson(resultFile, await readResult());
}


async function run() {
  await rm(resultFile, { force: true });
  await startServer();
  roomCode = await readRoomCode();
  assert(/^[A-Z0-9]{4,12}$/.test(roomCode), `Invalid native room code: ${roomCode}`);
  await mkdir(dirname(screenshotFile), { recursive: true });
  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.addInitScript(() => {
    const NativePeerConnection = window.RTCPeerConnection;
    window.__airChessPeerConnections = [];
    window.RTCPeerConnection = new Proxy(NativePeerConnection, {
      construct(target, args) {
        const peer = Reflect.construct(target, args, target);
        window.__airChessPeerConnections.push(peer);
        return peer;
      },
    });
    const NativeWebSocket = window.WebSocket;
    window.__airChessSnapshots = [];
    window.WebSocket = new Proxy(NativeWebSocket, {
      construct(target, args) {
        const socket = Reflect.construct(target, args, target);
        socket.addEventListener('message', event => {
          try {
            const message = JSON.parse(event.data);
            if (message.snapshot) window.__airChessSnapshots.push(message.snapshot);
          } catch {}
        });
        return socket;
      },
    });
  });
  page = await context.newPage();
  page.on('pageerror', error => console.error(`Browser page error: ${error.message}`));
  await page.goto(gameUrl(roomCode), { waitUntil: 'domcontentloaded' });
  await page.locator('#name').fill('AirChess Browser Smoke');
  await page.locator('#joinGame').click();
  await page.waitForFunction(() => document.querySelector('#mySide')?.textContent.trim() === 'Black', null, { timeout: timeoutMs });
  await enableSharing(page);
  await page.waitForFunction(() => document.querySelector('#cameraStatus')?.textContent.trim() === 'Views connected', null, { timeout: timeoutMs });
  await page.locator('#camera-board').click();
  await page.locator('#videoStage').waitFor({ state: 'visible', timeout: timeoutMs });
  await waitForBrowserPeer(page);

  const before = await frameSample(page);
  assert(before.width >= 100 && before.height >= 100, 'Received video frame dimensions are too small.');
  assert(before.visibleRatio > 0.12, `Remote frame is mostly black (${before.visibleRatio.toFixed(3)} visible).`);
  assert(before.distinctColors > 80, `Remote frame lacks board content (${before.distinctColors} sampled colors).`);
  peerStats = await readPeerStats(page);
  assert(peerStats.peerCount === 1, `Expected one browser WebRTC peer, found ${peerStats.peerCount}.`);
  assert(peerStats.connectionStates.some(state => state === 'connected'), `Browser peer not connected: ${peerStats.connectionStates.join(', ')}`);
  phase = 'ready-for-native-e4';
  frameProof = {
    width: before.width,
    height: before.height,
    distinctColorsBefore: before.distinctColors,
    visibleRatioBefore: before.visibleRatio,
    hashBefore: before.hash,
    remoteFrameVerified: false,
  };
  await publishResult();

  await waitForMove(page, 'e4');
  const { frame: after, difference } = await waitForBoardChange(page, before);
  assert(difference.changedRatio >= 0.008, `Remote board did not visibly change after e2e4 (${difference.changedRatio.toFixed(4)} changed).`);
  peerStats = await readPeerStats(page);
  assert(peerStats.inboundVideo.some(row => row.framesDecoded > 0 && row.bytesReceived > 0), 'Inbound video stopped before the e2e4 frame check.');
  frameProof = {
    width: after.width,
    height: after.height,
    distinctColorsBefore: before.distinctColors,
    distinctColorsAfter: after.distinctColors,
    visibleRatioAfter: after.visibleRatio,
    hashBefore: before.hash,
    hashAfterE2E4: after.hash,
    e2e4Difference: difference,
    remoteFrameVerified: true,
  };
  phase = 'remote-frame-verified';
  await page.screenshot({ path: screenshotFile, fullPage: true });
  await publishResult();

  const beforeE5 = await roomState(page);
  assert(beforeE5.moves.length === 1 && beforeE5.moves[0].toLowerCase() === 'e4', `Browser did not receive e2e4: ${beforeE5.moves.join(' ')}`);
  assert(beforeE5.activeClock === 'BLACK', `Expected Black to move after e2e4, got ${beforeE5.activeClock}.`);
  await page.locator('#stageBoardToggle').click();
  await page.locator('#videoStage').waitFor({ state: 'hidden', timeout: timeoutMs });
  await playMove(page, 'e7', 'e5');
  await waitForMove(page, 'e5');
  const afterE5 = await roomState(page);
  assert(afterE5.moves.map(move => move.toLowerCase()).join(',') === 'e4,e5', `Browser move list mismatch: ${afterE5.moves.join(' ')}`);
  assert(afterE5.activeClock === 'WHITE', `Expected White to move after e7e5, got ${afterE5.activeClock}.`);
  assert(afterE5.whiteClock && afterE5.blackClock, 'Online clocks are missing from browser view.');
  phase = 'awaiting-native-g1f3';
  peerStats = await readPeerStats(page);
  await page.screenshot({ path: screenshotFile, fullPage: true });
  await publishResult();

  await waitForMove(page, 'Nf3');
  const finalState = await roomState(page);
  assert(finalState.moves.map(move => move.toLowerCase()).join(',') === 'e4,e5,nf3', `Final room move list mismatch: ${finalState.moves.join(' ')}`);
  assert(finalState.activeClock === 'BLACK', `Expected Black to move after Nf3, got ${finalState.activeClock}.`);
  peerStats = await readPeerStats(page);
  assert(peerStats.inboundVideo.some(row => row.framesDecoded > 0 && row.bytesReceived > 0), 'No remote video frames at end of smoke.');
  phase = 'complete';
  await page.screenshot({ path: screenshotFile, fullPage: true });
  await publishResult();
  console.log(JSON.stringify(await readResult(), null, 2));
}

try {
  await run();
} catch (error) {
  phase = 'failed';
  console.error(error.stack || error);
  if (page) {
    try { await page.screenshot({ path: screenshotFile, fullPage: true }); } catch {}
  }
  try {
    await atomicJson(resultFile, {
      version: 1,
      phase,
      roomCode,
      error: error.message || String(error),
      remoteFrameVerified: Boolean(frameProof?.remoteFrameVerified),
      peer: peerStats,
      frame: frameProof,
      screenshot: screenshotFile,
    });
  } catch (writeError) {
    console.error(`Could not write browser smoke result: ${writeError.message}`);
  }
  process.exitCode = 1;
} finally {
  await browser?.close();
  if (serverProcess && serverProcess.exitCode === null) serverProcess.kill('SIGTERM');
}
