const { app, BrowserWindow, ipcMain, screen, Menu } = require('electron');
const path = require('path');

const WIN_W = 200;
const WIN_H = 150;
const TICK_MS = 33;
const SPEED = 1.5;

let win;
let state = 'idle'; // idle | walk | sleep | drag
let dir = 1;
let x = 200;
let y = 0;
let stateUntil = 0;
let dragOffset = null;

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
  win.webContents.send('state', { state, dir });
}

function pickNext() {
  const r = Math.random();
  if (r < 0.5) {
    dir = Math.random() < 0.5 ? -1 : 1;
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

  if (state === 'drag') {
    const c = screen.getCursorScreenPoint();
    x = c.x - dragOffset.x;
    y = c.y - dragOffset.y;
  } else {
    // fall back down to the ground after being dropped
    const gy = groundY();
    if (y < gy) y = Math.min(gy, y + 12);
    if (y >= gy && now > stateUntil) pickNext();

    if (state === 'walk' && y >= gy) {
      const wa = workArea();
      x += SPEED * dir;
      if (x < wa.x) { x = wa.x; dir = 1; win.webContents.send('state', { state, dir }); }
      if (x > wa.x + wa.width - WIN_W) { x = wa.x + wa.width - WIN_W; dir = -1; win.webContents.send('state', { state, dir }); }
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
  win.webContents.once('did-finish-load', () => setState('idle', 1500));
  setInterval(tick, TICK_MS);
}

ipcMain.on('drag-start', () => {
  const c = screen.getCursorScreenPoint();
  dragOffset = { x: c.x - x, y: c.y - y };
  setState('drag', 0);
});

ipcMain.on('drag-end', () => {
  if (state === 'drag') setState('idle', 1500);
});

ipcMain.on('wake', () => {
  if (state === 'sleep') setState('idle', 2000);
});

ipcMain.on('context-menu', () => {
  Menu.buildFromTemplate([
    { label: '睡覺 Sleep', click: () => setState('sleep', 15000) },
    { label: '走走 Walk', click: () => { dir = Math.random() < 0.5 ? -1 : 1; setState('walk', 6000); } },
    { type: 'separator' },
    { label: '再見 Quit', click: () => app.quit() },
  ]).popup({ window: win });
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
