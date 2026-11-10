/* FINNY_CACHE и FINNY_ASSETS добавляются при упаковке проверенного релиза. */
const allowed = new Set(FINNY_ASSETS.map(path => new URL(path, self.location.href).pathname));
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(FINNY_CACHE);
    try {
      for (let index = 0; index < FINNY_ASSETS.length; index += 12) {
        await Promise.all(FINNY_ASSETS.slice(index, index + 12).map(async path => {
          const response = await fetch(new Request(path, {cache:'reload', credentials:'omit'}));
          if (!response.ok || response.redirected) throw new Error('offline-asset-unavailable');
          await cache.put(path, response);
        }));
      }
    } catch (error) { await caches.delete(FINNY_CACHE); throw error; }
  })());
});
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key.startsWith('finny-static-') && key !== FINNY_CACHE).map(key => caches.delete(key)));
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  // Авторизация и прогресс никогда не попадают в кэш статики.
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  if (request.mode === 'navigate' && ['/', '/index.html'].includes(url.pathname)) {
    event.respondWith(caches.open(FINNY_CACHE).then(async cache => (await cache.match('index.html')) || fetch(request)));
  } else if (allowed.has(url.pathname)) {
    event.respondWith(caches.open(FINNY_CACHE).then(async cache => (await cache.match(url.pathname)) || fetch(request)));
  }
});
