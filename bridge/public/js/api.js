// All fetch calls use relative URLs.
// This works whether the PWA is served by:
//   - nginx at https://kijelzo.szeretet.ro  (nginx proxies /display, /books etc. to bridge)
//   - the bridge directly at http://192.168.1.12:3000  (bridge handles everything)
export async function apiFetch(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: {
      'Content-Type': 'application/json',
      ...(opts.headers || {}),
    },
  });
  if (!res.ok && res.status !== 503) throw new Error(`http_${res.status}`);
  return res.json();
}
