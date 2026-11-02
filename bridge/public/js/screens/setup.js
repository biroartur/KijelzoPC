import { setServerUrl } from '../api.js';

export function renderSetup(app, onConnected) {
  app.innerHTML = `
    <div class="pair-screen">
      <div class="pair-logo">✝</div>
      <div class="pair-title">Kijelző</div>

      <div class="pair-card">
        <div>
          <label style="font-size:.85rem;color:var(--text-dim);margin-bottom:.5rem;display:block">
            Server address
          </label>
          <input type="url" id="serverInput"
            placeholder="http://192.168.1.12:3000"
            value="${localStorage.getItem('kj_server') || ''}"
            style="width:100%;background:var(--bg);border:1px solid var(--border);
                   border-radius:8px;color:var(--text);padding:.7rem .9rem;
                   font-size:1rem;outline:none;">
        </div>

        <div class="pair-error" id="setupError"></div>
        <button class="btn btn-primary" id="connectBtn">Connect</button>

        <div id="httpsWarning" style="display:none;background:#1c1917;border:1px solid #78350f;
             border-radius:8px;padding:.85rem;font-size:.8rem;color:#fbbf24;line-height:1.5">
          <strong>Note:</strong> This app is loaded over HTTPS but the server address you entered
          uses HTTP. Browsers block such requests for security.<br><br>
          <strong>Solution:</strong> Open <span id="localUrl" style="font-family:monospace"></span>
          directly in Safari — the app will work there with no restrictions.
        </div>
      </div>
    </div>
  `;

  const input = document.getElementById('serverInput');
  const btn   = document.getElementById('connectBtn');
  const error = document.getElementById('setupError');
  const warn  = document.getElementById('httpsWarning');

  input.addEventListener('focus', () => {
    input.style.borderColor = 'var(--accent)';
  });
  input.addEventListener('blur', () => {
    input.style.borderColor = 'var(--border)';
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') btn.click();
  });

  btn.addEventListener('click', async () => {
    error.textContent = '';
    warn.style.display = 'none';

    const raw = input.value.trim().replace(/\/$/, '');
    if (!raw) { error.textContent = 'Please enter the server address'; return; }

    let url;
    try { url = new URL(raw); } catch {
      error.textContent = 'Invalid URL — example: http://192.168.1.12:3000';
      return;
    }

    // Detect mixed-content: HTTPS page → HTTP server
    if (window.location.protocol === 'https:' && url.protocol === 'http:') {
      document.getElementById('localUrl').textContent = raw;
      warn.style.display = 'block';
      return;
    }

    btn.disabled = true;
    btn.textContent = 'Connecting…';

    try {
      const res = await fetch(raw + '/health', { signal: AbortSignal.timeout(4000) });
      const data = await res.json();
      if (data.ok) {
        setServerUrl(raw);
        onConnected();
      } else {
        throw new Error('bad response');
      }
    } catch {
      error.textContent = 'Could not reach the server — check the address and your Wi-Fi';
      btn.disabled = false;
      btn.textContent = 'Connect';
    }
  });
}
