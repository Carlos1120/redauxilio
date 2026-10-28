"use strict";
/**
 * Confirmación independiente: solo POST con CSRF fresco y versión consultada.
 * No guarda confirmaciones ni datos de cuenta en caché/IndexedDB y nunca reintenta escrituras.
 */
(() => {
  let selected = null;
  let host = null;
  let generation = 0;
  let busy = false;
  let notice = "";
  const authenticated = () =>
    document.getElementById("identity-open")?.dataset.authenticated === "true";

  function draw() {
    if (!host) return;
    host.replaceChildren();
    if (!selected || selected.id < 1000000) return;
    const count = document.createElement("p");
    count.textContent = `${selected.confirmationCount || 0} confirmaciones independientes vigentes. No es verificación oficial.`;
    const message = document.createElement("p");
    message.setAttribute("role", "status");
    message.setAttribute("aria-live", "polite");
    message.textContent =
      notice ||
      (!navigator.onLine
        ? "Sin conexión: no se envían ni se guardan confirmaciones pendientes."
        : !authenticated()
          ? "Inicia sesión para confirmar información que hayas comprobado."
          : selected.closed
            ? "Un caso cerrado no admite confirmaciones."
            : "Confirma únicamente información que hayas comprobado. El autor no puede confirmar su reporte.");
    const button = document.createElement("button");
    button.type = "button";
    button.className = "secondary-button";
    button.textContent = busy ? "Confirmando…" : "Confirmar información";
    button.disabled = busy || !navigator.onLine || !authenticated() || selected.closed;
    button.addEventListener("click", () => void confirm());
    host.append(count, message, button);
  }

  async function confirm() {
    if (busy || !selected || selected.closed || !navigator.onLine || !authenticated()) return;
    const item = selected,
      request = generation;
    const current = () => generation === request && navigator.onLine && authenticated();
    busy = true;
    notice = "Esperando confirmación del servidor…";
    draw();
    try {
      const tokenResponse = await fetch("/csrf", {
        cache: "no-store",
        credentials: "same-origin",
        headers: { Accept: "application/json" },
      });
      if (!tokenResponse.ok)
        throw new Error("No se pudo renovar el formulario. Inicia sesión de nuevo.");
      const token = await tokenResponse.json();
      if (!current()) return;
      if (!token?.token) throw new Error("No se pudo renovar el formulario.");
      const response = await fetch(`/api/publications/${item.id}/confirmations`, {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": token.token,
        },
        body: JSON.stringify({ version: item.confirmationVersion }),
      });
      const result = await response.json().catch(() => null);
      if (!current()) return;
      if (!response.ok) {
        if (response.status === 401) window.RedAuxilioIdentityStatus?.setAuthenticated(false);
        throw new Error(
          result?.message ||
            "No se pudo confirmar. Actualiza la consulta e intenta de nuevo con sesión y conexión.",
        );
      }
      selected = result;
      notice = "Confirmación aceptada por el servidor.";
      // Refresca mapa/lista; el éxito de escritura no depende de la siguiente consulta pública.
      try {
        await window.RedAuxilioPublicationUI?.loadPublications();
      } catch {
        /* La próxima consulta recuperará el conteo; no reenviar el POST. */
      }
    } catch (error) {
      if (generation === request)
        notice = error.message || "No se pudo confirmar. Intenta de nuevo con conexión.";
    } finally {
      if (generation === request) {
        busy = false;
        draw();
      }
    }
  }

  function invalidate() {
    generation++;
    busy = false;
    notice = "";
    draw();
  }
  window.addEventListener("redauxilio:identity-change", invalidate);
  window.addEventListener("offline", invalidate);
  window.addEventListener("online", invalidate);
  window.RedAuxilioConfirmation = {
    render(item, container) {
      const sameVersion =
        selected?.id === item.id && selected?.confirmationVersion === item.confirmationVersion;
      if (!sameVersion) {
        generation++;
        busy = false;
        notice = "";
      }
      selected = item;
      host = container;
      draw();
    },
  };
  window.RedAuxilioPublicationUI?.refreshConfirmation?.();
})();
