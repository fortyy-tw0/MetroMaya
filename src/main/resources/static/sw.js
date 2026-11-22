// MetroMaya service worker: lets the site be installed on a phone and keeps the pages (and bookings saved on
// this device, which live in localStorage) opening when the signal at the office is weak or gone.
// Pages and files: network first, so a new deploy shows at once; if the network is slow (over 4 s) or down,
// the last copy is used. The API is never cached here: bookings and fees always come live from the server.
const CACHE = 'metromaya-v2';
const SHELL = [
  '/', '/book.html', '/manage.html', '/privacy.html',
  '/css/style.css', '/js/common.js', '/js/landing.js', '/js/book.js', '/js/manage.js', '/js/ncr-places.js',
  '/img/maya-hello.png', '/img/maya-hey-you.png', '/img/app-icon-192.png', '/img/favicon-32.png', '/favicon.ico', '/manifest.webmanifest',
];
const CDN = [
  'https://cdnjs.cloudflare.com/ajax/libs/qrcode-generator/1.4.4/qrcode.min.js',
];
// other sites whose files may be kept (fonts, the QR and map libraries); map tiles and routes are not
const KEEP_HOSTS = ['cdnjs.cloudflare.com', 'fonts.googleapis.com', 'fonts.gstatic.com'];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await Promise.all([
      ...SHELL.map((url) => cache.add(new Request(url, { cache: 'reload' })).catch(() => {})),
      ...CDN.map((url) => cache.add(new Request(url, { mode: 'cors' })).catch(() => {})),
    ]);
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const names = await caches.keys();
    await Promise.all(names.filter((n) => n.startsWith('metromaya-') && n !== CACHE).map((n) => caches.delete(n)));
    await self.clients.claim();
  })());
});

function keep(request, response) {
  if (!response || (!response.ok && response.type !== 'opaque')) return;
  const copy = response.clone();
  caches.open(CACHE).then((cache) => cache.put(request, copy)).catch(() => {});
}

// the network, but give up after `ms` so a weak signal falls back to the saved copy quickly
function networkFirst(request, cacheKey, ms) {
  return new Promise((resolve) => {
    let done = false;
    const fallback = async () => {
      if (done) return;
      const hit = await caches.match(cacheKey, { ignoreSearch: true });
      if (hit) { done = true; resolve(hit); }
    };
    const timer = setTimeout(fallback, ms);
    fetch(request).then((response) => {
      clearTimeout(timer);
      if (response.ok) keep(cacheKey, response);
      if (!done) { done = true; resolve(response); }
    }).catch(async () => {
      clearTimeout(timer);
      await fallback();
      if (!done) {
        done = true;
        resolve(request.mode === 'navigate'
          ? new Response('<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            + '<title>Offline</title><body style="font-family:system-ui,sans-serif;background:#FBF7F0;color:#3E2416;padding:2rem;max-width:32rem;margin:auto">'
            + '<h1>You’re offline</h1><p>This page hasn’t been saved on this device yet. Connect to the internet and try again.</p>'
            + '<p><a href="/manage.html" style="color:#2C3A72">Open Track a booking</a> to see bookings saved on this device.</p></body>',
          { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } })
          : Response.error());
      }
    });
  });
}

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);

  if (url.origin === self.location.origin) {
    if (url.pathname.startsWith('/api/')) return;   // always live
    // one saved copy per page, whatever its ?query (e.g. /book.html?service=renewal, /manage.html?ref=…)
    const key = request.mode === 'navigate' ? url.origin + (url.pathname === '/index.html' ? '/' : url.pathname) : request;
    event.respondWith(networkFirst(request, key, request.mode === 'navigate' ? 4000 : 5000));
    return;
  }

  if (KEEP_HOSTS.includes(url.hostname)) {
    // these files never change at a given address: the saved copy first, the network otherwise
    event.respondWith(caches.match(request).then((hit) => hit || fetch(request).then((response) => {
      keep(request, response);
      return response;
    })));
  }
});
