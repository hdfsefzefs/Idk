const SCALE = 8;
const COLORS = { B: '#D97757', K: '#1a1a1a', Z: '#8a8a8a' };

// 15x8 pixel Clawd. B = body, K = eye, A/legs swap while walking.
const BODY = [
  '..BBBBBBBBBBB..',
  '..BBKBBBBBKBB..',
  '..BBKBBBBBKBB..',
  'BBBBBBBBBBBBBBB',
  '..BBBBBBBBBBB..',
  '..BBBBBBBBBBB..',
];
const LEGS_A = '...B.B...B.B...';
const LEGS_B = '..B.B.....B.B..';

const SAYS = ['Hellooo :3', '摸摸我~', '要寫 code 嗎?', '*揮舞鉗子*', '嘿嘿 :3', '肚子餓了…'];

const canvas = document.getElementById('c');
const ctx = canvas.getContext('2d');
const bubble = document.getElementById('bubble');

let state = 'idle';
let dir = 1;
let frame = 0;
let jump = 0;
let blinkUntil = 0;
let bubbleTimer = null;

function px(col, row, color) {
  ctx.fillStyle = color;
  ctx.fillRect(col * SCALE, row * SCALE, SCALE, SCALE);
}

function drawRows(rows, y0, mutate) {
  rows.forEach((line, r) => {
    for (let c = 0; c < line.length; c++) {
      let ch = line[c];
      if (ch === '.') continue;
      if (mutate) ch = mutate(ch, r, c);
      if (ch) px(c, y0 + r, COLORS[ch]);
    }
  });
}

function draw(now) {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.save();
  if (dir < 0) { ctx.translate(canvas.width, 0); ctx.scale(-1, 1); }

  const t = now / 1000;
  let bob = 0;
  if (state === 'idle') bob = Math.round(Math.sin(t * 3));
  if (state === 'walk') bob = frame % 2 === 0 ? 0 : -1;
  if (state === 'sleep') bob = Math.round(Math.sin(t * 1.2) * 0.6);
  if (state === 'drag') bob = -1;
  const lift = jump > 0 ? Math.round(Math.sin((jump / 20) * Math.PI) * 4) : 0;
  const y0 = 4 + bob - lift;

  const blinking = now < blinkUntil;
  const sleeping = state === 'sleep';
  drawRows(BODY, y0, (ch, r) => {
    if (ch === 'K' && (sleeping || blinking)) return r === 2 ? 'K' : 'B'; // squint
    return ch;
  });

  // arms wave while walking or dragged
  if (state === 'walk' || state === 'drag') {
    const up = frame % 2 === 0;
    px(0, y0 + (up ? 2 : 3), 'B'); px(14, y0 + (up ? 3 : 2), 'B');
  }

  const legs = state === 'walk' && frame % 2 ? LEGS_B : LEGS_A;
  drawRows([legs], y0 + BODY.length);
  ctx.restore();

  if (sleeping) {
    ctx.fillStyle = COLORS.Z;
    ctx.font = 'bold 14px monospace';
    const zf = Math.floor(t * 1.5) % 3;
    for (let i = 0; i <= zf; i++) ctx.fillText('z', 92 + i * 9, 18 - i * 8);
  }
}

function say(text, ms = 2200) {
  bubble.textContent = text;
  bubble.style.opacity = 1;
  clearTimeout(bubbleTimer);
  bubbleTimer = setTimeout(() => { bubble.style.opacity = 0; }, ms);
}

function loop(now) {
  if (jump > 0) jump--;
  if (now > blinkUntil + 2500 + Math.random() * 3000) blinkUntil = now + 140;
  draw(now);
  requestAnimationFrame(loop);
}

setInterval(() => { frame++; }, 180);

window.pet.onState((s) => { state = s.state; dir = s.dir; });

// click = poke, move = drag
let downAt = null;
canvas.addEventListener('mousedown', (e) => {
  if (e.button !== 0) return;
  downAt = { x: e.screenX, y: e.screenY };
  window.pet.dragStart();
});
window.addEventListener('mouseup', (e) => {
  if (!downAt) return;
  const moved = Math.hypot(e.screenX - downAt.x, e.screenY - downAt.y) > 4;
  downAt = null;
  window.pet.dragEnd();
  if (!moved) {
    window.pet.wake();
    jump = 20;
    say(SAYS[Math.floor(Math.random() * SAYS.length)]);
  }
});
canvas.addEventListener('contextmenu', (e) => { e.preventDefault(); window.pet.contextMenu(); });

requestAnimationFrame(loop);
