/**
 * Service worker for the Mohammadpur Stock Point web app.
 *
 * Caches the app shell so the POS opens with no signal; never touches API
 * responses (different origin) so sync always talks to the real server.
 */

const VERSION = 'dxn-stockpoint-2.1.0';
const SHELL = [
  './',
  './index.html',
  './manifest.webmanifest',
  './privacy.html',
  './css/app.css',
  './js/app.js',
  './js/api.js',
  './js/store.js',
  './js/ui.js',
  './js/util.js',
  './js/views/catalog.js',
  './js/views/cart.js',
  './js/views/orders.js',
  './js/views/admin.js',
  './js/views/settings.js',
  './js/views/product-form.js',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './icons/logo-mark-96.png',
  './icons/logo-mark-192.png',
  './icons/logo-full.png',
  './icons/favicon-32.png',
  './icons/apple-touch-icon.png',
  './images/products/lingzhi-coffee-3in1.jpg',
  './images/products/lingzhi-coffee-2in1.jpg',
  './images/products/zhi-mocha-premium.jpg',
  './images/products/lingzhi-coffee-lite.jpg',
  './images/products/rggl-capsules-90.jpg',
  './images/products/morinzhi-pure-noni.jpg',
  './images/products/spirulina-tablets-120.jpg',
  './images/products/andro-g-capsules-90.jpg',
  './images/products/lingzhi-black-tea.jpg',
  './images/products/ganozhi-toothpaste.jpg',
  './images/products/ganozhi-beauty-soap.jpg',
  './images/products/ganozhi-shampoo.jpg',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSION).then((cache) =>
      // One missing file must not abort the whole install.
      Promise.all(SHELL.map((url) => cache.add(url).catch(() => null))),
    ).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== VERSION).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('message', (event) => {
  if (event.data === 'skip-waiting') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return; // API calls and fonts go straight to the network

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(VERSION).then((cache) => cache.put('./index.html', copy)).catch(() => {});
          return response;
        })
        .catch(() => caches.match('./index.html').then((cached) => cached || caches.match('./'))),
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      const network = fetch(request)
        .then((response) => {
          if (response && response.status === 200 && response.type === 'basic') {
            const copy = response.clone();
            caches.open(VERSION).then((cache) => cache.put(request, copy)).catch(() => {});
          }
          return response;
        })
        .catch(() => cached);
      return cached || network;
    }),
  );
});
