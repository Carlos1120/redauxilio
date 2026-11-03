"use strict";
/**
 * Contacto solo en memoria/DOM de la selección vigente; nunca storage, caché, URL o logs.
 * Limpia valores y descarta respuestas tardías al cambiar caso, sesión, cerrar detalle u offline.
 */
(() => {
  const find = (id) => document.getElementById(id);
  const outputs = new Set();
  let epoch = 0,
    selected = null,
    busy = false;
  const panel = find("own-contact-panel"),
    form = find("authorized-contact-form");
  function clear() {
    epoch++;
    busy = false;
    for (const output of outputs) output.textContent = "";
    outputs.clear();
    if (form) {
      find("authorized-contact-value").value = "";
      find("authorized-contact-consent").checked = false;
      find("authorized-contact-adult").checked = false;
    }
  }
  function configure(item) {
    clear();
    selected = item;
    if (!panel) return;
    panel.hidden = !item || item.category !== "MISSING_PERSON";
    for (const control of form.querySelectorAll("input,button"))
      control.disabled =
        panel.hidden ||
        !navigator.onLine ||
        (item.closed && control.id !== "authorized-contact-withdraw");
    find("authorized-contact-message").textContent =
      "El contacto es opcional y se autoriza por separado. No se guarda en el borrador.";
  }
  async function request(url, method, body, version) {
    const headers = { Accept: "application/json" };
    if (method !== "GET") {
      const tokenResponse = await fetch("/csrf", { credentials: "same-origin", cache: "no-store" });
      if (!tokenResponse.ok) throw new Error("No se pudo renovar el formulario.");
      const token = await tokenResponse.json();
      if (version !== epoch || !navigator.onLine) throw new Error("La sesión o selección cambió.");
      headers[token.headerName || "X-CSRF-TOKEN"] = token.token;
      if (body) headers["Content-Type"] = "application/json";
    }
    const response = await fetch(url, {
      method,
      credentials: "same-origin",
      cache: "no-store",
      headers,
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (version !== epoch) throw new Error("La sesión o selección cambió.");
    if (response.status === 401 || response.status === 403) {
      configure(null);
      throw new Error("La sesión ya no permite gestionar el contacto.");
    }
    if (!response.ok)
      throw new Error(
        response.status === 404
          ? "No hay contacto autorizado disponible."
          : "No se pudo completar la operación de contacto.",
      );
    const result = response.status === 204 ? null : await response.json();
    if (version !== epoch) throw new Error("La sesión o selección cambió.");
    return result;
  }
  function mount(container, id) {
    const section = document.createElement("section");
    section.className = "authorized-contact";
    const note = document.createElement("p");
    note.textContent =
      "Contacto adulto autorizado: consulta con conexión. No se guarda offline. Usa canales oficiales ante una emergencia.";
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Consultar contacto autorizado";
    const output = document.createElement("p");
    output.setAttribute("role", "status");
    outputs.add(output);
    button.addEventListener("click", async () => {
      const version = epoch;
      outputs.add(output);
      output.textContent = "Consultando autorización vigente…";
      try {
        if (!navigator.onLine) throw new Error("Conéctate para consultar el contacto autorizado.");
        const result = await request(
          `/api/publications/${id}/authorized-contact`,
          "GET",
          null,
          version,
        );
        if (version === epoch) output.textContent = result.value;
      } catch (error) {
        if (version === epoch) output.textContent = error.message;
      }
    });
    section.append(note, button, output);
    container.append(section);
  }
  async function change(method) {
    if (!selected || busy || !navigator.onLine) return;
    const version = epoch,
      id = selected.id;
    busy = true;
    const body =
      method === "PUT"
        ? {
            value: find("authorized-contact-value").value.trim(),
            consent: find("authorized-contact-consent").checked,
            adultContact: find("authorized-contact-adult").checked,
          }
        : null;
    try {
      await request(`/api/my/publications/${id}/authorized-contact`, method, body, version);
      if (version !== epoch) return;
      clear();
      find("authorized-contact-message").textContent =
        method === "PUT"
          ? "Autorización confirmada por el servidor. Puedes retirarla cuando lo necesites."
          : "Contacto retirado por el servidor; no volverá desde el historial.";
      window.dispatchEvent(new CustomEvent("redauxilio:contact-changed"));
    } catch (error) {
      if (version === epoch) find("authorized-contact-message").textContent = error.message;
    } finally {
      if (version === epoch) busy = false;
    }
  }
  if (form) {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (form.reportValidity()) void change("PUT");
    });
    find("authorized-contact-withdraw").addEventListener("click", () => void change("DELETE"));
  }
  window.addEventListener("redauxilio:publication-selected", (event) => configure(event.detail));
  window.addEventListener("redauxilio:identity-change", () => configure(null));
  window.addEventListener("offline", () => configure(null));
  window.addEventListener("pagehide", () => configure(null));
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) clear();
  });
  window.RedAuxilioAuthorizedContact = { clear, mount };
})();
