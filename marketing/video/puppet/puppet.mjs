#!/usr/bin/env node
// AirChess video puppet: drives ONE side of an online game in the web client
// through a visible browser window (or --headless). Logs every ply. See README.md.

import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import puppeteer from 'puppeteer-core';
// Move-notation conversion only. The web client's own copy of chess.js, not a dependency.
import { Chess } from '../../../server/src/main/resources/web/vendor/chess.js/chess.js';

const DEFAULT_BROWSER = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const TIME_CONTROLS = ['60,0', '180,2', '300,0', '600,0', '900,10'];
const COLORS = ['RANDOM', 'WHITE', 'BLACK'];
const UCI_RE = /^[a-h][1-8][a-h][1-8][qrbn]?$/;
const BOOLEAN_FLAGS = new Set([
  'create', 'headless', 'decline-draws', 'accept-rematch', 'offer-rematch', 'share', 'open-stage', 'help',
]);
const VALUE_FLAGS = new Set([
  'server', 'name', 'join', 'color', 'time', 'moves', 'delay', 'offer-draw-after', 'browser', 'width', 'height',
]);

const USAGE = `Usage: node puppet.mjs --server <your server URL> (--create | --join <CODE>) [options]

Required:
  --server <url>                 Base URL of your AirChess server (web client is at <url>/play/)
  --create                       Create a room and print its code
  --join <CODE>                  Join an existing room by its code

Options:
  --name <name>                  Display name (default: Alex with --create, Sam with --join)
  --color <WHITE|BLACK|RANDOM>   Colour requested with --create (default RANDOM)
  --time <init,inc>              Time control with --create: ${TIME_CONTROLS.join(' | ')} (default 300,0)
  --moves <file>                 UCI move list; this side plays its plies from it
  --delay <ms>                   Pause before each of our moves (default 3000)
  --offer-draw-after <n>         Offer a draw right after our move that makes ply n
  --decline-draws                Decline draw offers from the opponent
  --offer-rematch                Offer a rematch once the game is over
  --accept-rematch               Accept a rematch the opponent offers
  --share                        Turn on "Share view" once the game has started
  --open-stage                   Open the opponent's shared view full screen when it arrives
  --headless                     No visible window (for tests)
  --browser <exe>                Browser executable (default: Microsoft Edge)
  --width <px> / --height <px>   Window size (default 1600 x 1000)
  --help                         Show this help

Stop with Ctrl+C; that closes the browser and removes its temporary profile.
`;

// ---------------------------------------------------------------- logging

function stamp() {
  const d = new Date();
  const p = (n, w = 2) => String(n).padStart(w, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`;
}

function log(tag, message) {
  console.log(`[${stamp()}] ${tag.padEnd(7)} ${message}`);
}

function printRoomCode(code) {
  const rule = '='.repeat(56);
  console.log(`\n${rule}\n  ROOM CODE:  ${code.split('').join(' ')}\n  On the phone: Join game, enter ${code}\n${rule}\n`);
}

// ---------------------------------------------------------------- arguments

function parseArgs(argv) {
  const opts = {};
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (!arg.startsWith('--')) throw new Error(`unexpected argument "${arg}"`);
    const eq = arg.indexOf('=');
    const flag = eq === -1 ? arg.slice(2) : arg.slice(2, eq);
    const key = flag.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
    if (BOOLEAN_FLAGS.has(flag)) {
      if (eq !== -1) throw new Error(`--${flag} takes no value`);
      opts[key] = true;
    } else if (VALUE_FLAGS.has(flag)) {
      let value;
      if (eq !== -1) {
        value = arg.slice(eq + 1);
      } else {
        value = argv[++i];
        if (value === undefined || value.startsWith('--')) throw new Error(`--${flag} needs a value`);
      }
      opts[key] = value;
    } else {
      throw new Error(`unknown option --${flag} (see --help)`);
    }
  }
  return opts;
}

function integer(text, name, min) {
  const n = Number(text);
  if (!Number.isInteger(n) || n < min) throw new Error(`--${name} must be an integer >= ${min}`);
  return n;
}

function loadMoves(file) {
  const text = readFileSync(file, 'utf8');
  const moves = text
    .split('\n')
    .map((line) => line.replace(/#.*/, ''))
    .join(' ')
    .split(/\s+/)
    .filter(Boolean);
  if (moves.length === 0) throw new Error(`${file} contains no moves`);
  for (const move of moves) {
    if (!UCI_RE.test(move)) throw new Error(`${file}: "${move}" is not a UCI move`);
  }
  return moves;
}

function buildConfig(opts) {
  if (opts.help) return null;
  if (!opts.server) throw new Error('--server is required (your server URL)');
  if (opts.create && opts.join !== undefined) throw new Error('use either --create or --join, not both');
  if (!opts.create && opts.join === undefined) throw new Error('need --create or --join <CODE>');

  const base = new URL(opts.server);
  if (base.protocol !== 'http:' && base.protocol !== 'https:') throw new Error('--server must be an http(s) URL');
  base.pathname = `${base.pathname.replace(/\/+$/, '').replace(/\/play$/, '')}/play/`;
  base.search = '';
  base.hash = '';

  const create = Boolean(opts.create);
  if (!create && (opts.color !== undefined || opts.time !== undefined)) {
    throw new Error('--color and --time only apply with --create');
  }
  const color = (opts.color ?? 'RANDOM').toUpperCase();
  if (!COLORS.includes(color)) throw new Error(`--color must be one of ${COLORS.join(', ')}`);
  const timeControl = opts.time ?? '300,0';
  if (!TIME_CONTROLS.includes(timeControl)) throw new Error(`--time must be one of ${TIME_CONTROLS.join(' | ')}`);

  let join = null;
  if (!create) {
    join = opts.join.trim().toUpperCase();
    if (!/^[A-Z0-9]{4,12}$/.test(join)) throw new Error('--join needs the room code shown on the creating side');
  }
  const name = (opts.name ?? (create ? 'Alex' : 'Sam')).trim();
  if (!name) throw new Error('--name cannot be empty');

  const browser = opts.browser ?? DEFAULT_BROWSER;
  if (!existsSync(browser)) throw new Error(`browser not found: ${browser} (pass --browser <exe>)`);

  return {
    pageUrl: base.href,
    create,
    join,
    name,
    color,
    timeControl,
    moves: opts.moves === undefined ? null : loadMoves(opts.moves),
    delay: integer(opts.delay ?? '3000', 'delay', 0),
    offerDrawAfter: opts.offerDrawAfter === undefined ? null : integer(opts.offerDrawAfter, 'offer-draw-after', 1),
    declineDraws: Boolean(opts.declineDraws),
    offerRematch: Boolean(opts.offerRematch),
    acceptRematch: Boolean(opts.acceptRematch),
    share: Boolean(opts.share),
    openStage: Boolean(opts.openStage),
    headless: Boolean(opts.headless),
    browser,
    width: integer(opts.width ?? '1600', 'width', 300),
    height: integer(opts.height ?? '1000', 'height', 300),
  };
}

// ---------------------------------------------------------------- helpers

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function until(check, timeoutMs, what) {
  const end = Date.now() + timeoutMs;
  for (;;) {
    const value = await check();
    if (value) return value;
    if (Date.now() > end) throw new Error(`timed out waiting for ${what}`);
    await sleep(250);
  }
}

// Move history as UCI. Accepts UCI strings, SAN strings or {from,to,promotion} objects.
function toUci(entries) {
  if (entries.every((e) => typeof e === 'string' && UCI_RE.test(e))) return entries;
  const game = new Chess();
  return entries.map((entry) => {
    let move = entry;
    if (typeof entry === 'string' && UCI_RE.test(entry)) {
      move = { from: entry.slice(0, 2), to: entry.slice(2, 4), promotion: entry[4] };
    }
    const played = game.move(move);
    if (!played) throw new Error(`cannot replay move ${JSON.stringify(entry)}`);
    return played.from + played.to + (played.promotion ?? '');
  });
}

// ---------------------------------------------------------------- page-side functions
// These run inside the browser. They must not reference anything from this file.

function setValue(selector, value) {
  const el = document.querySelector(selector);
  el.value = value;
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
}

function clickSelector(selector) {
  document.querySelector(selector).click();
}

function pressSquare(square) {
  const hook = window.airchess;
  if (hook && typeof hook.selectSquare === 'function') {
    hook.selectSquare(square);
    return;
  }
  const cell = document.querySelector(`[data-square="${square}"]`);
  if (!cell) throw new Error(`no board square ${square}`);
  cell.click();
}

function promotionOpen() {
  return !document.getElementById('promotion').classList.contains('hidden');
}

function pickPromotion(piece) {
  const button = document.querySelector(`#promotion [data-piece="${piece}"]`);
  if (!button) throw new Error(`no promotion button for ${piece}`);
  button.click();
}

function readPageState() {
  const el = (id) => document.getElementById(id);
  const shown = (id) => {
    const node = el(id);
    return Boolean(node) && !node.classList.contains('hidden');
  };
  const hook = window.airchess;
  return {
    side: el('mySide').textContent.trim(),
    status: el('status').textContent.trim(),
    code: el('code').textContent.trim(),
    lobbyVisible: shown('lobby'),
    lobbyMessage: el('lobbyMessage').textContent.trim(),
    san: [...document.querySelectorAll('#moves .move-cell')].map((c) => c.textContent.trim()),
    hookHistory: hook && typeof hook.getHistory === 'function' ? hook.getHistory() : null,
    drawIncoming: shown('acceptDraw'),
    drawDisabled: el('draw').disabled,
    over: shown('gameOver'),
    result: el('resultTitle').textContent.trim(),
    rematchVisible: shown('rematch'),
    rematchText: el('rematch').textContent.trim(),
    shareText: el('cameraToggle').textContent.trim(),
    cameraStatus: el('cameraStatus').textContent.trim(),
    cameraBoard: shown('camera-board'),
    stageOpen: shown('videoStage'),
    remoteStream: Boolean(el('stageRemoteVideo').srcObject),
  };
}

// ---------------------------------------------------------------- main

async function main() {
  const cfg = buildConfig(parseArgs(process.argv.slice(2)));
  if (!cfg) {
    process.stdout.write(USAGE);
    return;
  }

  const profile = mkdtempSync(join(tmpdir(), 'airchess-puppet-'));
  const browser = await puppeteer.launch({
    executablePath: cfg.browser,
    headless: cfg.headless,
    userDataDir: profile,
    defaultViewport: cfg.headless ? { width: cfg.width, height: cfg.height } : null,
    args: [`--window-size=${cfg.width},${cfg.height}`, '--no-first-run', '--no-default-browser-check'],
  });

  let closing = false;
  const shutdown = async (code) => {
    if (closing) return;
    closing = true;
    await browser.close().catch(() => {});
    try {
      rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    } catch {
      // Browser may still hold the profile briefly; the OS temp folder cleans it up later.
    }
    process.exit(code);
  };
  process.on('SIGINT', () => {
    log('INFO', 'Ctrl+C: closing browser');
    shutdown(0);
  });
  browser.on('disconnected', () => {
    if (!closing) {
      log('WARN', 'browser window closed');
      shutdown(0);
    }
  });

  const page = (await browser.pages())[0] ?? (await browser.newPage());
  page.setDefaultTimeout(30000);
  page.on('pageerror', (err) => log('ERROR', `page script error: ${err.message}`));
  page.on('dialog', (dialog) => {
    log('WARN', `dismissed dialog: ${dialog.message()}`);
    dialog.dismiss();
  });
  await page.goto(cfg.pageUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#name');
  log('INFO', `opened web client at ${cfg.pageUrl}`);

  // ---- lobby: create or join
  await page.evaluate(setValue, '#name', cfg.name);
  if (cfg.create) {
    await page.evaluate(setValue, '#timeControl', cfg.timeControl);
    await page.evaluate(setValue, '#color', cfg.color);
    await page.evaluate(clickSelector, '#createGame');
    log('INFO', `creating room: colour ${cfg.color}, time ${cfg.timeControl}, name ${cfg.name}`);
  } else {
    await page.evaluate(setValue, '#joinCode', cfg.join);
    await page.evaluate(clickSelector, '#joinGame');
    log('INFO', `joining room ${cfg.join} as ${cfg.name}`);
  }

  const seat = await until(async () => {
    const s = await page.evaluate(readPageState);
    if (s.side && s.code) return s;
    if (s.lobbyVisible && s.lobbyMessage) throw new Error(`server said: ${s.lobbyMessage}`);
    return null;
  }, 20000, 'seat assignment');

  log('INFO', `seated as ${seat.side} (${cfg.name}) in room ${seat.code}; board access: ${seat.hookHistory !== null ? 'window.airchess' : 'DOM'}`);
  if (cfg.create) printRoomCode(seat.code);

  // ---- state for the play loop
  const exp = cfg.moves ?? [];
  const st = {
    side: seat.side === 'White' ? 'w' : 'b',
    game: 1,
    over: false,
    plies: 0,
    playback: cfg.moves !== null,
    listDone: false,
    pending: null,
    blocked: -1,
    waitPly: -1,
    readyAt: 0,
    badKey: '',
    badWarnAt: 0,
    histWarned: false,
    drawDue: false,
    drawOffered: false,
    drawResolved: true,
    drawClickedAt: 0,
    drawPlies: -1,
    incomingDraw: false,
    rematchNoted: false,
    rematchAsked: false,
    rematchAccepted: false,
    shareTries: 0,
    shareAt: 0,
    camera: '',
    remoteLive: false,
    stageFor: false,
  };

  const startNewGame = (why) => {
    st.game++;
    st.over = false;
    st.plies = 0;
    st.pending = null;
    st.blocked = -1;
    st.waitPly = -1;
    st.drawDue = false;
    st.drawOffered = false;
    st.drawResolved = true;
    st.incomingDraw = false;
    st.rematchNoted = false;
    st.rematchAsked = false;
    st.rematchAccepted = false;
    st.badKey = '';
    if (st.playback) {
      st.playback = false;
      log('GAME', `game ${st.game} started (${why}); the move list was for game 1, so playback stops here`);
    } else {
      log('GAME', `game ${st.game} started (${why})`);
    }
  };

  const clickMove = async (uci) => {
    await page.evaluate(pressSquare, uci.slice(0, 2));
    await page.evaluate(pressSquare, uci.slice(2, 4));
    if (uci.length === 5) {
      for (let i = 0; i < 20; i++) {
        if (await page.evaluate(promotionOpen)) {
          await page.evaluate(pickPromotion, uci[4]);
          return;
        }
        await sleep(100);
      }
    }
  };

  const tick = async () => {
    const s = await page.evaluate(readPageState);
    const now = Date.now();
    const side = s.side === 'White' ? 'w' : 'b';
    if (side !== st.side) {
      log('INFO', `your colour is now ${s.side}`);
      st.side = side;
    }
    const started = s.status !== 'Waiting for opponent';

    let hist;
    try {
      hist = toUci(s.hookHistory ?? s.san);
      st.histWarned = false;
    } catch (err) {
      if (!st.histWarned) log('WARN', `could not read the move history: ${err.message}`);
      st.histWarned = true;
      return;
    }

    // ---- game boundaries (rematch)
    if (st.over && !s.over) startNewGame('rematch');
    else if (hist.length < st.plies) startNewGame('board reset');

    // ---- plies: log each new one, flag a mismatch against the move list
    let bad = -1;
    if (st.playback) {
      for (let i = 0; i < hist.length; i++) {
        if (exp[i] !== hist[i]) {
          bad = i;
          break;
        }
      }
    }
    for (; st.plies < hist.length; st.plies++) {
      const i = st.plies;
      const uci = hist[i];
      const san = s.san[i] ?? uci;
      const ours = (i % 2 === 0) === (st.side === 'w');
      log(ours ? 'PLAY' : 'SEEN', `${ours ? 'played' : 'seen'} ply ${i + 1}: ${san} (${uci})`);
      if (ours && cfg.offerDrawAfter === i + 1) st.drawDue = true;
    }
    if (s.over && !st.over) {
      st.over = true;
      log('GAME', `game ${st.game} over after ${hist.length} plies: ${s.result || s.status}`);
    }
    if (st.pending && hist.length > st.pending.ply) st.pending = null;

    if (bad >= 0) {
      const key = `${bad}:${hist[bad]}`;
      if (key !== st.badKey || now - st.badWarnAt > 15000) {
        st.badKey = key;
        st.badWarnAt = now;
        const want = bad < exp.length ? exp[bad] : 'nothing (list ended)';
        log('WARN', `MISMATCH at ply ${bad + 1}: board has ${hist[bad]}, move list has ${want}. Playback PAUSED until the board matches the list again.`);
      }
    } else if (st.badKey) {
      st.badKey = '';
      log('INFO', 'board matches the move list again; playback resumed');
    }

    // ---- draw offers
    if (s.drawIncoming && !st.incomingDraw) {
      st.incomingDraw = true;
      log('DRAW', `opponent offered a draw (after ply ${hist.length})`);
      if (cfg.declineDraws) {
        await page.evaluate(clickSelector, '#declineDraw');
        log('DRAW', 'declined the draw offer');
      }
    } else if (!s.drawIncoming) {
      st.incomingDraw = false;
    }
    if (st.drawDue && s.over) st.drawDue = false;
    if (st.drawDue && started && !s.drawDisabled) {
      await page.evaluate(clickSelector, '#draw');
      st.drawDue = false;
      st.drawOffered = true;
      st.drawResolved = false;
      st.drawClickedAt = now;
      st.drawPlies = hist.length;
      log('DRAW', `offered a draw after ply ${hist.length}`);
    }
    if (st.drawOffered && !st.drawResolved && !s.drawDisabled && !s.over
      && now - st.drawClickedAt > 1500) {
      st.drawResolved = true;
      log('DRAW', 'our draw offer was declined; the game continues');
    }

    // ---- rematch
    if (s.over) {
      if (s.rematchText === 'Accept rematch' && !st.rematchNoted) {
        st.rematchNoted = true;
        log('REMATCH', 'opponent offered a rematch');
      }
      if (cfg.offerRematch && !st.rematchAsked && s.rematchVisible && s.rematchText === 'Rematch') {
        st.rematchAsked = true;
        await page.evaluate(clickSelector, '#rematch');
        log('REMATCH', 'offered a rematch');
      }
      if (cfg.acceptRematch && !st.rematchAccepted && s.rematchText === 'Accept rematch') {
        st.rematchAccepted = true;
        await page.evaluate(clickSelector, '#rematch');
        log('REMATCH', 'accepted the opponent\'s rematch');
      }
    }

    // ---- view sharing
    if (cfg.share && started && s.shareText === 'Share view' && st.shareTries < 3 && now - st.shareAt > 8000) {
      st.shareTries++;
      st.shareAt = now;
      await page.evaluate(clickSelector, '#cameraToggle');
      log('SHARE', `turned on view sharing (attempt ${st.shareTries})`);
    }
    if (s.cameraStatus !== st.camera) {
      st.camera = s.cameraStatus;
      if (s.cameraStatus) log('SHARE', `view status: ${s.cameraStatus}`);
    }
    if (s.remoteStream) {
      if (!st.remoteLive) {
        st.remoteLive = true;
        log('SHARE', 'opponent view stream received');
      }
      if (cfg.openStage && !st.stageFor && s.cameraBoard && !s.stageOpen) {
        st.stageFor = true;
        await page.evaluate(clickSelector, '#camera-board');
        log('STAGE', 'opened the opponent view full screen');
      }
    } else {
      st.remoteLive = false;
      st.stageFor = false;
    }

    // ---- our moves
    if (st.playback && bad < 0 && started && !s.over) {
      const n = hist.length;
      if (n >= exp.length) {
        if (!st.listDone) {
          st.listDone = true;
          log('INFO', `move list complete (${exp.length} plies); playback idle`);
        }
      } else if (st.pending) {
        const p = st.pending;
        if (now - p.sentAt > 4000) {
          if (p.attempts < 2) {
            p.attempts++;
            p.sentAt = now;
            log('WARN', `ply ${p.ply + 1} (${p.uci}) did not register; retrying once`);
            await clickMove(p.uci);
          } else {
            log('WARN', `ply ${p.ply + 1} (${p.uci}) did not register after 2 tries; giving up on it. Fix the board by hand; playback waits for the board to change.`);
            st.blocked = p.ply;
            st.pending = null;
          }
        }
      } else {
        const ourTurn = (n % 2 === 0) === (st.side === 'w') && s.status === 'Your move';
        if (ourTurn && st.blocked !== n) {
          if (st.waitPly !== n) {
            st.waitPly = n;
            st.readyAt = now + cfg.delay;
            log('INFO', `our move up next: ply ${n + 1} is ${exp[n]} (in ${(cfg.delay / 1000).toFixed(1)} s)`);
          }
          if (now >= st.readyAt) {
            st.pending = { ply: n, uci: exp[n], sentAt: now, attempts: 1 };
            await clickMove(exp[n]);
          }
        }
      }
    }
  };

  log('INFO', 'running. Ctrl+C to stop.');
  let failures = 0;
  for (;;) {
    try {
      await tick();
      failures = 0;
    } catch (err) {
      failures++;
      log('ERROR', `${err.message.split('\n')[0]} (${failures}/10)`);
      if (failures >= 10) await shutdown(1);
    }
    await sleep(250);
  }
}

main().catch((err) => {
  console.error(`[${stamp()}] ERROR   ${err.message}`);
  process.exit(1);
});
