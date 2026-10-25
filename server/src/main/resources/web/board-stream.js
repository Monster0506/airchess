const CAPTURE_SIZE = 640;
const KEEPALIVE_MS = 1000;
let canvas, track;

export function shareBoard(board) {
  const stream = draw(board).captureStream(0);
  const current = stream.getVideoTracks()[0];
  track = current;
  current.requestFrame();
  // Canvas capture emits only on change; late-opened remote views need a recent frame.
  const timer = setInterval(() => {
    if (track !== current || current.readyState === 'ended') {
      clearInterval(timer);
      return;
    }
    refreshBoardStream(board);
  }, KEEPALIVE_MS);
  return stream;
}

export function refreshBoardStream(board) {
  if (!track || track.readyState === 'ended') { return; }
  draw(board);
  track.requestFrame();
}

function draw(board) {
  canvas ??= Object.assign(document.createElement('canvas'), { width: CAPTURE_SIZE, height: CAPTURE_SIZE });
  const context = canvas.getContext('2d');
  if (!context) { throw new Error('Board sharing is unavailable in this browser.'); }
  const bounds = board.getBoundingClientRect();
  const scale = CAPTURE_SIZE / bounds.width;
  context.clearRect(0, 0, CAPTURE_SIZE, CAPTURE_SIZE);

  for (const square of board.querySelectorAll('.square')) {
    const rect = square.getBoundingClientRect();
    const x = (rect.left - bounds.left) * scale, y = (rect.top - bounds.top) * scale;
    context.fillStyle = getComputedStyle(square).backgroundColor;
    context.fillRect(x, y, rect.width * scale, rect.height * scale);
    const piece = square.querySelector('.piece');
    if (piece) {
      const style = getComputedStyle(piece);
      context.font = `${style.fontWeight} ${parseFloat(style.fontSize) * scale}px ${style.fontFamily}`;
      context.textAlign = 'center';
      context.textBaseline = 'middle';
      context.fillStyle = style.color;
      const stroke = parseFloat(style.webkitTextStrokeWidth) * scale;
      if (stroke) {
        context.lineWidth = stroke;
        context.strokeStyle = style.webkitTextStrokeColor;
        context.strokeText(piece.textContent, x + rect.width * scale / 2, y + rect.height * scale / 2);
      }
      context.fillText(piece.textContent, x + rect.width * scale / 2, y + rect.height * scale / 2);
    }
    for (const marker of square.querySelectorAll('.legal-dot, .capture-ring')) {
      const position = marker.getBoundingClientRect(), style = getComputedStyle(marker);
      context.beginPath();
      context.arc((position.left + position.width / 2 - bounds.left) * scale, (position.top + position.height / 2 - bounds.top) * scale, position.width * scale / 2, 0, Math.PI * 2);
      if (marker.classList.contains('capture-ring')) {
        context.strokeStyle = style.borderTopColor;
        context.lineWidth = parseFloat(style.borderTopWidth) * scale;
        context.stroke();
      } else {
        context.fillStyle = style.backgroundColor;
        context.fill();
      }
    }
    for (const label of square.querySelectorAll('.coords')) {
      const position = label.getBoundingClientRect(), style = getComputedStyle(label);
      context.font = `${style.fontWeight} ${parseFloat(style.fontSize) * scale}px ${style.fontFamily}`;
      context.textAlign = 'left';
      context.textBaseline = 'top';
      context.fillStyle = style.color;
      context.fillText(label.textContent, (position.left - bounds.left) * scale, (position.top - bounds.top) * scale);
    }
  }

  const hint = board.querySelector('.hint-line');
  if (hint) {
    const style = getComputedStyle(hint);
    const x1 = Number(hint.getAttribute('x1')) * scale, y1 = Number(hint.getAttribute('y1')) * scale;
    const x2 = Number(hint.getAttribute('x2')) * scale, y2 = Number(hint.getAttribute('y2')) * scale;
    const width = parseFloat(style.strokeWidth) * scale;
    context.strokeStyle = style.stroke;
    context.fillStyle = style.stroke;
    context.lineWidth = width;
    context.lineCap = 'round';
    context.beginPath();
    context.moveTo(x1, y1);
    context.lineTo(x2, y2);
    context.stroke();
    context.save();
    context.translate(x2, y2);
    context.rotate(Math.atan2(y2 - y1, x2 - x1));
    context.beginPath();
    context.moveTo(2 * width, 0);
    context.lineTo(-6 * width, -4 * width);
    context.lineTo(-6 * width, 4 * width);
    context.closePath();
    context.fill();
    context.restore();
  }
  return canvas;
}
