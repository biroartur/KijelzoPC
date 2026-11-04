import { applyTheme } from './theme.js';
applyTheme();

// Haptic feedback on every button/interactive tap (Android Chrome only; no-op elsewhere)
if (navigator.vibrate) {
  document.addEventListener('touchstart', (e) => {
    const el = e.target.closest('button, [role="button"], a, input[type="submit"], input[type="button"], select');
    if (el) navigator.vibrate(30);
  }, { passive: true });
}

import { renderMain } from './screens/main.js';

const app = document.getElementById('app');
renderMain(app);

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js');
  });
}
