const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('pet', {
  onState: (cb) => ipcRenderer.on('state', (_e, s) => cb(s)),
  onLand: (cb) => ipcRenderer.on('land', (_e, d) => cb(d)),
  onHop: (cb) => ipcRenderer.on('hop', () => cb()),
  dragStart: () => ipcRenderer.send('drag-start'),
  dragEnd: () => ipcRenderer.send('drag-end'),
  wake: () => ipcRenderer.send('wake'),
  contextMenu: () => ipcRenderer.send('context-menu'),
});
