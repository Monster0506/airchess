import { Chess } from './vendor/chess.js/chess.js';
import { CameraCall } from './camera.js';
import { refreshBoardStream, shareBoard } from './board-stream.js';
let board3d;
const $ = id => document.getElementById(id);
const PIECES = { k:'♚',q:'♛',r:'♜',b:'♝',n:'♞',p:'♟' };
const GLYPH = { w:{k:'♔',q:'♕',r:'♖',b:'♗',n:'♘',p:'♙'}, b:PIECES };
const MODE_DEPTH = { Easy:3, Medium:7, Hard:12, Max:17 };
const SHARE_FPS = 15;
let chess = new Chess(), mode = 'local', side = 'w', flipped = false, selected = null, legal = [], lastMove = null, view3d = false, showThreats = false, hintMove = null, worker, ws, onlineSide, onlineCode, token, snap, receivedAt = 0, engineReady = false, pending = null, dragSource = null;
let engineTask = 'hint', searchGeneration = 0;
let pendingMove = null;
const camera = new CameraCall({ send, localVideo: $('localVideo'), remoteVideo: $('stageRemoteVideo'), onState: cameraState, capture: captureBoard });
let cameraStage = false;
function captureBoard() {
  if (!view3d) { return shareBoard(boardEl); }
  const canvas = $('threeBoard');
  if (!canvas.captureStream) { throw new Error('View sharing is unavailable in this browser.'); }
  return canvas.captureStream(SHARE_FPS);
}
function cameraState(state, message) {
  const enabled = camera.enabled;
  $('cameraToggle').textContent = enabled ? 'Stop sharing' : 'Share view';
  $('cameraStatus').textContent = message === 'off' ? '' : message;
  $('stageCameraStatus').textContent = message === 'off' ? 'Waiting for opponent view' : message;
  $('stageCameraStatus').classList.toggle('hidden', Boolean($('stageRemoteVideo').srcObject));
  $('camera-board').classList.toggle('hidden', !enabled || cameraStage);
  $('cameraBoardStatus').textContent = message === 'off' ? 'Waiting for opponent view' : message;
  if (state === 'connected' && !view3d) { refreshBoardStream(boardEl); }
  $('boardRemoteVideo').srcObject = $('stageRemoteVideo').srcObject;
  if (!enabled) {
    $('boardRemoteVideo').srcObject = null;
    closeCameraStage();
  }
}
function showCameraStage() {
  cameraStage = true;
  $('videoStage').classList.remove('hidden');
  $('camera-board').classList.add('hidden');
}
function closeCameraStage() {
  cameraStage = false;
  $('videoStage').classList.add('hidden');
  $('camera-board').classList.toggle('hidden', !camera.enabled);
  if (mode === 'online') $('game').classList.remove('hidden');
}
camera.setOnline(false);
$('stageBoardToggle').onclick = closeCameraStage;
$('cameraToggle').onclick = () => camera.enabled ? camera.stop() : camera.enable();
$('stageCameraOff').onclick = () => camera.stop();
$('localVideoSwap').onclick = closeCameraStage;
document.addEventListener('keydown', event => { if (cameraStage && event.key === 'Escape') closeCameraStage(); });
const boardEl=$('board');
$('name').value=localStorage.getItem('airchess.name')||'';
$('name').addEventListener('input',()=>localStorage.setItem('airchess.name',$('name').value));
const queryCode=new URLSearchParams(location.search).get('code'); if(queryCode){$('joinCode').value=queryCode.toUpperCase(); if(!$('name').value) $('lobbyMessage').textContent='Enter your name to join the game.';}
function name(){const n=$('name').value.trim(); if(!n){$('lobbyMessage').textContent='Please enter your name.';$('name').focus();return null;}localStorage.setItem('airchess.name',n);return n;}
function socketUrl(){return `${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`;}
function setConnection(text){$('connection').textContent=text;}
function connect(first){camera.setOnline(true);if(ws)ws.close();const socket=new WebSocket(socketUrl());ws=socket;socket.onopen=()=>{if(ws!==socket)return;setConnection('Connected');socket.send(JSON.stringify(first));};socket.onmessage=e=>{if(ws===socket)onMessage(JSON.parse(e.data));};socket.onerror=()=>{if(ws===socket)setConnection('Connection error');};socket.onclose=()=>{if(ws!==socket)return;camera.setOnline(false);if(mode==='online'&&token&&onlineCode){setConnection('Reconnecting…');setTimeout(()=>{if(mode==='online'&&ws===socket)connect({type:'resume',code:onlineCode,token});},1500);}else if(mode==='online')setConnection('Disconnected');};}
function send(m){if(ws?.readyState===WebSocket.OPEN)ws.send(JSON.stringify(m));}
function startLocal(computer=false){cancelEngine();mode=computer?'computer':'local';onlineSide=null;side=computer?($('computerSide').value==='random'?(Math.random()<.5?'w':'b'):$('computerSide').value):'w';flipped=side==='b';chess=new Chess();lastMove=null;hintMove=null;clearOnline();showGame();render();if(computer&&side==='b')computerMove();}
function clearOnline(){onlineCode=null;token=null;snap=null;sessionStorage.removeItem('airchess.session');localStorage.removeItem('airchess.session');}
function showGame() {
  $('continueLocal').classList.add('hidden');
  $('lobby').classList.add('hidden');
  $('game').classList.remove('hidden');
  $('gameOver').classList.add('hidden');
  for (const id of ['onlineControls', 'invite', 'cameraControls']) $(id).classList.toggle('hidden', mode !== 'online');
  $('connection').textContent = mode === 'online' ? 'Connecting…' : '';
}
function startOnline(create){const n=name();if(!n)return;cancelEngine();camera.setOnline(true);$('cameraControls').classList.remove('hidden');mode='online';showGame();connect(create?{type:'create',name:n,timeControl:(()=>{const [initialSeconds,incrementSeconds]=$('timeControl').value.split(',').map(Number);return{initialSeconds,incrementSeconds};})(),color:$('color').value}:{type:'join',code:$('joinCode').value.trim().toUpperCase(),name:n});}
$('localStart').onclick=()=>startLocal();$('computerStart').onclick=()=>startLocal(true);$('createGame').onclick=()=>startOnline(true);$('joinGame').onclick=()=>startOnline(false);$('joinCode').onkeydown=e=>{if(e.key==='Enter')startOnline(false);};
function onMessage(message) {
  if (message.type === 'error') {
    const text = message.message || message.code;
    if (mode === 'online' && !snap) {
      showHome();
      $('lobbyMessage').textContent = text;
    } else {
      $('statusDetail').textContent = text;
    }
    return;
  }
  if (message.type === 'camera_config') camera.setConfig(message);
  if (message.type === 'camera_signal') { camera.signal(message); return; }
  if (message.type === 'joined') {
    onlineSide = message.side === 'WHITE' ? 'w' : 'b';
    side = onlineSide;
    flipped = side === 'b';
    onlineCode = message.code;
    token = message.token;
    camera.joined(onlineSide);
    const session = JSON.stringify({ code: onlineCode, token });
    localStorage.setItem('airchess.session', session);
    sessionStorage.setItem('airchess.session', session);
    location.hash = '';
  }
  if (!message.snapshot) return;
  snap = message.snapshot;
  snap.started = Boolean(snap.whiteName && snap.blackName);
  receivedAt = performance.now();
  const fresh = new Chess();
  for (const uci of snap.moves) {
    if (!fresh.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci[4] || 'q' })) break;
  }
  chess = fresh;
  lastMove = snap.moves.at(-1) || null;
  selected = null;
  hintMove = null;
  render();
  if (message.type === 'move_rejected') $('statusDetail').textContent = `Move rejected: ${message.reason}`;
}
function squares(){let arr=[];for(let r=0;r<8;r++)for(let f=0;f<8;f++)arr.push(String.fromCharCode(97+f)+(8-r));return flipped?arr.map(s=>String.fromCharCode(104-(s.charCodeAt(0)-97))+(9-Number(s[1]))):arr;}
function checkedKing(){if(!chess.isCheck())return null;const k=chess.board().flatMap((row,r)=>row.map((p,f)=>p?.type==='k'&&p.color===chess.turn()?String.fromCharCode(97+f)+(8-r):null).filter(Boolean))[0];return k||null;}
function render() {
  if ($('game').classList.contains('hidden')) return;
  const legalMoves = selected ? chess.moves({ square: selected, verbose: true }) : [];
  legal = legalMoves.map(move => move.to);
  const check = checkedKing();
  if (!view3d) {
    boardEl.replaceChildren();
    for (const square of squares()) {
      const file = square.charCodeAt(0) - 97, rank = Number(square[1]) - 1, pieceAt = chess.get(square);
      const cell = document.createElement('div');
      cell.className = `square ${((file + rank) % 2 ? 'light' : 'dark')}`;
      cell.dataset.square = square;
      cell.setAttribute('role', 'gridcell');
      if (lastMove && (lastMove.slice(0, 2) === square || lastMove.slice(2, 4) === square)) cell.classList.add('last');
      if (selected === square) cell.classList.add('selected');
      if (square === check) cell.classList.add('checked');
      if (showThreats && pieceAt && pieceAt.color !== chess.turn() && chess.isAttacked(square, chess.turn())) cell.classList.add('threatened');
      if (legal.includes(square)) {
        const marker = document.createElement('span');
        marker.className = pieceAt ? 'capture-ring' : 'legal-dot';
        cell.append(marker);
      }
      if (pieceAt) {
        const piece = document.createElement('span');
        piece.className = `piece ${pieceAt.color === 'w' ? 'white' : 'black'}`;
        piece.textContent = GLYPH[pieceAt.color][pieceAt.type];
        piece.draggable = true;
        piece.addEventListener('dragstart', event => {
          if (!canMoveFrom(square)) { event.preventDefault(); return; }
          dragSource = square;
          event.dataTransfer.setData('text/plain', square);
        });
        cell.append(piece);
      }
      if (flipped ? file === 7 : file === 0) {
        const coordinate = document.createElement('span');
        coordinate.className = 'coords rank';
        coordinate.textContent = square[1];
        cell.append(coordinate);
      }
      if (flipped ? rank === 7 : rank === 0) {
        const coordinate = document.createElement('span');
        coordinate.className = 'coords file';
        coordinate.textContent = square[0];
        cell.append(coordinate);
      }
      cell.onclick = () => onSquare(square);
      cell.ondragover = event => event.preventDefault();
      cell.ondrop = event => {
        event.preventDefault();
        const from = event.dataTransfer.getData('text/plain') || dragSource;
        if (from) moveTo(from, square);
        dragSource = null;
      };
      boardEl.append(cell);
    }
    drawHint2d();
  } else {
    board3d.render3d(chess, flipped, selected, legal, lastMove, check, showThreats);
    board3d.setArrow3d(hintMove);
  }
  renderPanels();
  if (camera.enabled && !view3d) { refreshBoardStream(boardEl); }
}
function canMoveFrom(sq){const p=chess.get(sq);return !!p&&!gameOver()&&(mode!=='online'||Boolean(snap?.started)&&onlineSide===chess.turn())&&(mode!=='computer'||chess.turn()===side)&&p.color===chess.turn();}
function onSquare(sq){if(gameOver())return;if(selected&&legal.includes(sq)){moveTo(selected,sq);return;}if(canMoveFrom(sq)){selected=sq;legal=chess.moves({square:sq,verbose:true}).map(m=>m.to);render();return;}selected=null;legal=[];render();}
function moveTo(from, to, promotion) {
  const moves = chess.moves({ square: from, verbose: true }).filter(move => move.to === to);
  if (!moves.length) { return; }
  if (moves.some(move => move.promotion) && !promotion) {
    cancelEngine();
    pendingMove = { from, to };
    $('promotion').classList.remove('hidden');
    return;
  }
  const uci = from + to + (promotion || '');
  if (mode === 'online') {
    if (!chess.move({ from, to, promotion: promotion || 'q' })) { return; }
    chess.undo();
    send({ type: 'move', uci, ply: snap?.moves.length || 0 });
    selected = null;
    return;
  }
  const result = chess.move({ from, to, promotion: promotion || undefined });
  if (!result) { return; }
  cancelEngine();
  lastMove = uci;
  selected = null;
  hintMove = null;
  $('hintText').textContent = '';
  render();
  if (mode === 'computer' && !gameOver()) { computerMove(); }
}
document.querySelectorAll('[data-piece]').forEach(button => {
  button.onclick = () => {
    if (!pendingMove) { return; }
    const move = pendingMove;
    pendingMove = null;
    $('promotion').classList.add('hidden');
    moveTo(move.from, move.to, button.dataset.piece);
  };
});
function closePromotion() {
  pendingMove = null;
  $('promotion').classList.add('hidden');
  selected = null;
  legal = [];
  render();
}
$('promotion').onclick = event => { if (event.target === $('promotion')) { closePromotion(); } };
window.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !$('promotion').classList.contains('hidden')) { closePromotion(); }
});
function gameOver(){return chess.isGameOver()||(mode==='online'&&!!snap?.result);}
function resultText(){if(mode==='online'&&snap?.result){const r=snap.result;return `${r.winner===null?'Draw':r.winner===(onlineSide==='w'?'WHITE':'BLACK')?'You won':'You lost'} — ${r.reason.toLowerCase().replaceAll('_',' ')}`;}if(chess.isCheckmate())return `${chess.turn()==='w'?'Black':'White'} wins by checkmate`;if(chess.isStalemate())return 'Draw by stalemate';if(chess.isThreefoldRepetition())return 'Draw by repetition';if(chess.isInsufficientMaterial())return 'Draw by insufficient material';if(chess.isDraw())return 'Draw';return '';}
function renderPanels(){const over=gameOver();$('status').textContent=over?resultText():mode==='online'?(!snap?.started?'Waiting for opponent':chess.turn()===onlineSide?'Your move':'Opponent’s move'):chess.isCheck()?'Check':mode==='computer'&&chess.turn()!==side?'Computer thinking…':chess.turn()==='w'?'White to move':'Black to move';$('myName').textContent=mode==='online'?(side==='w'?snap?.whiteName:snap?.blackName)||'You':mode==='computer'?'You':$('name').value||'Player';$('oppName').textContent=mode==='online'?(side==='w'?snap?.blackName:snap?.whiteName)||'Waiting for opponent…':mode==='computer'?'Stockfish':'Other player';$('mySide').textContent=side==='w'?'White':'Black';$('oppSide').textContent=side==='w'?'Black':'White';$('moves').replaceChildren();const hist=chess.history();for(let i=0;i<hist.length;i+=2){const row=document.createElement('div');row.className='move-row';const no=document.createElement('span');no.className='move-num';no.textContent=`${i/2+1}.`;const w=document.createElement('span');w.className='move-cell';w.textContent=hist[i];if(i===hist.length-1)w.classList.add('latest');row.append(no,w);if(hist[i+1]){const b=document.createElement('span');b.className='move-cell';b.textContent=hist[i+1];if(i+1===hist.length-1)b.classList.add('latest');row.append(b);} $('moves').append(row);}if(mode==='online'&&snap){$('code').textContent=onlineCode||snap.code;$('invite').classList.toggle('hidden',snap.started);$('onlineControls').classList.remove('hidden');$('resign').disabled=over||!snap.started;$('draw').disabled=over||!snap.started||snap.drawOfferBy===(side==='w'?'WHITE':'BLACK');const incoming=snap.drawOfferBy&&snap.drawOfferBy!==(side==='w'?'WHITE':'BLACK');$('acceptDraw').classList.toggle('hidden',!incoming);$('declineDraw').classList.toggle('hidden',!incoming);$('rematch').classList.toggle('hidden',!over);$('rematch').textContent=snap.rematchOfferBy&&snap.rematchOfferBy!==(side==='w'?'WHITE':'BLACK')?'Accept rematch':'Rematch';const link=`${location.origin}${location.pathname}?code=${encodeURIComponent(snap.code)}`;$('copy').onclick=()=>navigator.clipboard.writeText(link);$('share').onclick=()=>navigator.share?navigator.share({text:`Join my AirChess game: ${link} (code ${snap.code})`}):navigator.clipboard.writeText(link);if(/Android/i.test(navigator.userAgent)){$('openApp').href=`intent://join/${snap.code}#Intent;scheme=airchess;package=com.airchess;end`;$('openApp').classList.remove('hidden');}}else{$('onlineControls').classList.add('hidden');$('invite').classList.add('hidden');}if(over){$('resultTitle').textContent=resultText();$('resultReason').textContent='';$('gameOver').classList.remove('hidden');}else $('gameOver').classList.add('hidden');tickClocks();}
function drawHint2d() {
  boardEl.querySelector('.hint-arrow')?.remove();
  if (!hintMove || view3d) return;
  const from = boardEl.querySelector(`[data-square="${hintMove.slice(0, 2)}"]`);
  const to = boardEl.querySelector(`[data-square="${hintMove.slice(2, 4)}"]`);
  if (!from || !to) return;
  const bounds = boardEl.getBoundingClientRect(), start = from.getBoundingClientRect(), end = to.getBoundingClientRect();
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.classList.add('hint-arrow');
  svg.setAttribute('viewBox', `0 0 ${bounds.width} ${bounds.height}`);
  const defs = document.createElementNS(svg.namespaceURI, 'defs');
  const marker = document.createElementNS(svg.namespaceURI, 'marker');
  marker.setAttribute('id', 'hint-head');
  marker.setAttribute('markerWidth', '8');
  marker.setAttribute('markerHeight', '8');
  marker.setAttribute('refX', '6');
  marker.setAttribute('refY', '4');
  marker.setAttribute('orient', 'auto');
  const tip = document.createElementNS(svg.namespaceURI, 'path');
  tip.setAttribute('d', 'M0,0 L8,4 L0,8 z');
  tip.classList.add('hint-tip');
  marker.append(tip);
  defs.append(marker);
  svg.append(defs);
  const line = document.createElementNS(svg.namespaceURI, 'line');
  line.setAttribute('x1', `${start.left + start.width / 2 - bounds.left}`);
  line.setAttribute('y1', `${start.top + start.height / 2 - bounds.top}`);
  line.setAttribute('x2', `${end.left + end.width / 2 - bounds.left}`);
  line.setAttribute('y2', `${end.top + end.height / 2 - bounds.top}`);
  line.setAttribute('marker-end', 'url(#hint-head)');
  line.classList.add('hint-line');
  svg.append(line);
  boardEl.append(svg);
}
function tickClocks(){if(mode!=='online'||!snap)return;const elapsed=snap.clockRunning&&!snap.result?performance.now()-receivedAt:0;const wm=snap.whiteMillis-(chess.turn()==='w'?elapsed:0),bm=snap.blackMillis-(chess.turn()==='b'?elapsed:0);const fmt=x=>{const s=Math.max(0,Math.ceil(x/1000));return`${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;};$('myClock').textContent=fmt(side==='w'?wm:bm);$('oppClock').textContent=fmt(side==='w'?bm:wm);$('myClock').classList.toggle('active',snap.clockRunning&&!snap.result&&chess.turn()===side);$('oppClock').classList.toggle('active',snap.clockRunning&&!snap.result&&chess.turn()!==side);}
function cancelEngine() {
  searchGeneration++;
  pending = null;
  engineTask = null;
  if (worker) {
    worker.terminate();
    worker = null;
  }
  engineReady = false;
}
function runSearch(command) {
  worker.postMessage('ucinewgame');
  if (engineTask === 'computer') worker.postMessage(`setoption name Skill Level value ${{ Easy: 1, Medium: 7, Hard: 15, Max: 20 }[$('difficulty').value]}`);
  worker.postMessage(`position fen ${chess.fen()}`);
  worker.postMessage(command);
}
function engineFailure(message) {
  cancelEngine();
  $('hintText').textContent = '';
  $('statusDetail').textContent = `Stockfish unavailable: ${message || 'worker failed'}.`;
  $('continueLocal').classList.toggle('hidden', mode !== 'computer');
}
function ensureEngine(command, task) {
  if (task === 'hint' && (gameOver() || (mode === 'computer' && chess.turn() !== side))) return;
  if (worker && engineTask) cancelEngine();
  engineTask = task;
  pending = command;
  const generation = ++searchGeneration;
  const searchFen = chess.fen();
  const searchMode = mode;
  const searchTurn = chess.turn();
  if (!worker) {
    worker = new Worker('/play/vendor/stockfish/stockfish-19-lite-single.js');
  }
  const searchWorker = worker;
  const current = () => worker === searchWorker && engineTask === task && searchGeneration === generation;
  searchWorker.onmessage = event => {
    if (!current()) return;
    const line = String(event.data);
    if (line === 'uciok') searchWorker.postMessage('isready');
    if (line === 'readyok') {
      engineReady = true;
      if (pending) { const next = pending; pending = null; runSearch(next); }
    }
    if (line.startsWith('info') && line.includes('score')) {
      const score = line.match(/score (cp|mate) (-?\d+)/);
      if (score) $('hintText').dataset.eval = score[1] === 'mate' ? `mate ${score[2]}` : `${(Number(score[2]) / 100 * (searchTurn === 'w' ? 1 : -1)).toFixed(2)}`;
    }
    if (!line.startsWith('bestmove')) return;
    const move = line.split(/\s+/)[1];
    engineTask = null;
    if (!move || move === '(none)' || chess.fen() !== searchFen || mode !== searchMode) return;
    if (task === 'computer' && mode === 'computer' && chess.turn() !== side) {
      const played = chess.move({ from: move.slice(0, 2), to: move.slice(2, 4), promotion: move[4] || undefined });
      if (played) { lastMove = move; render(); }
      return;
    }
    if (task !== 'hint' || chess.turn() !== searchTurn || (mode === 'computer' && searchTurn !== side)) return;
    const suggested = chess.moves({ verbose: true }).find(candidate => candidate.from + candidate.to + (candidate.promotion || '') === move);
    if (!suggested) return;
    hintMove = move;
    $('hintText').textContent = `Suggested move: ${suggested.san}  Eval: ${$('hintText').dataset.eval || '—'}`;
    render();
  };
  searchWorker.onerror = event => {
    if (current()) engineFailure(event.message);
  };
  if (engineReady) {
    pending = null;
    runSearch(command);
  } else {
    searchWorker.postMessage('uci');
  }
}
function computerMove() { if (gameOver()) return; ensureEngine(`go depth ${MODE_DEPTH[$('difficulty').value]}`, 'computer'); }
function hint() {
  if (mode === 'online' || gameOver() || (mode === 'computer' && chess.turn() !== side)) return;
  $('hintText').dataset.eval = '';
  $('hintText').textContent = 'Finding a move…';
  ensureEngine(`go depth ${Number($('hintDepth').value)}`, 'hint');
}
$('hint').onclick = hint;
$('undo').onclick = () => {
  if (mode === 'online') { return; }
  cancelEngine();
  chess.undo();
  if (mode === 'computer' && chess.turn() !== side && chess.history().length) { chess.undo(); }
  lastMove = null;
  hintMove = null;
  selected = null;
  render();
};
$('reset').onclick = () => {
  if (mode === 'online') { return; }
  cancelEngine();
  chess = new Chess();
  lastMove = null;
  hintMove = null;
  selected = null;
  if (mode === 'computer' && side === 'b') { computerMove(); }
  render();
};
$('flip').onclick = () => { flipped = !flipped; render(); };
$('threats').onclick = () => {
  showThreats = !showThreats;
  $('threats').textContent = `Threats ${showThreats ? 'on' : 'off'}`;
  render();
};
$('viewToggle').onclick = async () => {
  $('viewToggle').disabled = true;
  try {
    if (!view3d) {
      try {
        board3d ??= await import('./three.js');
        await board3d.init3d(onSquare);
      } catch {
        $('statusDetail').textContent = '3D is unavailable. You can keep playing in 2D.';
        return;
      }
    }
    view3d = !view3d;
    boardEl.classList.toggle('hidden', view3d);
    $('threeBoard').classList.toggle('hidden', !view3d);
    $('viewToggle').textContent = view3d ? '2D view' : '3D view';
    if (view3d) { board3d.resize3d(); }
    render();
    if (camera.enabled) { await camera.replaceStream(captureBoard()); }
  } finally {
    $('viewToggle').disabled = false;
  }
};
$('home').onclick = showHome;
$('homeLink').onclick = event => { event.preventDefault(); showHome(); };
$('resign').onclick = () => send({ type: 'resign' });
$('draw').onclick = () => send({ type: 'offer_draw' });
$('acceptDraw').onclick = () => send({ type: 'respond_draw', accept: true });
$('declineDraw').onclick = () => send({ type: 'respond_draw', accept: false });
$('rematch').onclick = () => send(snap.rematchOfferBy && snap.rematchOfferBy !== (side === 'w' ? 'WHITE' : 'BLACK')
  ? { type: 'respond_rematch', accept: true } : { type: 'offer_rematch' });
$('leave').onclick = showHome;
$('newGame').onclick = () => {
  if (mode === 'online') { showHome(); } else { startLocal(mode === 'computer'); }
};
$('closeModal').onclick = showHome;
$('camera-board').onclick = showCameraStage;
$('rematchModal').onclick = () => {
  if (mode === 'online') $('rematch').click();
  else startLocal(mode === 'computer');
};
$('continueLocal').onclick = () => {
  cancelEngine();
  mode = 'local';
  selected = null;
  legal = [];
  hintMove = null;
  $('continueLocal').classList.add('hidden');
  $('statusDetail').textContent = '';
  render();
};
function showHome() {
  if (mode === 'online' && token && snap && !snap.result && !confirm('Leave this online game? The game may be forfeited.')) { return; }
  cancelEngine();
  selected = null;
  legal = [];
  pendingMove = null;
  hintMove = null;
  if (mode === 'online' && ws?.readyState === WebSocket.OPEN) {
    camera.stop();
    send({ type: 'leave' });
  }
  camera.setOnline(false);
  cameraStage = false;
  $('videoStage').classList.add('hidden');
  $('cameraControls').classList.add('hidden');
  ws?.close();
  clearOnline();
  mode = 'local';
  $('game').classList.add('hidden');
  $('lobby').classList.remove('hidden');
  $('gameOver').classList.add('hidden');
  $('promotion').classList.add('hidden');
  $('lobbyMessage').textContent = '';
  history.replaceState(null, '', location.pathname);
  $('resume').classList.add('hidden');
}
function restore(){let saved=null;try{const raw=localStorage.getItem('airchess.session');if(raw){const value=JSON.parse(raw);if(value&&typeof value.code==='string'&&/^[A-Z0-9]{4,12}$/.test(value.code.toUpperCase())&&typeof value.token==='string'&&value.token.length>0)saved={code:value.code.toUpperCase(),token:value.token};}}catch{}$('resume').classList.toggle('hidden',!saved);$('resumeGame').onclick=()=>{if(!saved)return;camera.setOnline(true);$('cameraControls').classList.remove('hidden');mode='online';onlineCode=saved.code;token=saved.token;showGame();connect({type:'resume',...saved});};if(queryCode&&$('name').value)$('joinGame').click();}
if(queryCode&&!$('name').value)$('name').focus();restore();window.addEventListener('resize',()=>{if(view3d)board3d.resize3d();else drawHint2d();});setInterval(tickClocks,200);
