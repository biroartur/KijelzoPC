import { apiFetch, setServerUrl, getServerUrl } from '../api.js';
import { KNOWN_SERVERS } from '../servers.js';

export function renderPair(app, onPaired) {
  const savedServer = getServerUrl();

  app.innerHTML = `
    <div class="pair-screen">
      <div class="pair-logo">✝</div>
      <div>
        <div class="pair-title">Kijelző</div>
      </div>

      <div class="pair-card">
        <!-- Step 1: choose server -->
        <div>
          <label style="font-size:.85rem;color:var(--text-dim);margin-bottom:.5rem;display:block">Server address</label>
          <div class="server-options" id="serverOptions">
            ${KNOWN_SERVERS.map((s, i) => `
              <button class="server-opt ${savedServer === s.url ? 'active' : ''}" data-url="${s.url}" data-idx="${i}">
                ${s.label}
              </button>
            `).join('')}
            <button class="server-opt ${!KNOWN_SERVERS.some(s => s.url === savedServer) ? 'active' : ''}" data-idx="custom">
              Local / custom…
            </button>
          </div>
          <input type="url" id="serverCustom" class="server-custom"
            placeholder="http://192.168.1.12:3000"
            value="${!KNOWN_SERVERS.some(s => s.url === savedServer) ? (savedServer || '') : ''}"
            style="display:${!KNOWN_SERVERS.some(s => s.url === savedServer) ? 'block' : 'none'}">
          <div class="pair-error" id="serverError" style="margin-top:.4rem"></div>
        </div>

        <hr style="border:none;border-top:1px solid var(--border)">

        <!-- Step 2: enter pairing code -->
        <p class="pair-hint" style="margin:0">Enter the 4-character code shown on the church computer</p>
        <div class="code-inputs" id="codeInputs">
          <input class="code-char" maxlength="1" autocomplete="off" autocorrect="off"
            spellcheck="false" inputmode="text" id="c0">
          <input class="code-char" maxlength="1" autocomplete="off" autocorrect="off"
            spellcheck="false" inputmode="text" id="c1">
          <input class="code-char" maxlength="1" autocomplete="off" autocorrect="off"
            spellcheck="false" inputmode="text" id="c2">
          <input class="code-char" maxlength="1" autocomplete="off" autocorrect="off"
            spellcheck="false" inputmode="text" id="c3">
        </div>
        <div class="pair-error" id="pairError"></div>
        <button class="btn btn-primary" id="pairBtn" disabled>Connect</button>
      </div>
    </div>
  `;

  addServerStyles();

  const inputs = [0, 1, 2, 3].map((i) => document.getElementById(`c${i}`));
  const btn = document.getElementById('pairBtn');
  const pairError = document.getElementById('pairError');
  const serverError = document.getElementById('serverError');
  const customInput = document.getElementById('serverCustom');
  let showingCustom = !KNOWN_SERVERS.some(s => s.url === savedServer);

  // ── Server selection ──────────────────────────────
  document.getElementById('serverOptions').addEventListener('click', (e) => {
    const opt = e.target.closest('.server-opt');
    if (!opt) return;

    document.querySelectorAll('.server-opt').forEach(el => el.classList.remove('active'));
    opt.classList.add('active');

    if (opt.dataset.idx === 'custom') {
      showingCustom = true;
      customInput.style.display = 'block';
      customInput.focus();
    } else {
      showingCustom = false;
      customInput.style.display = 'none';
    }
    serverError.textContent = '';
    checkReady();
  });

  customInput.addEventListener('input', () => {
    serverError.textContent = '';
    checkReady();
  });

  // ── Code inputs ───────────────────────────────────
  function getCode() {
    return inputs.map((el) => el.value.toUpperCase()).join('');
  }

  function getSelectedServer() {
    if (showingCustom) return customInput.value.trim();
    const active = document.querySelector('.server-opt.active');
    return active && active.dataset.url ? active.dataset.url : '';
  }

  function checkReady() {
    btn.disabled = getCode().length < 4 || !getSelectedServer();
  }

  inputs.forEach((input, i) => {
    input.addEventListener('input', (e) => {
      const val = e.target.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
      input.value = val ? val[val.length - 1] : '';
      if (input.value && i < 3) inputs[i + 1].focus();
      checkReady();
      pairError.textContent = '';
    });

    input.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !input.value && i > 0) {
        inputs[i - 1].focus();
        inputs[i - 1].value = '';
        checkReady();
      }
      if (e.key === 'Enter') btn.click();
    });

    input.addEventListener('paste', (e) => {
      e.preventDefault();
      const text = (e.clipboardData || window.clipboardData)
        .getData('text').replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, 4);
      text.split('').forEach((ch, idx) => { if (inputs[idx]) inputs[idx].value = ch; });
      inputs[Math.min(text.length, 3)].focus();
      checkReady();
    });
  });

  // ── Connect ───────────────────────────────────────
  btn.addEventListener('click', async () => {
    const code = getCode();
    const serverUrl = getSelectedServer();

    if (!serverUrl) { serverError.textContent = 'Please choose a server'; return; }
    if (code.length < 4) return;

    // Basic URL validation
    try { new URL(serverUrl); } catch {
      serverError.textContent = 'Invalid URL — example: http://192.168.1.12:3000';
      return;
    }

    // Warn about mixed content if PWA is HTTPS but server is HTTP
    if (window.location.protocol === 'https:' && serverUrl.startsWith('http:')) {
      serverError.textContent =
        'Warning: this browser may block HTTP calls from an HTTPS page. ' +
        'Open the server URL directly in your browser to use it locally.';
      // Still let them try — some browsers allow it on LAN
    }

    btn.disabled = true;
    btn.textContent = 'Connecting…';
    pairError.textContent = '';
    serverError.textContent = '';

    // Temporarily set server URL so apiFetch can use it
    setServerUrl(serverUrl);

    try {
      const data = await apiFetch('/pair/validate', {
        method: 'POST',
        body: JSON.stringify({ code }),
      });

      if (data.token) {
        localStorage.setItem('kj_token', data.token);
        onPaired();
      } else {
        throw new Error('no token');
      }
    } catch (err) {
      // Roll back server URL on failure
      if (err.message === 'unauthorized') {
        pairError.textContent = 'Invalid code — please try again';
      } else if (err.message === 'no_server') {
        serverError.textContent = 'No server selected';
      } else {
        pairError.textContent = 'Could not reach the server — check the address and your network';
      }
      btn.disabled = false;
      btn.textContent = 'Connect';
      inputs.forEach((el) => { el.value = ''; });
      inputs[0].focus();
    }
  });

  // Pre-focus first code char if server is already selected
  if (getSelectedServer()) inputs[0].focus();
}

function addServerStyles() {
  if (document.getElementById('server-styles')) return;
  const style = document.createElement('style');
  style.id = 'server-styles';
  style.textContent = `
    .server-options { display: flex; flex-direction: column; gap: .45rem; }
    .server-opt {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: var(--text-dim);
      padding: .65rem .9rem;
      font-size: .875rem;
      text-align: left;
      cursor: pointer;
      transition: border-color .15s, color .15s;
    }
    .server-opt.active { border-color: var(--accent); color: var(--accent); }
    .server-custom {
      margin-top: .5rem;
      width: 100%;
      background: var(--bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: var(--text);
      padding: .65rem .9rem;
      font-size: .9rem;
      outline: none;
    }
    .server-custom:focus { border-color: var(--accent); }
  `;
  document.head.appendChild(style);
}
