"use strict";

/**
 * Presenta una transición breve de marca mientras termina la carga inicial.
 * No retrasa la navegación interna y se retira incluso si el evento load no llega.
 */
(() => {
  const loader = document.getElementById("app-loader");
  if (!loader) return;

  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const minimumDuration = reducedMotion ? 120 : 720;
  const maximumDuration = reducedMotion ? 450 : 2500;
  const startedAt = performance.now();
  let closing = false;

  document.documentElement.classList.add("app-loading");

  const close = () => {
    if (closing) return;
    closing = true;
    const remaining = Math.max(0, minimumDuration - (performance.now() - startedAt));
    window.setTimeout(() => {
      loader.classList.add("is-leaving");
      loader.setAttribute("aria-hidden", "true");
      document.documentElement.classList.remove("app-loading");
      window.setTimeout(
        () => {
          loader.hidden = true;
        },
        reducedMotion ? 0 : 360,
      );
    }, remaining);
  };

  if (document.readyState === "complete") close();
  else window.addEventListener("load", close, { once: true });
  window.setTimeout(close, maximumDuration);
})();
