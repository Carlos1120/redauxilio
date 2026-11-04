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
    HELP_REQUEST: {
      ABIERTA: ["EN ATENCIÓN", "SATISFECHA"],
      "EN ATENCIÓN": ["ABIERTA", "SATISFECHA"],
      SATISFECHA: ["ABIERTA"],
    },
    MISSING_PERSON: {
      DESAPARECIDA: ["ENCONTRADA", "CASO CERRADO"],
      ENCONTRADA: ["CASO CERRADO", "DESAPARECIDA"],
      "CASO CERRADO": ["DESAPARECIDA"],
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
    window.dispatchEvent?.(new CustomEvent("redauxilio:publication-selected", { detail: null }));
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
      "own-help-need-type",
      "own-help-quantity",
      "own-help-urgency",
      "own-help-requester",
      "own-missing-age",
      "own-missing-seen",
    ])
      find(id).value = "";
    states.replaceChildren();
    find("own-evidence").replaceChildren();
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
      needType: "Tipo de necesidad",
      approximateQuantity: "Cantidad aproximada",
      declaredUrgency: "Urgencia declarada",
      requesterReference: "Alias o comunidad",
      reviewStatus: "Revisión de contenido",
      referenceName: "Nombre o referencia",
      ageRange: "Rango de edad",
      physicalDescription: "Descripción física",
      lastSeenPlace: "Último sector conocido",
      lastSeenAt: "Última vez vista",
      contactAuthorized: "Contacto autorizado",
    };
    const actions = {
      CONTACT: "Autorización de contacto adulto",
      CONTACT_WITHDRAW: "Retiro de contacto",
      EDIT: "Corrección",
      STATUS: "Cambio de estado",
      CLOSE: "Cierre",
      REOPEN: "Reapertura",
      REVIEW: "Revisión de contenido",
    };
    const details = [];
    try {
      const before = JSON.parse(change.beforeSnapshot),
        after = JSON.parse(change.afterSnapshot);
      Object.assign(before, before.missingPerson);
      Object.assign(after, after.missingPerson);
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

  /** Expone acciones de retiro solo en el área privada del autor autenticado. */
  async function loadEvidence(item, version) {
    const gallery = find("own-evidence");
    gallery.replaceChildren();
    try {
      const response = await fetch(`/api/publications/${item.id}/evidence`, {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      const images = response.ok ? await response.json() : [];
      if (!stillCurrent(version) || current()?.id !== item.id) return;
      for (const image of images) {
        const figure = document.createElement("figure");
        const preview = document.createElement("img");
        const remove = document.createElement("button");
        preview.src = image.url;
        preview.alt = "Evidencia adjunta";
        remove.type = "button";
        remove.className = "secondary-button";
        remove.textContent = "Retirar imagen";
        remove.disabled = item.closed || saving;
        remove.addEventListener("click", async () => {
          if (!stillCurrent(version) || item.closed) return;
          try {
            const token = await fetch("/csrf", {
              credentials: "same-origin",
              cache: "no-store",
            }).then((r) => r.json());
            const result = await fetch(`/api/my/publications/${item.id}/evidence/${image.id}`, {
              method: "DELETE",
              credentials: "same-origin",
              cache: "no-store",
              headers: { "X-CSRF-TOKEN": token.token },
            });
            if (!result.ok) throw new Error("No se pudo retirar la imagen.");
            await loadEvidence(item, version);
          } catch (error) {
            if (stillCurrent(version)) notify(error.message || "No se pudo retirar la imagen.");
          }
        });
        figure.append(preview, remove);
        gallery.append(figure);
      }
      if (!images.length) gallery.textContent = "No hay imágenes adjuntas.";
    } catch {
      if (stillCurrent(version)) gallery.textContent = "No se pudieron consultar las imágenes.";
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
    for (const control of editForm.querySelectorAll("input, textarea, select, button")) {
      control.disabled = item.closed || saving;
    }
    const isHelp = item.category === "HELP_REQUEST";
    void loadEvidence(item, sessionVersion);
    find("own-help-fields").hidden = !isHelp;
    for (const [id, value] of [
      ["need-type", item.needType],
      ["quantity", item.approximateQuantity],
      ["urgency", item.declaredUrgency],
      ["requester", item.requesterReference],
    ]) {
      const control = find(`own-help-${id}`);
      control.value = isHelp ? (value ?? "") : "";
      control.disabled = !isHelp || item.closed || saving;
    }
    const isMissing = item.category === "MISSING_PERSON";
    find("own-missing-fields").hidden = !isMissing;
    find("own-missing-age").value = item.missingPerson?.ageRange || "";
    if (isMissing)
      window.RedAuxilioMissingPerson?.setSeenTime(
        find("own-missing-seen"),
        item.missingPerson?.lastSeenAt,
      );
    for (const id of ["age", "seen"])
      find(`own-missing-${id}`).disabled = !isMissing || item.closed || saving;
    find("own-description").maxLength = isMissing ? 500 : 1000;
    window.dispatchEvent?.(
      new CustomEvent("redauxilio:publication-selected", {
        detail: { id: item.id, category: item.category, closed: item.closed },
      }),
    );
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
    find("own-reason").required =
      item.closed ||
      (item.category === "MISSING_PERSON" &&
        item.operationalStatus === "ENCONTRADA" &&
        states.value === "DESAPARECIDA");
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
      const previous = current();
      const result = await receive(url, method, payload, version);
      // El guardado idéntico de ayuda no debe anunciar una entrada de historial inexistente.
      const unchangedHelp =
        method === "PATCH" &&
        ["HELP_REQUEST", "MISSING_PERSON"].includes(previous?.category) &&
        Number.isInteger(previous.confirmationVersion) &&
        result?.confirmationVersion === previous.confirmationVersion &&
        result.updatedAt === previous.updatedAt;
      if (!stillCurrent(version)) return;
      saving = false;
      await loadMine(id);
      if (!stillCurrent(version)) return;
      notify(
        unchangedHelp
          ? "Sin cambios: se conservan fecha, historial y confirmaciones."
          : successMessage,
      );
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
        for (const control of editForm.querySelectorAll("input, textarea, select, button"))
          control.disabled = !item || item.closed;
        for (const id of ["need-type", "quantity", "urgency", "requester"])
          find(`own-help-${id}`).disabled =
            !item || item.closed || item.category !== "HELP_REQUEST";
        for (const id of ["age", "seen"])
          find(`own-missing-${id}`).disabled =
            !item || item.closed || item.category !== "MISSING_PERSON";
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
      ...(item.category === "MISSING_PERSON"
        ? {
            missingPerson: {
              referenceName: find("own-title-input").value.trim(),
              physicalDescription: find("own-description").value.trim(),
              lastSeenPlace: find("own-location").value.trim(),
              ageRange: find("own-missing-age").value || null,
              lastSeenAt: window.RedAuxilioMissingPerson.readSeenTime(find("own-missing-seen")),
            },
          }
        : {}),
      ...(item.category === "HELP_REQUEST"
        ? {
            needType: find("own-help-need-type").value,
            approximateQuantity: find("own-help-quantity").value.trim() || null,
            declaredUrgency: find("own-help-urgency").value,
            requesterReference: find("own-help-requester").value,
          }
        : {}),
    };
    if (
      item.category === "HELP_REQUEST" &&
      (payload.requesterReference.includes("@") ||
        /(?:[0-9][\s().+-]*){7,}/.test(payload.requesterReference))
    ) {
      notify("Usa un alias o comunidad sin correo, teléfono ni documento.");
      return;
    }
    void submitChange(
      find("own-save"),
      `/api/my/publications/${item.id}`,
      "PATCH",
      payload,
      "Corrección guardada e incorporada al historial.",
      item.id,
    );
  });
  window.addEventListener("redauxilio:contact-changed", () => {
    if (current()) void loadHistory();
  });
  states.addEventListener("change", () => {
    const item = current();
    find("own-reason").required =
      !!item &&
      (item.closed ||
        (item.category === "MISSING_PERSON" &&
          item.operationalStatus === "ENCONTRADA" &&
          states.value === "DESAPARECIDA"));
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
