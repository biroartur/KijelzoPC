import { apiFetch } from '../api.js';

const PLACEHOLDER = '— Please choose a book —';
let books = [];
let selectedBook = PLACEHOLDER;

export function renderMain(app) {
  app.innerHTML = `
    <div class="screen">
      <div class="header">
        <h1>Kijelző</h1>
        <div style="display:flex;align-items:center;gap:.75rem">
          <button class="theme-toggle" id="themeToggle" title="Toggle theme"></button>
          <div class="status">
            <div class="status-dot" id="statusDot"></div>
            <span id="statusText">Checking…</span>
          </div>
        </div>
      </div>

      <div class="content" id="mainContent">
        <!-- What the display shows now (server v1.5+; hidden on older servers) -->
        <div class="card now-card" id="nowCard" style="display:none">
          <div class="now-label">On the display:</div>
          <div class="now-text" id="nowText"></div>
        </div>

        <!-- Tabs -->
        <div style="display:flex;gap:.5rem;margin-bottom:.25rem">
          <button class="btn btn-primary" id="tabPresenter" style="flex:1;padding:.55rem">Presenter</button>
          <button class="btn btn-secondary" id="tabAdvanced" style="flex:1;padding:.55rem">Advanced</button>
        </div>

        <!-- Presenter panel -->
        <div id="presenterPanel">
          <div class="card" style="display:flex;flex-direction:column;gap:.9rem">
            <button class="btn btn-danger-solid" id="clearBtn">Clear Display</button>
            <div>
              <label>Book / Hymnal</label>
              <button class="book-select-btn" id="bookBtn">
                <span id="bookLabel">Loading…</span>
                <span class="arrow">▼</span>
              </button>
            </div>
            <div>
              <label>Number (0 – 999)</label>
              <div class="number-input-wrap">
                <input class="number-input-big" type="text" id="numberInput"
                  maxlength="3" placeholder="" inputmode="numeric" pattern="[0-9]*">
              </div>
            </div>
            <div class="action-row">
              <button class="btn btn-primary" id="sendBtn">Send</button>
            </div>
          </div>
        </div>

        <!-- Advanced panel (hidden initially) -->
        <div id="advancedPanel" style="display:none"></div>
      </div>
    </div>

    <!-- Book selector modal -->
    <div class="modal-overlay" id="bookModal" style="display:none">
      <div class="modal-sheet">
        <div class="modal-title">Select Book</div>
        <div id="bookList"></div>
        <button class="modal-cancel" id="modalCancel">Cancel</button>
      </div>
    </div>

    <div class="toast" id="toast"></div>
  `;

  // Lazy-load advanced screen
  const advancedPanel = document.getElementById('advancedPanel');
  document.getElementById('tabPresenter').addEventListener('click', () => {
    document.getElementById('presenterPanel').style.display = '';
    advancedPanel.style.display = 'none';
    document.getElementById('tabPresenter').className = 'btn btn-primary';
    document.getElementById('tabAdvanced').className = 'btn btn-secondary';
  });
  document.getElementById('tabAdvanced').addEventListener('click', async () => {
    document.getElementById('presenterPanel').style.display = 'none';
    advancedPanel.style.display = '';
    document.getElementById('tabPresenter').className = 'btn btn-secondary';
    document.getElementById('tabAdvanced').className = 'btn btn-primary';
    if (!advancedPanel._loaded) {
      const { renderAdvanced } = await import('./advanced.js');
      renderAdvanced(advancedPanel, showToast);
      advancedPanel._loaded = true;
    }
  });

  // Book selector
  document.getElementById('bookBtn').addEventListener('click', () => {
    populateBookList();
    document.getElementById('bookModal').style.display = 'flex';
  });
  document.getElementById('modalCancel').addEventListener('click', () => {
    document.getElementById('bookModal').style.display = 'none';
  });

  // Number input — digits only
  document.getElementById('numberInput').addEventListener('input', (e) => {
    const clean = e.target.value.replace(/\D/g, '').slice(0, 3);
    if (e.target.value !== clean) e.target.value = clean;
  });

  // The keyboard's Enter / Go key sends too (Android, hardware keyboards)
  document.getElementById('numberInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); document.getElementById('sendBtn').click(); }
  });

  // Send (no lock: a new number can be sent right away)
  document.getElementById('sendBtn').addEventListener('click', async () => {
    const number = document.getElementById('numberInput').value.trim();
    if (!selectedBook || selectedBook === PLACEHOLDER) { showToast('Please select a book first'); return; }
    if (number === '') { showToast('Please enter a number'); return; }
    try {
      const data = await apiFetch('/display/hymn', {
        method: 'POST',
        body: JSON.stringify({ title: selectedBook, number: parseInt(number, 10) }),
      });
      if (data && data.error) throw new Error(data.error);
      // Cleared only after a successful send; after an error the number stays for another try
      document.getElementById('numberInput').value = '';
      showToast('Sent!');
    } catch (err) {
      if (err.message !== 'unauthorized') showToast('Failed to send — is the display PC on?');
    }
    displayChanged();
  });

  // Clear
  document.getElementById('clearBtn').addEventListener('click', async () => {
    try {
      const data = await apiFetch('/display/blank', { method: 'POST' });
      if (data && data.error) throw new Error(data.error);
      document.getElementById('numberInput').value = '';
      showToast('Display cleared');
    } catch (err) {
      if (err.message !== 'unauthorized') showToast('Failed to clear display — is the display PC on?');
    }
    displayChanged();
  });

  // Theme toggle
  const themeBtn = document.getElementById('themeToggle');
  function applyTheme(light) {
    document.documentElement.classList.toggle('light', light);
    themeBtn.textContent = light ? '🌙' : '☀️';
  }
  applyTheme(localStorage.getItem('kj_theme') === 'light');
  themeBtn.addEventListener('click', () => {
    const light = !document.documentElement.classList.contains('light');
    localStorage.setItem('kj_theme', light ? 'light' : 'dark');
    applyTheme(light);
  });

  // Load data
  loadBooks();
  checkConnection();
  startNowShowing();
}

// ── What the display shows now ───────────────────────────────────────────────
// Refreshed every 15 s while the page is visible and right after any send / clear (also from the
// Advanced tab), so changes from phones, other iPhones and the hide timer show up too.

const NOW_REFRESH_MS = 15000;
let nowTimer = null;

export function displayChanged() {
  document.dispatchEvent(new Event('kj:display-changed'));
}

function startNowShowing() {
  refreshNowShowing();
  clearInterval(nowTimer);
  nowTimer = setInterval(() => {
    if (document.visibilityState === 'visible') refreshNowShowing();
  }, NOW_REFRESH_MS);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') refreshNowShowing();
  });
  document.addEventListener('kj:display-changed', refreshNowShowing);
}

async function refreshNowShowing() {
  const card = document.getElementById('nowCard');
  const text = document.getElementById('nowText');
  if (!card || !text) return;
  try {
    const data = await apiFetch('/display/current');
    // The bridge answers even while the display PC is off: let the header show the PC's state
    if (!data || data.error) { card.style.display = 'none'; updateStatus(false); return; }
    updateStatus(true);
    if (!data.supported) { card.style.display = 'none'; return; }
    if (!data.shown) {
      text.textContent = 'The display is empty';
      text.classList.add('empty');
    } else {
      // Book + number on one row ("Nagykar 12"); two text lines each on their own row
      const l1 = data.line1 || '', l2 = data.line2 || '';
      text.textContent = !l2 ? l1 : /^\d{1,4}$/.test(l2) ? `${l1} ${l2}` : `${l1}\n${l2}`;
      text.classList.remove('empty');
    }
    card.style.display = '';
  } catch {
    card.style.display = 'none';
  }
}

async function loadBooks() {
  const bookBtn = document.getElementById('bookBtn');
  const bookLabel = document.getElementById('bookLabel');
  if (!bookBtn) return;
  try {
    const data = await apiFetch('/books');
    books = data.books || [];
    selectedBook = PLACEHOLDER;
    bookLabel.textContent = PLACEHOLDER;
    updateStatus(true);
  } catch (err) {
    if (err.message !== 'unauthorized') {
      bookLabel.textContent = 'Could not load books';
      updateStatus(false);
    }
  }
}

async function checkConnection() {
  try {
    await apiFetch('/health');
    updateStatus(true);
  } catch {
    updateStatus(false);
  }
}

function updateStatus(ok) {
  const dot = document.getElementById('statusDot');
  const text = document.getElementById('statusText');
  if (!dot || !text) return;
  dot.className = 'status-dot ' + (ok ? 'ok' : 'err');
  text.textContent = ok ? 'Connected' : 'Display PC offline';
}

function populateBookList() {
  const list = document.getElementById('bookList');
  if (!list) return;
  list.innerHTML = books.map((b) => `
    <div class="modal-option ${b === selectedBook ? 'selected' : ''}" data-book="${escAttr(b)}">
      ${b === selectedBook ? '✓ ' : ''}${escHtml(b)}
    </div>
  `).join('');

  list.querySelectorAll('.modal-option').forEach((el) => {
    el.addEventListener('click', () => {
      selectedBook = el.dataset.book;
      document.getElementById('bookLabel').textContent = selectedBook;
      document.getElementById('bookModal').style.display = 'none';
    });
  });
}



function showToast(msg) {
  const toast = document.getElementById('toast');
  if (!toast) return;
  toast.textContent = msg;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 2200);
}

function escHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
function escAttr(s) {
  return s.replace(/"/g,'&quot;');
}
