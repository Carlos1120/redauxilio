"use strict";
/**
 * Service worker: intermediario de red para recursos propios y consultas públicas GET.
 * Intenta primero la red; solo ante un fallo de red utiliza una copia previamente guardada.
 * No almacena borradores, operaciones de escritura, bibliotecas CDN ni cartografía externa.
 */
// La versión 11 actualiza la cuenta ciudadana y conserva la recuperación offline de recursos propios.
const CACHE = "redauxilio-demo-v11";
const SHELL = [
  "/",
  "/app.css",
  "/app.js",
  "/identity-forms.js",
  "/identity-status.js",
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
  event.waitUntil(
    caches.open(CACHE).then(async (cache) => {
      await cache.addAll(SHELL.filter((path) => path !== "/"));
      const response = await fetch(new URL("/", self.location.origin));
      if (!response.ok) throw new Error("No se pudo guardar la pantalla pública inicial.");
      await cache.put(
        new URL("/", self.location.origin).href,
        await removeCachedCsrfTokens(response),
      );
    }),
  );
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
  const publicShell = SHELL.includes(url.pathname) && !(url.pathname === "/" && url.search);
  if (!publicQuery && !publicShell) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      try {
        const response = await fetch(event.request);
        if (response.ok) {
          // Una respuesta tiene un cuerpo consumible una vez: clona para guardar y devolver por separado.
          let stored = response.clone();
          if (url.pathname === "/") {
            stored = await removeCachedCsrfTokens(response);
          } else if (publicQuery) {
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

/** Quita los tokens de sesión de la copia offline; identity-forms.js obtiene uno vigente antes del POST. */
async function removeCachedCsrfTokens(response) {
  const html = await response.clone().text();
  const sanitized = html
    .replace(/(<input\b(?=[^>]*\bname=["']_csrf["'])[^>]*)(>)/gi, (match, input, close) => {
      const withoutToken = input.replace(/\svalue=(["']).*?\1/i, ' value=""');
      return `${withoutToken}${close}`;
    })
    .replace(/\sdata-authenticated=["']true["']/gi, ' data-authenticated="false"');
  const headers = new Headers(response.headers);
  headers.delete("Content-Length");
  headers.delete("Content-Encoding");
  return new Response(sanitized, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
