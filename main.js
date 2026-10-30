const { app, BrowserWindow, ipcMain, screen, Menu } = require('electron');
const path = require('path');
const fs = require('fs');

const WIN_W = 200;
const WIN_H = 210;
const TICK_MS = 33;
const GRAVITY = 1.2;

let win;
let state = 'idle'; // idle | walk | sleep | drag | fall
let dir = 1;
let speed = 1.5;
let vx = 0;
let vy = 0;
let lastDrag = null;
let x = 200;
let y = 0;
let stateUntil = 0;
let dragOffset = null;
let character = 'clawd'; // clawd | tung

const settingsFile = () => path.join(app.getPath('userData'), 'settings.json');

function loadSettings() {
  try {
    const s = JSON.parse(fs.readFileSync(settingsFile(), 'utf8'));
    if (s.character === 'clawd' || s.character === 'tung') character = s.character;
  } catch (_) { /* first run: defaults */ }
}

function setCharacter(c) {
  character = c;
  try { fs.writeFileSync(settingsFile(), JSON.stringify({ character })); } catch (_) { /* not fatal */ }
  win.webContents.send('character', character);
}

function workArea() {
  return screen.getDisplayNearestPoint({ x: Math.round(x), y: Math.round(y) }).workArea;
}

function groundY() {
  const wa = workArea();
  return wa.y + wa.height - WIN_H + 6;
}

function setState(next, durationMs) {
  state = next;
  stateUntil = Date.now() + durationMs;
  sendState();
}

function sendState() {
  win.webContents.send('state', { state, dir, speed });
}

function pickNext() {
  const r = Math.random();
  if (r < 0.5) {
    dir = Math.random() < 0.5 ? -1 : 1;
    speed = Math.random() < 0.25 ? 4 : 1 + Math.random() * 1.5; // sometimes a little run
    setState('walk', 2000 + Math.random() * 4000);
  } else if (r < 0.8) {
    setState('idle', 2000 + Math.random() * 3000);
  } else {
    setState('sleep', 6000 + Math.random() * 8000);
  }
}

function tick() {
  if (!win || win.isDestroyed()) return;
  const now = Date.now();
  const wa = workArea();
  const gy = groundY();
  const maxX = wa.x + wa.width - WIN_W;

  if (state === 'drag') {
    const c = screen.getCursorScreenPoint();
    const nx = c.x - dragOffset.x;
    const ny = c.y - dragOffset.y;
    // smoothed release velocity so a flick throws Clawd
    if (lastDrag) {
      vx = vx * 0.5 + (nx - lastDrag.x) * 0.5;
      vy = vy * 0.5 + (ny - lastDrag.y) * 0.5;
    }
    lastDrag = { x: nx, y: ny };
    x = nx;
    y = ny;
  } else if (state === 'fall') {
    vy += GRAVITY;
    x += vx;
    y += vy;
    if (x < wa.x) { x = wa.x; vx = -vx * 0.6; }
    if (x > maxX) { x = maxX; vx = -vx * 0.6; }
    if (y < wa.y) { y = wa.y; vy = Math.abs(vy) * 0.5; }
    if (y >= gy) {
      y = gy;
      const impact = Math.abs(vy);
      win.webContents.send('land', { impact });
      if (impact > 7) {
        vy = -impact * 0.45;
        vx *= 0.7;
      } else {
        vx = vy = 0;
        setState('idle', 1500);
      }
    }
  } else {
    if (y < gy) {
      // dropped from rest: start falling
      vx = vy = 0;
      setState('fall', 0);
    } else if (now > stateUntil) {
      pickNext();
    }

    if (state === 'walk') {
      x += speed * dir;
      if (x < wa.x) { x = wa.x; dir = 1; sendState(); }
      if (x > maxX) { x = maxX; dir = -1; sendState(); }
      if (Math.random() < 0.004) win.webContents.send('hop');
    }
  }
  win.setPosition(Math.round(x), Math.round(y));
}

function createWindow() {
  const wa = screen.getPrimaryDisplay().workArea;
  x = wa.x + Math.round(wa.width / 3);
  y = groundY();

  win = new BrowserWindow({
    width: WIN_W,
    height: WIN_H,
    x: Math.round(x),
    y: Math.round(y),
    transparent: true,
    frame: false,
    resizable: false,
    hasShadow: false,
    alwaysOnTop: true,
    skipTaskbar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
    },
  });
  win.setAlwaysOnTop(true, 'screen-saver');
  win.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  win.loadFile('index.html');
  win.webContents.once('did-finish-load', () => {
    win.webContents.send('character', character);
    setState('idle', 1500);
  });
  setInterval(tick, TICK_MS);
}

ipcMain.on('drag-start', () => {
  const c = screen.getCursorScreenPoint();
  dragOffset = { x: c.x - x, y: c.y - y };
  lastDrag = null;
  vx = vy = 0;
  setState('drag', 0);
});

ipcMain.on('drag-end', () => {
  if (state !== 'drag') return;
  vx = Math.max(-40, Math.min(40, vx));
  vy = Math.max(-40, Math.min(40, vy));
  const gy = groundY();
  if (y < gy - 2 || Math.hypot(vx, vy) > 3) setState('fall', 0);
  else setState('idle', 1500);
});

ipcMain.on('wake', () => {
  if (state === 'sleep') setState('idle', 2000);
});

ipcMain.on('context-menu', () => {
  Menu.buildFromTemplate([
    { label: '角色 Character', submenu: [
      { label: 'Clawd', type: 'radio', checked: character === 'clawd', click: () => setCharacter('clawd') },
      { label: 'Tung Tung Tung Sahur', type: 'radio', checked: character === 'tung', click: () => setCharacter('tung') },
    ] },
    { type: 'separator' },
    { label: '睡覺 Sleep', click: () => setState('sleep', 15000) },
    { label: '走走 Walk', click: () => { dir = Math.random() < 0.5 ? -1 : 1; speed = 1.5; setState('walk', 6000); } },
    { type: 'separator' },
    { label: '再見 Quit', click: () => app.quit() },
  ]).popup({ window: win });
});

app.whenReady().then(() => {
  loadSettings();
  createWindow();
});
app.on('window-all-closed', () => app.quit());
