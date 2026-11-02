const express = require('express');
const router = express.Router();
const axios  = require('axios');
const { SOAP_URL, PORT } = require('../config');

const ACTIVATION_URL = new URL('/activation', SOAP_URL).href;

const escapeHtml = (s) => String(s).replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

async function getActivation() {
  try {
    const { data } = await axios.get(ACTIVATION_URL, { timeout: 2000 });
    if (data.activated) return { text: 'activated', color: '#4ade80', data };
    return { text: 'waiting for activation key', color: '#fbbf24', data };
  } catch (err) {
    if (err.response && err.response.status === 404) {
      return { text: 'unknown (server version without activation)', color: '#6b7280' };
    }
    return { text: 'server offline', color: '#f87171' };
  }
}

const RESULT_MESSAGES = {
  ok:      { text: 'Activated. The display is starting.', color: '#4ade80' },
  invalid: { text: 'Invalid activation key for this device.', color: '#f87171' },
  error:   { text: 'Could not reach the display server.', color: '#f87171' },
};

router.post('/activate', express.urlencoded({ extended: false, limit: '2kb' }), async (req, res) => {
  const key = String((req.body && req.body.key) || '').trim().slice(0, 300);
  let result = 'error';
  try {
    await axios.post(ACTIVATION_URL, key, {
      timeout: 5000,
      headers: { 'Content-Type': 'text/plain' },
    });
    result = 'ok';
  } catch (err) {
    if (err.response && err.response.status === 400) result = 'invalid';
  }
  res.redirect(303, `${req.baseUrl}?activation=${result}`);
});

router.get('/', async (req, res) => {
  const message = RESULT_MESSAGES[req.query.activation];
  let soapStatus = 'unknown';
  let soapColor  = '#6b7280';
  const [, activation] = await Promise.all([
    axios.get(SOAP_URL, { timeout: 2000 }).then(() => {
      soapStatus = 'reachable';
      soapColor  = '#4ade80';
    }).catch((err) => {
      soapStatus = err.code === 'ECONNREFUSED' ? 'not running' : 'unreachable';
      soapColor  = '#f87171';
    }),
    getActivation(),
  ]);
  const device = activation.data
    ? `${escapeHtml(activation.data.deviceId)} (${escapeHtml(activation.data.platform)})`
    : '';

  res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Kijelzo Status</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: sans-serif; background: #111827; color: #f9fafb;
           display: flex; align-items: center; justify-content: center;
           min-height: 100vh; }
    .card { background: #1f2937; border-radius: 12px; padding: 2rem;
            max-width: 380px; width: 100%; }
    h1 { font-size: 1.1rem; color: #9ca3af; margin-bottom: 1.5rem; }
    .row { display: flex; justify-content: space-between; align-items: center;
           padding: .6rem 0; border-bottom: 1px solid #374151; }
    .row:last-child { border: none; }
    .label { font-size: .85rem; color: #9ca3af; }
    .value { font-size: .9rem; font-weight: 600; }
    .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%;
           margin-right: .4rem; }
    .msg { font-size: .85rem; font-weight: 600; margin-bottom: 1rem; }
    form { margin-top: 1.25rem; display: flex; flex-direction: column; gap: .6rem; }
    form label { font-size: .85rem; color: #9ca3af; }
    form textarea { width: 100%; min-height: 4.5rem; padding: .6rem; border-radius: 8px;
           border: 1px solid #374151; background: #111827; color: #f9fafb;
           font-family: monospace; font-size: .85rem; resize: vertical; }
    form button { padding: .7rem; border: none; border-radius: 8px; background: #fbbf24;
           color: #111827; font-weight: 700; font-size: .95rem; cursor: pointer; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Kijelzo Server Status</h1>
    ${message ? `<p class="msg" style="color:${message.color}">${message.text}</p>` : ''}
    <div class="row">
      <span class="label">Java SOAP server</span>
      <span class="value">
        <span class="dot" style="background:${soapColor}"></span>${soapStatus}
      </span>
    </div>
    <div class="row">
      <span class="label">Activation</span>
      <span class="value">
        <span class="dot" style="background:${activation.color}"></span>${activation.text}
      </span>
    </div>
    ${device ? `<div class="row">
      <span class="label">Device ID</span>
      <span class="value" style="font-size:.8rem;word-break:break-all;user-select:all">${device}</span>
    </div>` : ''}
    <div class="row">
      <span class="label">SOAP endpoint</span>
      <span class="value" style="font-size:.8rem;word-break:break-all">${SOAP_URL}</span>
    </div>
    <div class="row">
      <span class="label">REST bridge port</span>
      <span class="value">${PORT}</span>
    </div>
    <div class="row">
      <span class="label">PWA address</span>
      <span class="value" style="font-size:.8rem">http://192.168.1.12:${PORT}</span>
    </div>
    ${activation.data && !activation.data.activated ? `<form method="post" action="${req.baseUrl}/activate">
      <label for="key">Activation key</label>
      <textarea id="key" name="key" required maxlength="300" autocomplete="off" spellcheck="false"
                placeholder="XXXXX-XXXXX-XXXXX-…"></textarea>
      <button type="submit">Activate</button>
    </form>` : ''}
  </div>
</body>
</html>`);
});

module.exports = router;
