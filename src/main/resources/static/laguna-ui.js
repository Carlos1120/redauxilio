"use strict";

/**
 * Capa de navegación Laguna. app.js conserva filtros, mapa y publicaciones;
 * identity-status.js sigue siendo la única fuente del estado de la sesión.
 */
function lagunaSectionFromHash(hash) {
  if (["#search-section", "#publications", "#map-title"].includes(hash)) return "explorar";
  if (hash === "#draft-section") return "publicar";
  if (hash === "#own-section") return "cuenta";
  return "inicio";
}

/** navigator.onLine indica conectividad del dispositivo, no éxito de la API. */
function lagunaConnectionCopy(online) {
  return online ? "● En línea" : "● Sin conexión";
}

if (typeof module !== "undefined") {
  module.exports = { lagunaSectionFromHash, lagunaConnectionCopy };
}

if (typeof document !== "undefined") {
  function initializeLaguna() {
    const sourceSearch = document.getElementById("search-query");
    const search = document.getElementById("laguna-search-query");
    const mobileAccount = document.getElementById("laguna-account-mobile");
    const desktopAccount = document.getElementById("identity-open");
    const badge = document.getElementById("laguna-connection-label");
    const links = [...document.querySelectorAll("[data-laguna-nav]")];

    // El control visible delega en renderSearchResults a través del evento existente.
    if (sourceSearch && search) {
      search.value = sourceSearch.value;
      search.addEventListener("input", () => {
        sourceSearch.value = search.value;
        sourceSearch.dispatchEvent(new Event("input", { bubbles: true }));
      });
    }

    // Los atajos de Inicio usan el filtro real. No se simulan resultados.
    document.querySelectorAll("[data-laguna-category]").forEach((shortcut) => {
      shortcut.addEventListener("click", () => {
        const category = shortcut.getAttribute("data-laguna-category");
        const filter = [...document.querySelectorAll(".quick-filters button")].find(
          (button) => button.dataset.category === category,
        );
        filter?.click();
      });
    });

    const updateConnection = () => {
      if (!badge) return;
      badge.textContent = lagunaConnectionCopy(navigator.onLine);
      badge.dataset.online = String(navigator.onLine);
    };

    const updateNavigation = () => {
      const selected = lagunaSectionFromHash(window.location.hash);
      links.forEach((link) => {
        if (link.dataset.lagunaNav === selected) {
          link.setAttribute("aria-current", "page");
        } else {
          link.removeAttribute("aria-current");
        }
      });
      mobileAccount?.classList.toggle("is-active", selected === "cuenta");
    };

    // Un solo acceso a Cuenta en móvil; se reaprovecha la lógica de sesión.
    mobileAccount?.addEventListener("click", () => {
      desktopAccount?.click();
      mobileAccount.classList.add("is-active");
    });
    document.getElementById("identity-dialog")?.addEventListener("close", updateNavigation);

    window.addEventListener("online", updateConnection);
    window.addEventListener("offline", updateConnection);
    window.addEventListener("hashchange", updateNavigation);
    updateConnection();
    updateNavigation();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeLaguna, { once: true });
  } else {
    initializeLaguna();
  }
}
