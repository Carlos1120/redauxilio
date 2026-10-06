"use strict";
const CACHE = "redauxilio-demo-v1";
const SHELL = ["/", "/app.css", "/app.js", "/manifest.webmanifest", "/icon.svg"];
self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
});
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("redauxilio-demo-") && key !== CACHE)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== self.location.origin) return;
  const publicQuery = url.pathname === "/api/publications";
  if (!publicQuery && !SHELL.includes(url.pathname)) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      try {
        const response = await fetch(event.request);
        if (response.ok) {
          let stored = response.clone();
          if (publicQuery) {
            const headers = new Headers(response.headers);
            headers.set("X-RedAuxilio-Saved-At", new Date().toISOString());
            stored = new Response(await response.clone().blob(), {
              status: response.status,
              headers,
            });
          }
          await cache.put(event.request, stored);
        }
        return response;
      } catch {
        const cached = await cache.match(event.request);
        return (
          cached ||
          new Response("Consulta no disponible sin conexión", {
            status: 503,
            headers: { "Content-Type": "text/plain; charset=utf-8" },
          })
        );
      }
    })(),
  );
});
