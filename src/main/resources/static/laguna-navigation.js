"use strict";
/**
 * Laguna — navegación pública sin duplicar permisos ni lógica de publicación.
 *
 * La barra móvil reutiliza la cuenta ya conectada a app.js y sus validaciones.
 * Mantener todos los ID y eventos de la aplicación existente.
 */
(() => {
  const tabs = [...document.querySelectorAll(".laguna-bottom-nav [data-laguna-tab]")];
  const desktopTabs = [...document.querySelectorAll(".site-header .header-content > nav a")];
  const account = document.getElementById("laguna-mobile-account");
  const existingAccountTrigger = document.getElementById("identity-open");
  const dialog = document.getElementById("identity-dialog");
  if (tabs.length !== 4 || !account || !existingAccountTrigger || !dialog) return;

  // Los accesos del Inicio reutilizan los filtros públicos existentes.
  for (const link of document.querySelectorAll("[data-home-category]")) {
    link.addEventListener("click", () => {
      document
        .querySelector(`.quick-filters [data-category="${link.dataset.homeCategory}"]`)
        ?.click();
    });
  }

  function selectedByHash(hash) {
    if (hash === "#draft-section") return "publish";
    if (hash === "#own-section") return "account";
    if (["#search-section", "#map-title", "#report-map", "#publications"].includes(hash))
      return "explore";
    return "home";
  }

  function setActive(key) {
    for (const item of tabs) {
      if (item.dataset.lagunaTab === key) item.setAttribute("aria-current", "page");
      else item.removeAttribute("aria-current");
    }
    // La misma navegación activa se refleja en la cabecera de escritorio.
    const desktopHref = {
      home: "#main-content",
      explore: "#search-section",
      publish: "#draft-section",
      account: window.location.hash === "#own-section" ? "#own-section" : null,
    }[key];
    for (const link of desktopTabs) {
      if (desktopHref && link.getAttribute("href") === desktopHref)
        link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    }
  }

  for (const tab of tabs) {
    if (tab === account) continue;
    tab.addEventListener("click", () => setActive(tab.dataset.lagunaTab));
  }

  account.addEventListener("click", () => {
    // Un único origen de sesión: dispara el manejador ya registrado por app.js.
    existingAccountTrigger.click();
    if (dialog.open) setActive("account");
  });
  dialog.addEventListener("close", () => setActive(selectedByHash(window.location.hash)));
  window.addEventListener("hashchange", () => {
    if (!dialog.open) setActive(selectedByHash(window.location.hash));
  });
  setActive(selectedByHash(window.location.hash));
})();
