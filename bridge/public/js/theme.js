// App look: "system" (default, follows the phone), "light" or "dark" — same choice as the Android app
const KEY = 'kj_theme';

export function themeMode() {
  const m = localStorage.getItem(KEY);
  return m === 'light' || m === 'dark' ? m : 'system';
}

export function applyTheme() {
  const mode = themeMode();
  const light = mode === 'light' ||
    (mode === 'system' && window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches);
  document.documentElement.classList.toggle('light', light);
}

export function setThemeMode(mode) {
  if (mode === 'system') localStorage.removeItem(KEY); else localStorage.setItem(KEY, mode);
  applyTheme();
}

// Follow the phone switching between light and dark while the page is open
if (window.matchMedia) {
  const mq = window.matchMedia('(prefers-color-scheme: light)');
  (mq.addEventListener ? mq.addEventListener.bind(mq, 'change') : mq.addListener.bind(mq))(() => {
    if (themeMode() === 'system') applyTheme();
  });
}
