"use strict";
/**
 * Denuncia privada RA-07: motivo solo en memoria del formulario, CSRF fresco y sin cola offline.
 * Descarta respuestas anteriores al cambiar de selección/sesión; solo anuncia éxito tras el POST.
 */
(() => {
  let selected = null;
  let host = null;
  let generation = 0;
  let busy = false;
  let reason = "";
  let notice = "";
  const authenticated = () =>
    document.getElementById("identity-open")?.dataset.authenticated === "true";
  const available = () => navigator.onLine && authenticated();

  function clearReason() {
    reason = "";
    const field = host?.querySelector("textarea");
    if (field) field.value = "";
  }
  function draw() {
    if (!host) return;
    host.replaceChildren();
    if (!selected || selected.id < 1000000) return;
    const form = document.createElement("form");
    form.className = "content-report-form";
    const label = document.createElement("label");
    label.htmlFor = "content-report-reason";
    label.textContent = "Motivo del reporte de contenido";
    const field = document.createElement("textarea");
    field.id = "content-report-reason";
    field.required = true;
    field.maxLength = 500;
    field.rows = 3;
    field.value = reason;
    field.disabled = busy || !available();
    field.setAttribute("aria-describedby", "content-report-help");
    field.addEventListener("input", () => {
      reason = field.value;
    });
    const help = document.createElement("small");
    help.id = "content-report-help";
    help.textContent =
      "Entre 1 y 500 caracteres. El motivo no se publica; evita datos personales. Reportar no cierra ni oculta el caso.";
    const message = document.createElement("p");
    message.setAttribute("role", "status");
    message.setAttribute("aria-live", "polite");
    message.textContent =
      notice ||
      (!navigator.onLine
        ? "Sin conexión: no se guardan ni se envían reportes pendientes."
        : !authenticated()
          ? "Inicia sesión para reportar contenido que consideres cuestionable."
          : "Una cuenta ajena al autor puede reportar una vez; tres denunciantes independientes activan la revisión.");
    const button = document.createElement("button");
    button.type = "submit";
    button.className = "secondary-button";
    button.textContent = busy ? "Enviando reporte…" : "Reportar contenido";
    button.disabled = busy || !available();
    form.append(label, field, help, message, button);
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (form.reportValidity()) void submit();
    });
    host.append(form);
  }

  async function submit() {
    const validReason = reason.trim();
    if (busy || !available() || !selected || selected.id < 1000000) return;
    if (!validReason || validReason.length > 500) {
      notice = "Indica un motivo de entre 1 y 500 caracteres.";
      draw();
      return;
    }
    const item = selected,
      request = generation;
    const current = () => request === generation && available();
    busy = true;
    notice = "Esperando respuesta del servidor…";
    draw();
    try {
      const tokenResponse = await fetch("/csrf", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (!tokenResponse.ok)
        throw new Error("No se pudo renovar el formulario. Inicia sesión de nuevo.");
      const token = await tokenResponse.json();
      if (!current()) return;
      if (!token?.token) throw new Error("No se pudo renovar el formulario.");
      const response = await fetch(`/api/publications/${item.id}/content-reports`, {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": token.token,
        },
        body: JSON.stringify({ reason: validReason }),
      });
      const result = await response.json().catch(() => null);
      if (!current()) return;
      if (!response.ok) {
        if (response.status === 401) {
          clearReason();
          window.RedAuxilioIdentityStatus?.setAuthenticated(false);
        }
        throw new Error(
          result?.message || "No se pudo reportar. Intenta de nuevo con sesión y conexión.",
        );
      }
      selected = result;
      clearReason();
      notice = "Reporte de contenido recibido por el servidor.";
      try {
        await window.RedAuxilioPublicationUI?.loadPublications();
      } catch {
        /* La denuncia ya se guardó: no reenviar el POST si falla la consulta pública. */
      }
    } catch (error) {
      if (request === generation)
        notice =
          error.name === "TypeError"
            ? "No se pudo conectar. No hay confirmación de recepción; revisa tu conexión antes de reintentar."
            : error.message || "No se pudo reportar. Intenta de nuevo con conexión.";
    } finally {
      if (request === generation) {
        busy = false;
        draw();
      }
    }
  }

  function invalidate() {
    generation++;
    busy = false;
    clearReason();
    notice = "";
    draw();
  }
  window.addEventListener("redauxilio:identity-change", invalidate);
  window.addEventListener("offline", invalidate);
  window.addEventListener("online", invalidate);
  window.RedAuxilioContentReport = {
    render(item, container) {
      if (selected?.id !== item.id) {
        generation++;
        busy = false;
        clearReason();
        notice = "";
      }
      selected = item;
      host = container;
      draw();
    },
  };
  window.RedAuxilioPublicationUI?.refreshConfirmation?.();
})();
