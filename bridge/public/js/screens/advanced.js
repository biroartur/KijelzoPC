import { apiFetch } from '../api.js';
import { displayChanged } from './main.js';

const STORAGE_KEY = 'kj_advanced_entries';

function loadEntries() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; }
  catch { return []; }
}
function saveEntries(entries) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
}

export function renderAdvanced(container, showToast) {
  let entries = loadEntries();

  function render() {
    container.innerHTML = `
      <div style="display:flex;flex-direction:column;gap:1rem">
        <div class="card" style="display:flex;flex-direction:column;gap:.9rem">
          <button class="btn btn-danger-solid" id="advClear">Clear Display</button>
          <div>
            <label>Line 1 (max 26 chars)</label>
            <input type="text" id="adv1" maxlength="26" placeholder="First line…">
          </div>
          <div>
            <label>Line 2 (max 26 chars)</label>
            <input type="text" id="adv2" maxlength="26" placeholder="Second line…">
          </div>
          <div class="action-row">
            <button class="btn btn-primary" id="advSend">Send</button>
            <button class="btn btn-secondary" id="advSave">Save</button>
          </div>
        </div>

        ${entries.length > 0 ? `
        <div>
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:.5rem">
            <label style="margin:0">Saved entries</label>
            <button class="btn btn-danger" id="clearAll" style="width:auto;padding:.3rem .9rem;font-size:.8rem">Clear all</button>
          </div>
          <div class="entry-list" id="entryList">
            ${entries.map((e, i) => `
              <div class="entry-item ${e.sent ? 'sent' : ''}" data-idx="${i}">
                <div class="entry-text">
                  <div class="entry-line1">${escHtml(e.line1)}</div>
                  <div class="entry-line2">${escHtml(e.line2)}</div>
                </div>
                <button class="entry-del" data-del="${i}" title="Delete">✕</button>
              </div>
            `).join('')}
          </div>
        </div>` : ''}
      </div>
    `;

    // Send directly
    document.getElementById('advSend').addEventListener('click', async () => {
      const l1 = document.getElementById('adv1').value;
      const l2 = document.getElementById('adv2').value;
      if (!l1 && !l2) { showToast('Enter at least one line'); return; }
      await sendStrings(l1, l2, showToast);
    });

    // Keyboard: Enter on line 1 moves to line 2, Enter on line 2 sends
    document.getElementById('adv1').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); document.getElementById('adv2').focus(); }
    });
    document.getElementById('adv2').addEventListener('keydown', (e) => {
      if (e.key === 'Enter') { e.preventDefault(); document.getElementById('advSend').click(); }
    });

    // Save entry
    document.getElementById('advSave').addEventListener('click', () => {
      const l1 = document.getElementById('adv1').value.trim();
      const l2 = document.getElementById('adv2').value.trim();
      if (!l1 && !l2) { showToast('Enter at least one line'); return; }
      entries.push({ line1: l1, line2: l2, sent: false });
      saveEntries(entries);
      document.getElementById('adv1').value = '';
      document.getElementById('adv2').value = '';
      render();
    });

    // Clear display
    document.getElementById('advClear').addEventListener('click', async () => {
      try {
        const data = await apiFetch('/display/blank', { method: 'POST' });
        if (data && data.error) throw new Error(data.error);
        showToast('Display cleared');
      } catch (err) {
        if (err.message !== 'unauthorized') showToast('Failed to clear display — is the display PC on?');
      }
      displayChanged();
    });

    // Clear all saved entries
    const clearAllBtn = document.getElementById('clearAll');
    if (clearAllBtn) {
      clearAllBtn.addEventListener('click', () => {
        if (!confirm('Delete all saved entries?')) return;
        entries = [];
        saveEntries(entries);
        render();
      });
    }

    // Entry list: send on tap, delete on ✕
    const list = document.getElementById('entryList');
    if (list) {
      list.addEventListener('click', async (e) => {
        const delBtn = e.target.closest('[data-del]');
        const item = e.target.closest('[data-idx]');

        if (delBtn) {
          const idx = parseInt(delBtn.dataset.del, 10);
          entries.splice(idx, 1);
          saveEntries(entries);
          render();
          return;
        }

        if (item && !e.target.closest('[data-del]')) {
          const idx = parseInt(item.dataset.idx, 10);
          const entry = entries[idx];
          // Both lines in full, each on its own row
          const preview = [entry.line1, entry.line2].filter(Boolean).join('\n');
          if (!confirm(`Send to display?\n\n${preview}`)) return;
          // Marked as "on the display" only when the send really worked
          if (!(await sendStrings(entry.line1, entry.line2, showToast))) return;
          entries = entries.map((en, i) => ({ ...en, sent: i === idx }));
          saveEntries(entries);
          render();
        }
      });
    }
  }

  render();
}

// Returns true when the display PC received the text
async function sendStrings(line1, line2, showToast) {
  let ok = false;
  try {
    const data = await apiFetch('/display/strings', {
      method: 'POST',
      body: JSON.stringify({ line1, line2 }),
    });
    if (data && data.error) throw new Error(data.error);
    showToast('Sent!');
    ok = true;
  } catch (err) {
    if (err.message !== 'unauthorized') showToast('Failed to send — is the display PC on?');
  }
  displayChanged();
  return ok;
}

function escHtml(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}
