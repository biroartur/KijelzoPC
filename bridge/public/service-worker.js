const CACHE = 'kijelzo-v6';
const PRECACHE = [
  '/',
  '/css/app.css',
  '/js/app.js',
  '/js/api.js',
  '/js/screens/main.js',
  '/js/screens/advanced.js',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE).then((c) => c.addAll(PRECACHE))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  // Never intercept API calls — let them go straight to the server
  const url = new URL(e.request.url);
  const isApi = ['/display', '/books', '/health', '/admin'].some(p => url.pathname.startsWith(p));
  if (isApi) return;

  // Cache-first for static assets, network fallback
  e.respondWith(
    caches.match(e.request).then((cached) => {
      if (cached) return cached;
      return fetch(e.request).then((res) => {
        if (res.ok && e.request.method === 'GET') {
          const clone = res.clone();
          caches.open(CACHE).then((c) => c.put(e.request, clone));
        }
        return res;
      });
    })
  );
});
