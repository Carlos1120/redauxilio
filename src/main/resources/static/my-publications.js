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
  let sessionVersion = 0;
  let listVersion = 0;
  let historyVersion = 0;
  let saving = false;
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

  // Cada petición pertenece a una sesión y selección. Una respuesta tardía nunca repuebla el DOM.
  const authenticated = () => find("identity-open").dataset.authenticated === "true";
  const stillCurrent = (version) => version === sessionVersion && authenticated();
  function clearFields() {
    workspace.hidden = true;
    select.replaceChildren();
    history.replaceChildren();
    for (const id of [
      "own-title-input",
      "own-description",
      "own-location",
      "own-latitude",
      "own-longitude",
      "own-reason",
    ])
      find(id).value = "";
    states.replaceChildren();
    for (const form of [editForm, stateForm]) {
      for (const control of form.querySelectorAll("input, textarea, select, button"))
        control.disabled = true;
    }
  }
  function clearPrivate() {
    sessionVersion++;
    listVersion++;
    historyVersion++;
    mine = [];
    saving = false;
    clearFields();
    select.disabled = false;
    refresh.disabled = false;
  }
  async function receive(url, method = "GET", value = null, version = sessionVersion) {
    const ensureSession = () => {
      if (!stillCurrent(version))
        throw new Error("La sesión cambió; vuelve a consultar tus publicaciones.");
    };
    const headers = { Accept: "application/json" };
    async function read(response) {
      ensureSession();
      if (response.status === 401 || response.status === 403) {
        clearPrivate();
        window.RedAuxilioIdentityStatus?.setAuthenticated(false);
        notify("Debes iniciar sesión nuevamente para continuar.");
        throw new Error("Debes iniciar sesión nuevamente para continuar.");
      }
      const result = await response.json().catch(() => null);
      ensureSession();
      if (!response.ok) throw new Error(result?.message || "No se pudo completar la operación.");
      return result;
    }
    ensureSession();
    if (value !== null) {
      const token = await read(
        await fetch("/csrf", { credentials: "same-origin", cache: "no-store", headers }),
      );
      if (!token?.token) throw new Error("No fue posible renovar el formulario.");
      headers["X-CSRF-TOKEN"] = token.token;
      headers["Content-Type"] = "application/json";
    }
    ensureSession();
    return read(
      await fetch(url, {
        method,
        credentials: "same-origin",
        cache: "no-store",
        headers,
        ...(value === null ? {} : { body: JSON.stringify(value) }),
      }),
    );
  }

  // Solo campos del contrato conocido, insertados como texto; nunca renderizar JSON como HTML.
  function describeChange(change) {
    const labels = {
      title: "Nombre o tramo",
      description: "Descripción",
      location: "Sector",
      latitude: "Latitud",
      longitude: "Longitud",
      operationalStatus: "Estado",
      assistanceSubtype: "Tipo de punto",
      impactType: "Afectación",
      closed: "Cerrado",
    };
    const actions = {
      EDIT: "Corrección",
      STATUS: "Cambio de estado",
      CLOSE: "Cierre",
      REOPEN: "Reapertura",
    };
    const details = [];
    try {
      const before = JSON.parse(change.beforeSnapshot),
        after = JSON.parse(change.afterSnapshot);
      for (const [key, label] of Object.entries(labels)) {
        if (before[key] !== after[key])
          details.push(
            label + ": " + String(before[key] ?? "—") + " → " + String(after[key] ?? "—"),
          );
      }
    } catch {
      /* Historial antiguo sin fotografía legible conserva fecha, acción y motivo. */
    }
    return (
      new Date(change.changedAt).toLocaleString("es-CO") +
      " · " +
      (actions[change.action] || change.action) +
      (details.length ? " · " + details.join("; ") : "") +
      (change.reason ? " · Motivo: " + change.reason : "")
    );
  }
  async function loadHistory() {
    const item = current(),
      version = sessionVersion,
      request = ++historyVersion;
    history.replaceChildren();
    if (!item) return;
    try {
      const changes = await receive(
        `/api/my/publications/${item.id}/history`,
        "GET",
        null,
        version,
      );
      if (!stillCurrent(version) || request !== historyVersion || current()?.id !== item.id) return;
      for (const change of changes.length ? changes : [null]) {
        const entry = document.createElement("li");
        entry.textContent = change
          ? describeChange(change)
          : "Sin modificaciones después de la publicación inicial.";
        history.append(entry);
      }
    } catch (error) {
      if (stillCurrent(version) && request === historyVersion) notify(error.message);
    }
  }

  function showCurrent(withHistory = true) {
    const item = current();
    workspace.hidden = !item;
    if (!item) {
      clearFields();
      return;
    }
    find("own-title-input").value = item.title;
    find("own-description").value = item.description;
    find("own-location").value = item.location;
    find("own-latitude").value = item.latitude;
    find("own-longitude").value = item.longitude;
    for (const control of editForm.querySelectorAll("input, textarea, button")) {
      control.disabled = item.closed || saving;
    }
    states.replaceChildren();
    const nextStates = transitions[item.category]?.[item.operationalStatus] || [];
    for (const state of nextStates) {
      const option = document.createElement("option");
      option.value = state;
      option.textContent = state;
      states.append(option);
    }
    for (const control of stateForm.querySelectorAll("input, textarea, select, button"))
      control.disabled = saving;
    find("own-change-status").disabled = saving || !nextStates.length;
    find("own-reason").value = "";
    find("own-reason").required = item.closed;
    if (withHistory) void loadHistory();
  }

  async function loadMine(preferredId = null) {
    if (!authenticated()) {
      clearPrivate();
      notify("Inicia sesión para ver tus publicaciones.");
      return;
    }
    const version = sessionVersion,
      request = ++listVersion;
    historyVersion++;
    clearFields();
    refresh.disabled = true;
    notify("Cargando tus publicaciones…");
    try {
      const result = await receive("/api/my/publications", "GET", null, version);
      if (!stillCurrent(version) || request !== listVersion) return;
      mine = result;
      for (const item of mine) {
        const option = document.createElement("option");
        option.value = String(item.id);
        option.textContent = item.title + " · " + item.operationalStatus;
        select.append(option);
      }
      if (preferredId && mine.some((item) => item.id === preferredId))
        select.value = String(preferredId);
      showCurrent();
      notify(
        mine.length
          ? "Selecciona tu publicación para actualizarla. Los cambios quedan auditados."
          : "Todavía no tienes publicaciones visibles.",
      );
    } catch (error) {
      if (stillCurrent(version) && request === listVersion) {
        mine = [];
        clearFields();
        notify(error.message);
      }
    } finally {
      if (version === sessionVersion && request === listVersion) refresh.disabled = false;
    }
  }

  async function submitChange(button, url, method, payload, successMessage, id) {
    if (saving) return;
    const version = sessionVersion;
    saving = true;
    select.disabled = true;
    refresh.disabled = true;
    for (const form of [editForm, stateForm])
      for (const control of form.querySelectorAll("input, textarea, select, button"))
        control.disabled = true;
    button.disabled = true;
    notify("Guardando cambio…");
    try {
      await receive(url, method, payload, version);
      if (!stillCurrent(version)) return;
      saving = false;
      await loadMine(id);
      if (!stillCurrent(version)) return;
      notify(successMessage);
      try {
        await window.RedAuxilioPublicationUI?.loadPublications();
      } catch {
        /* Escritura confirmada: la consulta pública puede reintentarse sin reenviar el cambio. */
      }
    } catch (error) {
      if (stillCurrent(version)) notify(error.message);
    } finally {
      if (stillCurrent(version)) {
        saving = false;
        select.disabled = false;
        refresh.disabled = false;
        // Restaurar permisos sin sobrescribir una corrección que falló y puede reintentarse.
        const item = current();
        for (const control of editForm.querySelectorAll("input, textarea, button"))
          control.disabled = !item || item.closed;
        for (const control of stateForm.querySelectorAll("input, textarea, select, button"))
          control.disabled = !item;
      }
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
    clearPrivate();
    if (event.detail.authenticated) void loadMine();
    else notify("Inicia sesión para ver tus publicaciones.");
  });
  window.addEventListener("offline", () => {
    clearPrivate();
    notify(
      "Sin conexión. Conéctate para consultar o modificar tus publicaciones; no se guardan cambios pendientes.",
    );
  });
  clearPrivate();
})();
