require('dotenv').config();
const express = require('express');
const path    = require('path');
const { PORT } = require('./config');

const app = express();
app.use(express.json());

// API routes — open, no auth (trust boundary is the LAN firewall + nginx)
app.use('/display', require('./routes/display'));
app.use('/books',   require('./routes/books'));
app.use('/admin',   require('./routes/admin'));
app.get('/health',  (_req, res) => res.json({ ok: true }));

// Serve PWA for direct LAN access (http://192.168.1.12:3000)
// When accessed via nginx (https://kijelzo.szeretet.ro), nginx serves public/ directly.
const publicDir = path.join(__dirname, '..', 'public');
app.use(express.static(publicDir));
app.get('*', (_req, res) => res.sendFile(path.join(publicDir, 'index.html')));

// Bind to localhost only — nginx handles external access on 80/443
app.listen(PORT, '127.0.0.1', () => {
  console.log(`[INFO] Kijelzo bridge on 127.0.0.1:${PORT}`);
  console.log(`[INFO] Direct access: http://192.168.1.12:${PORT}/`);
  console.log(`[INFO] Via nginx:     https://kijelzo.szeretet.ro/`);
  console.log(`[INFO] Status page:   http://127.0.0.1:${PORT}/admin`);
});
