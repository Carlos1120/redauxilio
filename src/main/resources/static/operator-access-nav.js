"use strict";
/** Muestra Auditorías solo cuando la sesión y el permiso persistente siguen vigentes. */
(() => {
  const trigger = document.getElementById("identity-open");
  const desktop = document.getElementById("operator-nav-link");
  const mobile = document.getElementById("operator-mobile-link");
  const bottom = document.querySelector(".laguna-bottom-nav");
  if (!trigger || !desktop || !mobile || !bottom) return;

  let revision = 0;
  function show(allowed) {
    desktop.hidden = !allowed;
    mobile.hidden = !allowed;
    bottom.classList.toggle("has-operator", allowed);
  }
  async function refresh() {
    const current = ++revision;
    show(false);
    if (trigger.dataset.authenticated !== "true" || !navigator.onLine) return;
    try {
      const response = await fetch("/api/my/operator-permission", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (current !== revision || !response.ok || response.redirected) return;
      const permission = await response.json();
      if (current === revision && trigger.dataset.authenticated === "true") {
        show(permission.canModerate === true);
      }
    } catch {
      // Si la red falla, el acceso privado queda oculto hasta una nueva comprobación.
    }
  }
  window.addEventListener("redauxilio:identity-change", () => void refresh());
  window.addEventListener("offline", () => {
    revision++;
    show(false);
  });
  window.addEventListener("online", () => void refresh());
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) void refresh();
  });
  void refresh();
})();
