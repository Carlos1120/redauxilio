"use strict";
/**
 * Gestión privada RA-04. Todas las operaciones se autorizan otra vez en el servidor,
 * emplean CSRF actual y no se guardan en el service worker ni en IndexedDB.
 */
(() => {
  const find = (id) => document.getElementById(id);
  const workspace = find("own-workspace");
  const select = find("own-select");
  const message = find("own-message");
  const refresh = find("own-refresh");
  const editForm = find("own-edit-form");
  const stateForm = find("own-status-form");
  const states = find("own-next-state");
  const history = find("own-history");
  let mine = [];
  const transitions = {
    ASSISTANCE: {
      DISPONIBLE: ["CAPACIDAD LIMITADA", "LLENO", "CERRADO"],
      "CAPACIDAD LIMITADA": ["DISPONIBLE", "LLENO", "CERRADO"],
      LLENO: ["DISPONIBLE", "CAPACIDAD LIMITADA", "CERRADO"],
      CERRADO: ["DISPONIBLE", "CAPACIDAD LIMITADA"],
    },
    ROAD: {
      BLOQUEADA: ["PARCIALMENTE HABILITADA", "HABILITADA"],
      "PARCIALMENTE HABILITADA": ["BLOQUEADA", "HABILITADA"],
      HABILITADA: ["BLOQUEADA", "PARCIALMENTE HABILITADA"],
    },
  };

  const current = () => mine.find((item) => String(item.id) === select.value);
  function notify(value) {
    message.textContent = value;
  }

  async function receive(url, method = "GET", value = null) {
    const headers = { Accept: "application/json" };
    if (value !== null) {
      // Un token obtenido antes de desconectarse no se reutiliza.
      const response = await fetch("/csrf", {
        credentials: "same-origin",
        cache: "no-store",
        headers,
      });
      if (!response.ok) throw new Error("Inicia sesión nuevamente para guardar.");
      headers["X-CSRF-TOKEN"] = (await response.json()).token;
      headers["Content-Type"] = "application/json";
    }
    const response = await fetch(url, {
      method,
      credentials: "same-origin",
      cache: "no-store",
      headers,
      ...(value === null ? {} : { body: JSON.stringify(value) }),
    });
    if (response.status === 401) throw new Error("Debes iniciar sesión para continuar.");
    const result = await response.json().catch(() => null);
    if (!response.ok) throw new Error(result?.message || "No se pudo completar la operación.");
    return result;
  }

  async function loadHistory() {
    const item = current();
    history.replaceChildren();
    if (!item) return;
    try {
      const changes = await receive(`/api/my/publications/${item.id}/history`);
      if (!changes.length) {
        const entry = document.createElement("li");
        entry.textContent = "Sin modificaciones después de la publicación inicial.";
        history.append(entry);
      }
      for (const change of changes) {
        const entry = document.createElement("li");
        const when = new Date(change.changedAt).toLocaleString("es-CO");
        entry.textContent = `${when} · ${change.action}${
          change.reason ? " · Motivo: " + change.reason : ""
        }`;
        history.append(entry);
      }
    } catch (error) {
      notify(error.message);
    }
  }

  function showCurrent() {
    const item = current();
    workspace.hidden = !item;
    if (!item) return;
    find("own-title-input").value = item.title;
    find("own-description").value = item.description;
    find("own-location").value = item.location;
    find("own-latitude").value = item.latitude;
    find("own-longitude").value = item.longitude;
    for (const control of editForm.querySelectorAll("input, textarea, button")) {
      control.disabled = item.closed;
    }
    states.replaceChildren();
    const nextStates = transitions[item.category]?.[item.operationalStatus] || [];
    for (const state of nextStates) {
      const option = document.createElement("option");
      option.value = state;
      option.textContent = state;
      states.append(option);
    }
    find("own-change-status").disabled = !nextStates.length;
    find("own-reason").value = "";
    find("own-reason").required = item.closed;
    void loadHistory();
  }

  async function loadMine(preferredId = null) {
    if (find("identity-open").dataset.authenticated !== "true") {
      mine = [];
      workspace.hidden = true;
      notify("Inicia sesión para ver tus publicaciones.");
      return;
    }
    refresh.disabled = true;
    notify("Cargando tus publicaciones…");
    try {
      mine = await receive("/api/my/publications");
      select.replaceChildren();
      for (const item of mine) {
        const option = document.createElement("option");
        option.value = String(item.id);
        option.textContent = `${item.title} · ${item.operationalStatus}`;
        select.append(option);
      }
      if (preferredId && mine.some((item) => item.id === preferredId)) {
        select.value = String(preferredId);
      }
      showCurrent();
      notify(
        mine.length
          ? "Selecciona tu publicación para actualizarla. Los cambios quedan auditados."
          : "Todavía no tienes publicaciones visibles.",
      );
    } catch (error) {
      workspace.hidden = true;
      notify(error.message);
    } finally {
      refresh.disabled = false;
    }
  }

  async function submitChange(button, url, method, payload, successMessage, id) {
    button.disabled = true;
    notify("Guardando cambio…");
    try {
      await receive(url, method, payload);
      await loadMine(id);
      notify(successMessage);
      try {
        await window.RedAuxilioPublicationUI?.loadPublications();
      } catch {
        // La escritura ya fue confirmada. El mapa puede actualizarse al consultar de nuevo.
      }
    } catch (error) {
      notify(error.message);
    } finally {
      button.disabled = false;
    }
  }

  refresh.addEventListener("click", () => void loadMine());
  select.addEventListener("change", showCurrent);
  editForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const item = current();
    if (!item || item.closed || !editForm.reportValidity()) return;
    const payload = {
      category: item.category,
      assistanceSubtype: item.assistanceSubtype,
      title: find("own-title-input").value,
      description: find("own-description").value,
      location: find("own-location").value,
      operationalStatus: item.operationalStatus,
      impactType: item.impactType,
      latitude: Number(find("own-latitude").value),
      longitude: Number(find("own-longitude").value),
    };
    void submitChange(
      find("own-save"),
      `/api/my/publications/${item.id}`,
      "PATCH",
      payload,
      "Corrección guardada e incorporada al historial.",
      item.id,
    );
  });
  stateForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const item = current();
    if (!item || !stateForm.reportValidity()) return;
    void submitChange(
      find("own-change-status"),
      `/api/my/publications/${item.id}/status`,
      "POST",
      { operationalStatus: states.value, reason: find("own-reason").value },
      "Estado actualizado y registrado en el historial.",
      item.id,
    );
  });
  window.addEventListener("redauxilio:identity-change", (event) => {
    if (!event.detail.authenticated) {
      mine = [];
      workspace.hidden = true;
      notify("Inicia sesión para ver tus publicaciones.");
    }
  });
})();
