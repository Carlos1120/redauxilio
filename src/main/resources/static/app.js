"use strict";
/**
 * Coordina la pantalla: consulta la API, sincroniza lista/mapa y conserva el borrador en IndexedDB.
 * map.js se ocupa de Leaflet; sw.js decide si una consulta se obtiene de red o de caché.
 */
const getElement = (id) => document.getElementById(id);
const categoryLabels = {
  ASSISTANCE: "Punto de asistencia",
  ROAD: "Vía afectada",
  MISSING_PERSON: "Persona desaparecida",
  HELP_REQUEST: "Solicitud de ayuda",
};
/** El esquema reserva los ID desde 1000000 para publicaciones persistidas; los inferiores son ejemplos. */
function isCommunityPublication(item) {
  return item.id >= 1000000;
}
/** Comunica el origen de cada subconjunto visible; recibe reportes de la API y devuelve texto para el estado. */
function describeReportCount(items) {
  const communityCount = items.filter(isCommunityPublication).length;
  const exampleCount = items.length - communityCount;
  if (items.length === 0) return "No se encontraron reportes.";
  const examples = `${exampleCount} ${exampleCount === 1 ? "reporte ficticio" : "reportes ficticios"}`;
  const community = `${communityCount} ${communityCount === 1 ? "publicación comunitaria" : "publicaciones comunitarias"}`;
  if (communityCount === 0) return `${examples} encontrados.`;
  if (exampleCount === 0) return `${community} encontradas.`;
  return `${examples} y ${community} encontrados.`;
}
let latestPublicationQueryId = 0;
let mapQueryTimer;
let detailTrigger;
let selectedReportId;
let currentItems = [];
let currentQueryMessage = "";
let currentSourceMessage = "";
let suppressFocusReturn = false;
/** El panel usa el objeto ya consultado; en ancho estrecho conserva el diálogo modal. */
function showReportDetail(item, trigger, initial = false) {
  window.RedAuxilioAuthorizedContact?.clear();
  // Usa el objeto de la carga compartido con mapa/lista: la red conserva autoridad del servidor.
  if (trigger) detailTrigger = trigger;
  selectedReportId = item.id;
  getElement("detail-kicker").textContent =
    `${categoryLabels[item.category]} · ${isCommunityPublication(item) ? "publicación comunitaria" : "reporte ficticio"}`;
  getElement("detail-title").textContent = item.title;
  const detail = getElement("detail-content");
  detail.replaceChildren();
  const evidenceGallery = createReportElement("div", "detail-evidence", "");
  evidenceGallery.setAttribute("aria-label", "Evidencias fotográficas");
  detail.append(evidenceGallery);
  fetch(`/api/publications/${item.id}/evidence`, { cache: "no-store" })
    .then((response) => (response.ok ? response.json() : []))
    .then((images) => {
      if (selectedReportId !== item.id || !Array.isArray(images)) return;
      for (const image of images) {
        const figure = document.createElement("figure");
        const element = document.createElement("img");
        element.src = image.url;
        element.alt = `Evidencia fotográfica ${images.indexOf(image) + 1}`;
        element.loading = "lazy";
        figure.append(element);
        evidenceGallery.append(figure);
      }
      evidenceGallery.hidden = images.length === 0;
    })
    .catch(() => {
      evidenceGallery.hidden = true;
    });
  const facts = createReportElement("div", "detail-facts", "");
  const specificFacts =
    item.category === "HELP_REQUEST" && item.needType
      ? [
          ["Tipo de necesidad", item.needType],
          ["Cantidad aproximada", item.approximateQuantity || "No indicada"],
          ["Urgencia declarada (no oficial)", item.declaredUrgency],
          ["Alias o comunidad solicitante", item.requesterReference],
        ]
      : item.assistanceSubtype
        ? [
            [
              "Tipo de punto",
              {
                SHELTER: "Refugio",
                COLLECTION: "Centro de acopio",
                MEDICAL_CENTER: "Centro médico",
              }[item.assistanceSubtype] || item.assistanceSubtype,
            ],
          ]
        : item.impactType
          ? [["Afectación", item.impactType]]
          : [];
  for (const [label, value] of [
    ...(item.missingPerson
      ? [
          ["Nombre o referencia", item.missingPerson.referenceName],
          ["Rango de edad", item.missingPerson.ageRange || "No indicado"],
          [
            "Última vez vista (aproximada)",
            new Date(item.missingPerson.lastSeenAt).toLocaleString("es-CO"),
          ],
          ["Último sector conocido", item.missingPerson.lastSeenPlace],
        ]
      : []),
    ...specificFacts,
    ["Estado operativo", item.operationalStatus],
    [
      "Confianza",
      item.confidenceLevel === "REPORTADA"
        ? "Reportada · sin verificar"
        : item.confidenceLevel === "CONFIRMADA"
          ? "Confirmada por la comunidad"
          : item.confidenceLevel === "VERIFICADA"
            ? "Verificada oficialmente"
            : item.confidenceLevel,
    ],
    [
      "Revisión de contenido",
      item.reviewStatus === "EN_REVISION"
        ? "En revisión · contenido cuestionado"
        : "Sin alerta de revisión",
    ],
    [
      "Vigencia",
      item.freshnessStatus === "DESACTUALIZADA"
        ? "Desactualizada · requiere información reciente"
        : item.freshnessStatus === "VIGENTE"
          ? "Vigente"
          : "Vigencia no disponible",
    ],
    [
      "Última actividad válida",
      item.lastValidActivityAt
        ? new Date(item.lastValidActivityAt).toLocaleString("es-CO")
        : "Sin dato",
    ],
    ["Ubicación aproximada", `${item.latitude.toFixed(3)}, ${item.longitude.toFixed(3)}`],
  ]) {
    const fact = createReportElement("div", "detail-fact", "");
    fact.append(createReportElement("small", "", label), createReportElement("strong", "", value));
    facts.append(fact);
  }
  if (item.reviewStatus === "EN_REVISION")
    detail.append(
      createReportElement("p", "review-notice", "En revisión · contenido cuestionado."),
    );
  if (item.freshnessStatus === "DESACTUALIZADA")
    detail.append(
      createReportElement(
        "p",
        "freshness-notice",
        "Información desactualizada: consulta una actualización reciente. No significa que sea falsa ni que el caso esté cerrado.",
      ),
    );
  detail.append(
    createReportElement("p", "detail-location", item.location),
    createReportElement("p", "detail-description", item.description),
    facts,
    createReportElement(
      "p",
      "detail-date",
      `Creado: ${new Date(item.createdAt).toLocaleString("es-CO")} · Actualizado: ${new Date(item.updatedAt).toLocaleString("es-CO")}`,
    ),
  );
  if (item.category === "MISSING_PERSON" && item.id >= 1000000)
    window.RedAuxilioAuthorizedContact?.mount(detail, item.id);
  const confirmation = createReportElement("div", "confirmation-actions", "");
  detail.append(confirmation);
  window.RedAuxilioConfirmation?.render(item, confirmation);
  const officialVerification = createReportElement("section", "official-verification", "");
  detail.append(officialVerification);
  window.RedAuxilioOfficialVerification?.render(item, officialVerification);
  const contentReport = createReportElement("div", "content-report-actions", "");
  detail.append(contentReport);
  window.RedAuxilioContentReport?.render(item, contentReport);
  window.RedAuxilioSightings?.render(item, detail);
  for (const card of getElement("publications").querySelectorAll(".card")) {
    card.dataset.selected = String(card.dataset.reportId === String(item.id));
  }
  const dialog = getElement("report-detail");
  if (window.matchMedia("(min-width: 1100px)").matches) {
    if (!dialog.open) dialog.show();
  } else if (!initial && !dialog.open) {
    dialog.showModal();
  }
}
getElement("close-detail").addEventListener("click", () => getElement("report-detail").close());
getElement("report-detail").addEventListener("close", () => {
  window.RedAuxilioAuthorizedContact?.clear();
  if (suppressFocusReturn) {
    suppressFocusReturn = false;
    return;
  }
  // Una consulta nueva puede haber eliminado el botón original; entonces el foco vuelve a la sección.
  if (detailTrigger?.isConnected) detailTrigger.focus();
  else getElement("search-section").focus();
});
const reportMap = window.RedAuxilioMap?.create(
  () => {
    // Debounce: espera 250 ms tras el último movimiento para no consultar por cada desplazamiento.
    clearTimeout(mapQueryTimer);
    mapQueryTimer = setTimeout(loadPublications, 250);
  },
  showReportDetail,
  categoryLabels,
);
window.RedAuxilioPublicationUI = {
  reportMap,
  // Un módulo diferido puede incorporarse después de la primera respuesta sin repetir la consulta.
  refreshConfirmation: () => {
    const selected = currentItems.find((item) => item.id === selectedReportId);
    if (selected) showReportDetail(selected, null, true);
  },
  loadPublications: () => loadPublications(),
  saveDraft: () => saveDraft(),
  // Al cerrar sesión elimina blobs privados de fotos y conserva el resto del borrador offline.
  clearEvidenceDraft: async () => {
    window.RedAuxilioEvidenceDraft?.clear();
    window.__redauxilioDraftEvidence = [];
    const db = await database;
    await new Promise((resolve, reject) => {
      const transaction = db.transaction("drafts", "readwrite");
      const store = transaction.objectStore("drafts");
      const request = store.get("current");
      request.onsuccess = () => {
        if (request.result) store.put({ ...request.result, evidence: [] }, "current");
      };
      transaction.oncomplete = resolve;
      transaction.onerror = () => reject(transaction.error);
      transaction.onabort = () => reject(transaction.error);
    });
  },
  clearDraft: () => executeDraftOperation("readwrite", (store) => store.delete("current")),
};
getElement("reset-map").disabled = !reportMap;
getElement("reset-map").addEventListener("click", () => reportMap?.reset());
function createReportElement(tag, className, text) {
  const element = document.createElement(tag);
  element.className = className;
  // Trata los datos como texto, no como HTML ejecutable que pudiera introducir un reporte.
  element.textContent = text;
  return element;
}
/** Actualiza mapa y tarjetas con el mismo subconjunto visible, después de filtros y búsqueda local. */
function renderPublications(items) {
  reportMap?.render(items);
  getElement("publications").replaceChildren();
  if (items.length === 0) {
    const empty = createReportElement("div", "empty-state", "");
    empty.append(
      createReportElement("h3", "", "No hay reportes para mostrar"),
      createReportElement(
        "p",
        "",
        "Cambia los filtros o la búsqueda, mueve el mapa o consulta de nuevo.",
      ),
    );
    getElement("publications").append(empty);
  }
  for (const item of items) {
    const card = createReportElement("article", "card", "");
    card.dataset.reportId = String(item.id);
    const header = createReportElement("div", "report-header", "");
    header.append(
      createReportElement("span", "tag", categoryLabels[item.category]),
      createReportElement("span", "report-id", `REPORTE ${String(item.id).padStart(3, "0")}`),
    );
    const facts = createReportElement("dl", "report-facts", "");
    for (const [label, value] of [
      ...(item.category === "HELP_REQUEST" && item.needType
        ? [
            ["Necesidad", item.needType],
            ["Urgencia declarada (no oficial)", item.declaredUrgency],
          ]
        : []),
      ["Estado operativo", item.operationalStatus],
      [
        "Confianza",
        item.confidenceLevel === "REPORTADA"
          ? "Reportada · sin verificar"
          : item.confidenceLevel === "CONFIRMADA"
            ? "Confirmada por la comunidad"
            : item.confidenceLevel === "VERIFICADA"
              ? "Verificada oficialmente"
              : item.confidenceLevel,
      ],
    ]) {
      const fact = document.createElement("div");
      fact.append(createReportElement("dt", "", label), createReportElement("dd", "", value));
      facts.append(fact);
    }
    if (item.reviewStatus === "EN_REVISION") {
      card.append(
        createReportElement(
          "p",
          "review-notice",
          "En revisión · contenido cuestionado. No equivale a cierre ni modifica su confianza.",
        ),
      );
    }
    if (item.freshnessStatus === "DESACTUALIZADA")
      card.append(
        createReportElement(
          "p",
          "freshness-notice",
          "Desactualizada · no hay respaldo reciente. El caso y su confianza se conservan.",
        ),
      );
    card.append(
      createReportElement(
        "p",
        "report-date",
        `Vigencia: ${item.freshnessStatus ?? "No disponible"} · Actividad válida: ${item.lastValidActivityAt ? new Date(item.lastValidActivityAt).toLocaleString("es-CO") : "Sin dato"}`,
      ),
    );
    // La ficha muestra la última actualización de la API, no una fecha de verificación.
    const date = createReportElement("p", "report-date", "Actualizado: ");
    const timestamp = createReportElement(
      "time",
      "",
      new Date(item.updatedAt).toLocaleString("es-CO"),
    );
    timestamp.dateTime = item.updatedAt;
    date.append(timestamp);
    card.append(
      header,
      createReportElement("h3", "", item.title),
      createReportElement("p", "report-location", item.location),
      facts,
      date,
    );
    const detail = createReportElement("button", "secondary-button", "Ver detalle");
    detail.type = "button";
    detail.setAttribute("aria-label", `Ver detalle de ${item.title}`);
    detail.addEventListener("click", () => showReportDetail(item, detail));
    card.append(detail);
    getElement("publications").append(card);
  }
  const selected = items.find((item) => item.id === selectedReportId) ?? items[0];
  if (selected) showReportDetail(selected, null, true);
  else {
    selectedReportId = undefined;
    if (getElement("report-detail").open) {
      suppressFocusReturn = true;
      getElement("report-detail").close();
    }
  }
}
/** Busca solo dentro de la respuesta actual, sin añadir una petición ni cambiar los filtros del servidor. */
function renderSearchResults() {
  const term = getElement("search-query").value.trim().toLocaleLowerCase("es-CO");
  const visible = term
    ? currentItems.filter((item) =>
        [item.title, item.location, item.description, categoryLabels[item.category]]
          .join(" ")
          .toLocaleLowerCase("es-CO")
          .includes(term),
      )
    : currentItems;
  renderPublications(visible);
  getElement("message").textContent =
    term && getElement("message").dataset.error !== "true"
      ? `Coincidencias: ${visible.length} de ${currentItems.length} reportes. ${currentSourceMessage}`
      : currentQueryMessage;
}
getElement("search-query").addEventListener("input", renderSearchResults);
/** Mantiene las pastillas rápidas sincronizadas con el filtro avanzado de categoría. */
function syncQuickFilters() {
  for (const button of document.querySelectorAll(".quick-filters button")) {
    button.setAttribute(
      "aria-pressed",
      String(button.dataset.category === getElement("category").value),
    );
  }
}
for (const button of document.querySelectorAll(".quick-filters button")) {
  button.addEventListener("click", () => {
    getElement("category").value = button.dataset.category;
    syncQuickFilters();
    loadPublications();
  });
}
/** Combina filtros independientes de confianza/revisión y área; comunica carga, error o caché. */
async function loadPublications() {
  const query = ++latestPublicationQueryId;
  getElement("message").textContent = "Consultando reportes…";
  getElement("message").dataset.error = "false";
  getElement("publications").setAttribute("aria-busy", "true");
  getElement("refresh").disabled = true;
  try {
    const parameters = new URLSearchParams({
      category: getElement("category").value,
      operationalStatus: getElement("operational-status").value,
      confidenceLevel: getElement("confidence-level").value,
      reviewStatus: getElement("review-status").value,
      freshnessStatus: getElement("freshness-status").value,
      includeClosed: String(getElement("include-closed").checked),
      ...reportMap?.bounds(),
    });
    const response = await fetch(`/api/publications?${parameters}`);
    if (!response.ok) throw new Error("Consulta rechazada");
    const items = await response.json();
    // Si otra consulta empezó después, esta respuesta ya es vieja y no debe reemplazar su pantalla.
    if (query !== latestPublicationQueryId) return;
    // El service worker añade esta cabecera SOLO a la copia guardada, para advertir su antigüedad.
    const savedAt = response.headers.get("X-RedAuxilio-Saved-At");
    // Solo una copia marcada por el SW se recalcula localmente; la red conserva la vigencia del servidor.
    currentItems = items
      .map((item) => (savedAt ? (window.RedAuxilioFreshness?.normalize(item) ?? item) : item))
      .filter(
        (item) =>
          !getElement("freshness-status").value ||
          item.freshnessStatus === getElement("freshness-status").value,
      );
    currentSourceMessage = savedAt
      ? `Consulta almacenada el ${new Date(savedAt).toLocaleString("es-CO")}; puede estar desactualizada.`
      : "Datos obtenidos del servidor.";
    const countLabel = describeReportCount(currentItems);
    currentQueryMessage = `${countLabel} ${currentSourceMessage}`;
    renderSearchResults();
  } catch {
    if (query === latestPublicationQueryId) {
      currentItems = [];
      currentSourceMessage = "";
      renderPublications([]);
      getElement("message").dataset.error = "true";
      currentQueryMessage =
        "No se pudo consultar. La categoría podría no tener una consulta guardada. Intenta de nuevo con conexión.";
      getElement("message").textContent = currentQueryMessage;
    }
  } finally {
    if (query === latestPublicationQueryId) {
      getElement("publications").setAttribute("aria-busy", "false");
      getElement("refresh").disabled = false;
    }
  }
}
// Al cambiar entre panel lateral y diálogo móvil, conserva el reporte sin bloquear el mapa.
window.matchMedia("(min-width: 1100px)").addEventListener("change", (event) => {
  const dialog = getElement("report-detail");
  if (dialog.open) {
    suppressFocusReturn = true;
    dialog.close();
  }
  if (event.matches && currentItems.length > 0) {
    const selected = currentItems.find((item) => item.id === selectedReportId) ?? currentItems[0];
    showReportDetail(selected, null, true);
  }
});
for (const id of [
  "category",
  "operational-status",
  "confidence-level",
  "review-status",
  "freshness-status",
  "include-closed",
]) {
  getElement(id).addEventListener("change", () => {
    syncQuickFilters();
    loadPublications();
  });
}
getElement("query-form").addEventListener("submit", (event) => {
  event.preventDefault();
  loadPublications();
});
getElement("clear-filters").addEventListener("click", () => {
  // Limpia el formulario, pero conserva el área actual; reset-map cambia la vista por separado.
  getElement("query-form").reset();
  syncQuickFilters();
  loadPublications();
});
// IndexedDB es almacenamiento de este navegador, separado de la caché HTTP y del servidor.
// Se guarda un único borrador bajo la clave "current"; nunca se publica mediante esta operación.
const database = new Promise((resolve, reject) => {
  const request = indexedDB.open("redauxilio-demo", 1);
  request.onupgradeneeded = () => request.result.createObjectStore("drafts");
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});
/** Ejecuta lectura/escritura local y solo confirma éxito cuando termina toda la transacción. */
async function executeDraftOperation(mode, operation) {
  const db = await database;
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("drafts", mode);
    const request = operation(transaction.objectStore("drafts"));
    let result;
    request.onsuccess = () => {
      result = request.result;
    };
    // Éxito de la petición no basta: la transacción aún podría abortarse antes de confirmar cambios.
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
/** Guarda todos los campos al confirmar la transacción local, incluso si el formulario está parcial. */
function saveDraft() {
  return executeDraftOperation("readwrite", (store) =>
    store.put(
      {
        text: getElement("draft-text").value,
        latitude: getElement("latitude").value,
        longitude: getElement("longitude").value,
        category: getElement("publication-category").value,
        title: getElement("publication-title").value,
        assistanceSubtype: getElement("assistance-subtype").value,
        assistanceStatus: getElement("assistance-status").value,
        roadImpact: getElement("road-impact").value,
        roadStatus: getElement("road-status").value,
        location: getElement("publication-location").value,
        needType: getElement("help-need-type").value,
        approximateQuantity: getElement("help-quantity").value,
        declaredUrgency: getElement("help-urgency").value,
        requesterReference: getElement("help-requester").value,
        evidence:
          window.RedAuxilioEvidenceDraft?.serialize() || window.__redauxilioDraftEvidence || [],
        missingAge: getElement("missing-age").value,
        missingSeen: getElement("missing-seen").value,
      },
      "current",
    ),
  );
}
getElement("draft-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await saveDraft();
    getElement("draft-message").textContent =
      "Borrador guardado localmente. No se ha enviado al servidor.";
  } catch {
    getElement("draft-message").textContent =
      "No se pudo guardar. Conserva el texto antes de cerrar esta página.";
  }
});
getElement("discard").addEventListener("click", async () => {
  try {
    await executeDraftOperation("readwrite", (store) => store.delete("current"));
    getElement("draft-form").reset();
    window.RedAuxilioEvidenceDraft?.clear();
    window.__redauxilioDraftEvidence = [];
    getElement("publication-category").dispatchEvent(new Event("change"));
    getElement("draft-message").textContent = "Borrador local descartado.";
  } catch {
    getElement("draft-message").textContent = "No se pudo descartar el borrador.";
  }
});
executeDraftOperation("readonly", (store) => store.get("current"))
  .then((draft) => {
    if (draft) {
      getElement("draft-text").value = draft.text;
      getElement("latitude").value = draft.latitude;
      getElement("longitude").value = draft.longitude;
      getElement("publication-category").value = draft.category || "ASSISTANCE";
      getElement("publication-title").value = draft.title || "";
      getElement("assistance-subtype").value = draft.assistanceSubtype || "SHELTER";
      getElement("assistance-status").value = draft.assistanceStatus || "DISPONIBLE";
      getElement("road-impact").value = draft.roadImpact || "DERRUMBE";
      getElement("road-status").value = draft.roadStatus || "BLOQUEADA";
      getElement("publication-location").value = draft.location || "";
      getElement("help-need-type").value = draft.needType || "";
      getElement("help-quantity").value = draft.approximateQuantity || "";
      getElement("help-urgency").value = draft.declaredUrgency || "";
      getElement("help-requester").value = draft.requesterReference || "";
      window.__redauxilioDraftEvidence = Array.isArray(draft.evidence) ? draft.evidence : [];
      window.RedAuxilioEvidenceDraft?.restore(window.__redauxilioDraftEvidence);
      getElement("missing-age").value = draft.missingAge || "";
      getElement("missing-seen").value = draft.missingSeen || "";
      getElement("publication-category").dispatchEvent(new Event("change"));
      getElement("draft-message").textContent = "Borrador local recuperado; no está publicado.";
    }
  })
  .catch(() => {
    getElement("draft-message").textContent = "Almacenamiento local no disponible.";
  });
const identityDialog = getElement("identity-dialog");
function setIdentityView(view) {
  const allowedViews = ["login", "register", "account"];
  const activeView = allowedViews.includes(view) ? view : "login";
  identityDialog.dataset.initialView = activeView;
  for (const panel of identityDialog.querySelectorAll("[data-access-panel]")) {
    panel.hidden = panel.dataset.accessPanel !== activeView;
  }
  const title = identityDialog.querySelector(`[data-access-panel="${activeView}"] h2`);
  if (title?.id) {
    identityDialog.setAttribute("aria-labelledby", title.id);
    title.tabIndex = -1;
  }
}
setIdentityView(identityDialog.dataset.initialView);
if (identityDialog.open) {
  identityDialog.close();
  identityDialog.showModal();
}
getElement("identity-open").addEventListener("click", () => {
  setIdentityView(
    getElement("identity-open").dataset.authenticated === "true" ? "account" : "login",
  );
  identityDialog.showModal();
});
window.addEventListener("redauxilio:identity-change", (event) => {
  // Notificación de estado: no volver a invocar setAuthenticated ni abrir un modal al reconectar.
  setIdentityView(event.detail.authenticated ? "account" : "login");
});
window.addEventListener("redauxilio:identity-action", (event) => {
  const { authenticated, message } = event.detail;
  window.RedAuxilioIdentityStatus?.setAuthenticated(authenticated);
  const status = getElement("identity-action-message");
  status.textContent = message;
  status.hidden = false;
  if (!identityDialog.open) identityDialog.showModal();
  identityDialog
    .querySelector(`[data-access-panel="${authenticated ? "account" : "login"}"] h2`)
    ?.focus();
});
getElement("identity-close").addEventListener("click", () => identityDialog.close());
for (const button of identityDialog.querySelectorAll("[data-show-view]")) {
  button.addEventListener("click", () => setIdentityView(button.dataset.showView));
}
identityDialog.addEventListener("click", (event) => {
  if (event.target === identityDialog) identityDialog.close();
});
// El registro habilita caché de recursos/consultas; el primer acceso aún necesita descargar recursos.
if ("serviceWorker" in navigator)
  navigator.serviceWorker.register("/sw.js").catch(() => {
    getElement("connection").textContent += " · caché offline no disponible";
  });
loadPublications();
