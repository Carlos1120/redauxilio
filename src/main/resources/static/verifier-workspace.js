"use strict";
/**
 * Presenta el trabajo de verificación en una sección propia. Lee solo publicaciones públicas;
 * el permiso vigente y la escritura siguen validados por las rutas protegidas del servidor.
 */
(() => {
  const workspace = document.getElementById("verifier-workspace");
  const link = document.getElementById("verifier-workspace-link");
  const message = document.getElementById("verifier-message");
  const search = document.getElementById("verifier-search");
  const list = document.getElementById("verifier-list");
  const title = document.getElementById("verifier-detail-title");
  const help = document.getElementById("verifier-detail-help");
  const verification = document.getElementById("verifier-official-verification");
  if (!workspace || !link || !message || !search || !list || !title || !help || !verification)
    return;

  let allowed = false;
  let publications = [];
  let revision = 0;

  function clearPrivateView() {
    publications = [];
    list.replaceChildren();
    verification.replaceChildren();
    title.textContent = "Selecciona una publicación";
    help.textContent = "Elige un reporte para consultar su estado y registrar una verificación.";
  }

  function renderList() {
    list.replaceChildren();
    const query = search.value.trim().toLocaleLowerCase("es-CO");
    const matches = publications.filter((item) =>
      `${item.title} ${item.location}`.toLocaleLowerCase("es-CO").includes(query),
    );
    if (!matches.length) {
      const empty = document.createElement("p");
      empty.textContent = "No hay publicaciones que coincidan con la búsqueda.";
      list.append(empty);
      return;
    }
    for (const item of matches) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "verifier-report";
      const name = document.createElement("strong");
      name.textContent = item.title;
      const location = document.createElement("span");
      location.textContent = item.location;
      button.append(name, location);
      button.addEventListener("click", () => {
        if (!allowed) return;
        for (const other of list.querySelectorAll("button")) other.removeAttribute("aria-current");
        button.setAttribute("aria-current", "true");
        title.textContent = item.title;
        help.textContent = `${item.location} · ${item.confidenceLevel === "VERIFICADA" ? "Verificada oficialmente" : "Sin verificación oficial vigente"}`;
        window.RedAuxilioOfficialVerification?.render(item, verification);
      });
      list.append(button);
    }
  }

  /** Revalida el permiso antes de mostrar el espacio y tras cambiar la sesión o la conexión. */
  async function refresh() {
    const current = ++revision;
    allowed = false;
    link.hidden = true;
    clearPrivateView();
    if (!navigator.onLine) {
      message.textContent = "Conéctate para consultar y verificar publicaciones.";
      return;
    }
    message.textContent = "Comprobando permiso de verificación…";
    try {
      const permission = await fetch("/api/my/verification-permission", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (current !== revision) return;
      allowed =
        permission.ok && !permission.redirected && (await permission.json()).canVerify === true;
      if (!allowed) {
        message.textContent = "Esta cuenta no tiene permiso vigente para verificar publicaciones.";
        return;
      }
      link.hidden = false;
      message.textContent = "Permiso vigente. Selecciona una publicación para revisarla.";
      const response = await fetch("/api/publications", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (!response.ok) throw new Error("No se pudieron consultar las publicaciones.");
      const result = await response.json();
      if (current !== revision) return;
      publications = result;
      renderList();
    } catch {
      if (current === revision)
        message.textContent =
          "No se pudo consultar el espacio de verificación. Intenta de nuevo con conexión.";
    }
  }

  search.addEventListener("input", renderList);
  link.addEventListener("click", () => document.getElementById("identity-dialog")?.close());
  window.addEventListener("redauxilio:identity-change", () => void refresh());
  window.addEventListener("online", () => void refresh());
  window.addEventListener("offline", () => {
    revision++;
    allowed = false;
    link.hidden = true;
    clearPrivateView();
    message.textContent = "Conéctate para consultar y verificar publicaciones.";
  });
  void refresh();
})();
