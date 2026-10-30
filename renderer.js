const SCALE = 8;
const COLORS = { B: '#D97757', K: '#1a1a1a', Z: '#8a8a8a' };

// 15x6 pixel Clawd body (eyes are drawn separately so they can look around).
const BODY = [
  '..BBBBBBBBBBB..',
  '..BBBBBBBBBBB..',
  '..BBBBBBBBBBB..',
  'BBBBBBBBBBBBBBB',
  '..BBBBBBBBBBB..',
  '..BBBBBBBBBBB..',
];
const LEGS_A = '...B.B...B.B...';
const LEGS_B = '..B.B.....B.B..';
const EYE_COLS = [4, 10];

const POKES = ['Hellooo :3', '摸摸我~', '要寫 code 嗎?', '*揮舞鉗子*', '嘿嘿 :3', '肚子餓了…', '今天也要加油喔', '我是 Clawd!', '有 bug 嗎?我來看看'];
const ANNOYED = ['好啦好啦,別戳了…', '再戳我要夾你喔!', '欸欸欸~'];
const DIZZY = '暈~@_@';
const IDLE_CHAT = {
  morning: ['早安~', '今天要做什麼呢?'],
  noon: ['吃飯了嗎?', '午休一下吧'],
  evening: ['辛苦了~', '今天寫得順嗎?'],
  night: ['這麼晚了…該睡了喔', 'zzz…還不睡嗎?'],
};

const canvas = document.getElementById('c');
const ctx = canvas.getContext('2d');
const tCanvas = document.getElementById('t');
const tctx = tCanvas.getContext('2d');
const bubble = document.getElementById('bubble');

let character = 'clawd';
let state = 'idle';
let dir = 1;
let speed = 1.5;
let frame = 0;
let jump = 0;
let squashUntil = 0;
let squash = 0;
let blinkUntil = 0;
let nextBlink = 0;
let lookX = 0;
let lookUntil = 0;
let action = null; // { type: 'yawn' | 'wave' | 'dizzy', until }
let bubbleTimer = null;
let clicks = [];

const TUNG_POKES = ['Tung tung tung sahur!', '咚咚咚咚~起床啦!', '該吃 sahur 了!', '*揮球棒*', '咚!', 'Sahur sahur!', '別睡啦,咚咚咚!'];
const TUNG = { front: new Image(), side: new Image() };
TUNG.front.src = 'assets/tung/front.png';
TUNG.side.src = 'assets/tung/side.png';
const TUNG_H = 138; // drawn height in canvas px
const TUNG_FEET = 142; // y of the feet line inside the 160x150 canvas

function setCharacter(c) {
  character = c;
  canvas.style.display = c === 'clawd' ? 'block' : 'none';
  tCanvas.style.display = c === 'tung' ? 'block' : 'none';
  action = null;
}

const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

function px(col, row, color) {
  ctx.fillStyle = color;
  ctx.fillRect(col * SCALE, row * SCALE, SCALE, SCALE);
}

function drawRows(rows, y0) {
  rows.forEach((line, r) => {
    for (let c = 0; c < line.length; c++) {
      if (line[c] !== '.') px(c, y0 + r, COLORS[line[c]]);
    }
  });
}

function draw(now) {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.save();
  if (dir < 0) { ctx.translate(canvas.width, 0); ctx.scale(-1, 1); }

  // squash & stretch around the feet
  if (now < squashUntil) {
    const k = Math.min(1, (squashUntil - now) / 250);
    const sy = 1 - 0.25 * squash * k;
    ctx.translate(canvas.width / 2, canvas.height);
    ctx.scale(1 + (1 - sy) * 0.8, sy);
    ctx.translate(-canvas.width / 2, -canvas.height);
  }

  const t = now / 1000;
  const act = action && now < action.until ? action.type : null;
  let bob = 0;
  if (state === 'idle') bob = Math.round(Math.sin(t * 3));
  if (state === 'walk') bob = frame % 2 === 0 ? 0 : -1;
  if (state === 'sleep') bob = Math.round(Math.sin(t * 1.2) * 0.6);
  if (state === 'drag' || state === 'fall') bob = -1;
  const lift = jump > 0 ? Math.round(Math.sin((jump / 20) * Math.PI) * 4) : 0;
  const y0 = 4 + bob - lift;
  const sleeping = state === 'sleep';

  drawRows(BODY, y0);

  // eyes
  const blinking = now < blinkUntil;
  const airborne = state === 'drag' || state === 'fall';
  if (now > lookUntil) lookX = 0;
  EYE_COLS.forEach((c, i) => {
    let col = c + lookX;
    if (act === 'dizzy') col = c + (frame % 2 ? -1 : 1) * (i ? -1 : 1);
    if (sleeping || blinking || act === 'yawn') {
      px(col, y0 + 2, COLORS.K); // squint
    } else if (airborne) {
      px(col, y0 + 1, COLORS.K); px(col, y0 + 2, COLORS.K); // wide eyes
      px(col + (i ? 1 : -1), y0 + 1, COLORS.K);
    } else {
      px(col, y0 + 1, COLORS.K); px(col, y0 + 2, COLORS.K);
    }
  });

  // mouth
  if (act === 'yawn') { px(6, y0 + 4, COLORS.K); px(7, y0 + 4, COLORS.K); px(8, y0 + 4, COLORS.K); }
  else if (airborne || act === 'dizzy') px(7, y0 + 4, COLORS.K);

  // arms: wave, flap while walking, or flail in the air
  const flap = frame % 2 === 0;
  if (state === 'walk' || airborne || act === 'wave' || act === 'dizzy') {
    px(0, y0 + (flap ? 2 : 3), COLORS.B); px(14, y0 + (flap ? 3 : 2), COLORS.B);
    if (act === 'wave' || airborne) { px(0, y0 + (flap ? 1 : 2), COLORS.B); px(14, y0 + (flap ? 2 : 1), COLORS.B); }
  }

  const legs = (state === 'walk' || airborne) && frame % 2 ? LEGS_B : LEGS_A;
  drawRows([legs], y0 + BODY.length);
  ctx.restore();

  if (sleeping) {
    ctx.fillStyle = COLORS.Z;
    ctx.font = 'bold 14px monospace';
    const zf = Math.floor(t * 1.5) % 3;
    for (let i = 0; i <= zf; i++) ctx.fillText('z', 92 + i * 9, 18 - i * 8);
  }
  if (act === 'dizzy') {
    ctx.fillStyle = '#e6b800';
    ctx.font = 'bold 14px monospace';
    const a = t * 6;
    for (let i = 0; i < 3; i++) {
      const ang = a + (i * Math.PI * 2) / 3;
      ctx.fillText('★', 52 + Math.cos(ang) * 30, 22 + Math.sin(ang) * 6);
    }
  }
}

// Static mesh render, so every state is a transform of the front/side sprite.
function drawTung(now) {
  tctx.clearRect(0, 0, tCanvas.width, tCanvas.height);
  const t = now / 1000;
  const act = action && now < action.until ? action.type : '';
  const airborne = state === 'drag' || state === 'fall';
  const useSide = state === 'walk';
  const img = useSide ? TUNG.side : TUNG.front;
  if (!img.complete || !img.naturalWidth) return;
  const w = TUNG_H * img.naturalWidth / img.naturalHeight;

  let rot = 0, sy = 1, dx = 0, dy = 0;
  if (state === 'idle') {
    sy = 1 + 0.012 * Math.sin(t * 3);
    rot = 0.015 * Math.sin(t * 1.5);
    if (now < lookUntil) dx = lookX * 3;
    if (act === 'wave') rot = 0.14 * Math.sin(t * 10);
    if (act === 'yawn') sy = 1.05;
  } else if (state === 'walk') {
    dy = frame % 2 ? -4 : 0;
    rot = (frame % 2 ? 1 : -1) * 0.05 + 0.04;
  } else if (state === 'sleep') {
    rot = 0.2 + 0.02 * Math.sin(t * 1.2);
  } else if (airborne) {
    rot = 0.25 * Math.sin(t * 14);
  }
  if (act === 'dizzy') rot = 0.3 * Math.sin(t * 9);
  if (jump > 0) dy -= Math.sin((jump / 20) * Math.PI) * 18;

  let sx = 1;
  if (now < squashUntil) {
    const k = Math.min(1, (squashUntil - now) / 250);
    sy *= 1 - 0.25 * squash * k;
    sx = 1 + (1 - (1 - 0.25 * squash * k)) * 0.8;
  }

  tctx.save();
  tctx.translate(tCanvas.width / 2 + dx, TUNG_FEET + dy);
  if (useSide) tctx.scale(dir, 1);
  tctx.rotate(rot);
  tctx.scale(sx, sy);
  tctx.drawImage(img, -w / 2, -TUNG_H, w, TUNG_H);
  tctx.restore();

  if (state === 'sleep') {
    tctx.fillStyle = COLORS.Z;
    tctx.font = 'bold 14px monospace';
    const zf = Math.floor(t * 1.5) % 3;
    for (let i = 0; i <= zf; i++) tctx.fillText('z', 124 + i * 8, 34 - i * 8);
  }
  if (act === 'dizzy') {
    tctx.fillStyle = '#e6b800';
    tctx.font = 'bold 14px monospace';
    for (let i = 0; i < 3; i++) {
      const ang = t * 6 + (i * Math.PI * 2) / 3;
      tctx.fillText('★', 72 + Math.cos(ang) * 30, 20 + Math.sin(ang) * 6);
    }
  }
}

function say(text, ms = 2200) {
  bubble.textContent = text;
  bubble.style.opacity = 1;
  clearTimeout(bubbleTimer);
  bubbleTimer = setTimeout(() => { bubble.style.opacity = 0; }, ms);
}

function timeOfDayLine() {
  if (character === 'tung' && Math.random() < 0.5) return pick(TUNG_POKES);
  const h = new Date().getHours();
  const slot = h >= 5 && h < 11 ? 'morning' : h < 14 && h >= 11 ? 'noon' : h >= 14 && h < 20 ? 'evening' : 'night';
  return pick(IDLE_CHAT[slot]);
}

function startAction(type, ms) {
  action = { type, until: performance.now() + ms };
}

function loop(now) {
  if (jump > 0) jump--;
  if (now > nextBlink) {
    blinkUntil = now + 140;
    nextBlink = now + 2500 + Math.random() * 3000;
  }
  if (character === 'tung') drawTung(now); else draw(now);
  requestAnimationFrame(loop);
}

// animation frame ticker: faster when running
let frameTimer = null;
function restartFrameTimer() {
  clearInterval(frameTimer);
  frameTimer = setInterval(() => { frame++; }, speed > 2.5 && state === 'walk' ? 90 : 180);
}

// idle personality: look around, yawn, wave, chat
setInterval(() => {
  if (state !== 'idle') return;
  const r = Math.random();
  const now = performance.now();
  if (r < 0.35) { lookX = pick([-1, 1]); lookUntil = now + 1200; }
  else if (r < 0.5) startAction('yawn', 1400);
  else if (r < 0.65) startAction('wave', 1600);
  else if (r < 0.75) say(timeOfDayLine(), 2800);
}, 2500);

window.pet.onState((s) => {
  const prev = state;
  state = s.state; dir = s.dir; speed = s.speed || 1.5;
  restartFrameTimer();
  if (state === 'drag' && Math.random() < 0.5) say(pick(['哇!', '放我下來~', '飛起來了!']), 1500);
  if (prev === 'sleep' && state !== 'sleep') action = null;
});
window.pet.onCharacter(setCharacter);
window.pet.onLand(({ impact }) => {
  squash = Math.min(1, impact / 20);
  squashUntil = performance.now() + 250;
  if (impact > 18) say('好痛…', 1500);
});
window.pet.onHop(() => { jump = 16; });

// click = poke, move = drag
let downAt = null;
for (const el of [canvas, tCanvas]) el.addEventListener('mousedown', (e) => {
  if (e.button !== 0) return;
  downAt = { x: e.screenX, y: e.screenY };
  window.pet.dragStart();
});
window.addEventListener('mouseup', (e) => {
  if (!downAt) return;
  const moved = Math.hypot(e.screenX - downAt.x, e.screenY - downAt.y) > 4;
  downAt = null;
  window.pet.dragEnd();
  if (moved) return;

  const now = performance.now();
  clicks = clicks.filter((t) => now - t < 2000);
  clicks.push(now);
  window.pet.wake();
  jump = 20;
  if (clicks.length >= 6) {
    clicks = [];
    startAction('dizzy', 2600);
    say(DIZZY, 2600);
  } else if (clicks.length >= 3) {
    say(pick(ANNOYED));
  } else {
    say(pick(character === 'tung' ? TUNG_POKES : POKES));
  }
});
for (const el of [canvas, tCanvas]) el.addEventListener('contextmenu', (e) => { e.preventDefault(); window.pet.contextMenu(); });

restartFrameTimer();
requestAnimationFrame(loop);
