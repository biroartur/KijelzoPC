import { apiFetch } from '../api.js';
import { themeMode, setThemeMode } from '../theme.js';

// Same choices as the Android app's Settings page; every change is saved at once (no Save button).
// Cards for display settings appear only when the display PC supports them.
const COLORS = [
  ['FFC800', 'Orange (default)'], ['FFFFFF', 'White'], ['FFFF00', 'Yellow'], ['00E000', 'Green'],
  ['FF3030', 'Red'], ['3399FF', 'Blue'], ['66CCFF', 'Light blue'], ['A0A0A0', 'Gray'],
  ['FF66CC', 'Pink'], ['B580FF', 'Purple'],
];
const HIDE = [5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 0];

export function renderSettings(container, showToast) {
  container.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:1rem">
      <div class="card settings-card">
        <div class="settings-title">Appearance</div>
        <div class="settings-hint">Light or dark look of this page</div>
        <select id="setTheme">
          <option value="system">System (default)</option>
          <option value="light">Light</option>
          <option value="dark">Dark</option>
        </select>
      </div>

      <div class="card settings-card" id="cardClock" style="display:none">
        <label class="settings-row">
          <span>
            <span class="settings-title">Show Clock</span>
            <span class="settings-hint">Shows the time on the display while no text is shown</span>
          </span>
          <input type="checkbox" id="setClock">
        </label>
      </div>

      <div class="card settings-card" id="cardColors" style="display:none">
        <div class="settings-title">Display colours</div>
        <div class="settings-hint">Text colour of the two lines on the display</div>
        <label>Line 1 (book)</label>
        <select id="setColor1">${colorOptions()}</select>
        <label>Line 2 (number)</label>
        <select id="setColor2">${colorOptions()}</select>
        <div class="color-preview">
          <div id="prev1">Hymnal</div>
          <div id="prev2">123</div>
        </div>
      </div>

      <div class="card settings-card" id="cardHide" style="display:none">
        <div class="settings-title">Text on the display</div>
        <div class="settings-hint">The text disappears this long after it was last changed</div>
        <label>Hide the text after</label>
        <select id="setHide">${HIDE.map((m) => `<option value="${m}">${
          m === 0 ? 'Never (only with Clear Display)' : `${m} minutes${m === 30 ? ' (default)' : ''}`}</option>`).join('')}</select>
      </div>

      <div class="settings-hint" id="setStatus" style="text-align:center">Loading…</div>
    </div>
  `;

  const theme = container.querySelector('#setTheme');
  theme.value = themeMode();
  theme.addEventListener('change', () => setThemeMode(theme.value));

  load(container, showToast);
}

function colorOptions() {
  return COLORS.map(([v, n]) => `<option value="${v}">${n}</option>`).join('');
}

async function load(container, showToast) {
  const status = container.querySelector('#setStatus');
  let data;
  try {
    data = await apiFetch('/display/settings');
  } catch { data = { error: 'x' }; }
  if (!data || data.error) {
    status.textContent = 'Display settings need the display PC to be on.';
    return;
  }
  status.textContent = '';

  if (data.clock !== null && data.clock !== undefined) {
    const cb = container.querySelector('#setClock');
    cb.checked = data.clock;
    container.querySelector('#cardClock').style.display = '';
    cb.addEventListener('change', () =>
      save('/display/clock', { value: cb.checked ? '1' : '0' }, showToast));
  }

  if (data.colors) {
    const s1 = container.querySelector('#setColor1');
    const s2 = container.querySelector('#setColor2');
    [[s1, data.colors[0]], [s2, data.colors[1]]].forEach(([sel, val]) => {
      if (![...sel.options].some((o) => o.value === val)) sel.add(new Option(`Custom (#${val})`, val));
      sel.value = val;
    });
    const preview = () => {
      container.querySelector('#prev1').style.color = '#' + s1.value;
      container.querySelector('#prev2').style.color = '#' + s2.value;
    };
    preview();
    const onChange = () => { preview(); save('/display/colors', { color1: s1.value, color2: s2.value }, showToast); };
    s1.addEventListener('change', onChange);
    s2.addEventListener('change', onChange);
    container.querySelector('#cardColors').style.display = '';
  }

  if (data.hideMinutes !== null && data.hideMinutes !== undefined) {
    const sel = container.querySelector('#setHide');
    if (![...sel.options].some((o) => +o.value === data.hideMinutes)) {
      sel.add(new Option(`${data.hideMinutes} minutes`, String(data.hideMinutes)));
    }
    sel.value = String(data.hideMinutes);
    sel.addEventListener('change', () => save('/display/hide', { minutes: parseInt(sel.value, 10) }, showToast));
    container.querySelector('#cardHide').style.display = '';
  }
}

async function save(path, body, showToast) {
  try {
    const data = await apiFetch(path, { method: 'POST', body: JSON.stringify(body) });
    if (data && data.error) throw new Error(data.error);
    showToast('Saved');
  } catch {
    showToast('Not saved — is the display PC on?');
  }
}
