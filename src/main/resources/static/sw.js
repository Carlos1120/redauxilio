"use strict";
/**
 * Service worker: intermediario de red para recursos propios y consultas públicas GET.
 * Intenta primero la red; solo ante un fallo de red utiliza una copia previamente guardada.
 * No almacena borradores, operaciones de escritura, bibliotecas CDN ni cartografía externa.
 */
const CACHE = "redauxilio-demo-v6";
const SHELL = [
  "/",
  "/app.css",
  "/app.js",
  "/map.js",
  "/manifest.webmanifest",
  "/icon.svg",
  "/vendor/leaflet/leaflet.css",
  "/vendor/leaflet/leaflet.js",
  "/vendor/leaflet/images/marker-icon.png",
  "/vendor/leaflet/images/marker-icon-2x.png",
  "/vendor/leaflet/images/marker-shadow.png",
  "/vendor/leaflet/images/layers.png",
  "/vendor/leaflet/images/layers-2x.png",
  "/vendor/onest/onest-latin.woff2",
];
self.addEventListener("install", (event) => {
  // waitUntil mantiene la instalación abierta hasta guardar los recursos iniciales de la pantalla.
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
});
self.addEventListener("activate", (event) => {
  // Elimina únicamente versiones anteriores de nuestra caché; no otras cachés del mismo origen.
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
  // La lista permitida evita capturar futuras rutas privadas o enviar mapas externos a esta caché.
  if (event.request.method !== "GET" || url.origin !== self.location.origin) return;
  const publicQuery = url.pathname === "/api/publications";
  if (!publicQuery && !SHELL.includes(url.pathname)) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      try {
        const response = await fetch(event.request);
        if (response.ok) {
          // Una respuesta tiene un cuerpo consumible una vez: clona para guardar y devolver por separado.
          let stored = response.clone();
          if (publicQuery) {
            const headers = new Headers(response.headers);
            // Marca la copia, no la respuesta de red, para que app.js pueda mostrar su fecha al recuperarla.
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
        // La clave conserva filtros y área de la URL: otra consulta podría no tener copia guardada.
        // HTTP 400/500 no entra aquí: fetch devuelve esos errores al navegador sin ocultarlos con caché.
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
