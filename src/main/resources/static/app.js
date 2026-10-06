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
  if (trigger) detailTrigger = trigger;
  selectedReportId = item.id;
  getElement("detail-kicker").textContent = `${categoryLabels[item.category]} · reporte ficticio`;
  getElement("detail-title").textContent = item.title;
  const detail = getElement("detail-content");
  detail.replaceChildren();
  const facts = createReportElement("div", "detail-facts", "");
  for (const [label, value] of [
    ["Estado operativo", item.operationalStatus],
    [
      "Confianza",
      item.confidenceLevel === "REPORTADA" ? "Reportada · sin verificar" : item.confidenceLevel,
    ],
    ["Ubicación aproximada", `${item.latitude.toFixed(3)}, ${item.longitude.toFixed(3)}`],
  ]) {
    const fact = createReportElement("div", "detail-fact", "");
    fact.append(createReportElement("small", "", label), createReportElement("strong", "", value));
    facts.append(fact);
  }
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
getElement("reset-map").disabled = !reportMap;
getElement("reset-map").addEventListener("click", () => reportMap?.reset());
function updateConnectionStatus() {
  // onLine indica conectividad del navegador; no garantiza que servidor o proveedor respondan.
  getElement("connection").dataset.offline = String(!navigator.onLine);
  getElement("connection").textContent = navigator.onLine
    ? "Conexión de red disponible"
    : "Sin conexión · consulta la caché";
}
window.addEventListener("online", updateConnectionStatus);
window.addEventListener("offline", updateConnectionStatus);
updateConnectionStatus();
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
      ["Estado operativo", item.operationalStatus],
      [
        "Confianza",
        item.confidenceLevel === "REPORTADA" ? "Reportada · sin verificar" : item.confidenceLevel,
      ],
    ]) {
      const fact = document.createElement("div");
      fact.append(createReportElement("dt", "", label), createReportElement("dd", "", value));
      facts.append(fact);
    }
    const date = createReportElement("p", "report-date", "Fecha del reporte: ");
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
/** Combina formulario y área del mapa, consulta la API y comunica carga, error o fuente guardada. */
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
    currentItems = items;
    currentSourceMessage = savedAt
      ? `Consulta almacenada el ${new Date(savedAt).toLocaleString("es-CO")}; puede estar desactualizada.`
      : "Datos obtenidos del servidor.";
    const countLabel =
      items.length === 1
        ? "1 reporte ficticio encontrado."
        : `${items.length} reportes ficticios encontrados.`;
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
for (const id of ["category", "operational-status", "confidence-level", "include-closed"]) {
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
getElement("draft-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await executeDraftOperation("readwrite", (store) =>
      store.put(
        {
          text: getElement("draft-text").value,
          latitude: getElement("latitude").value,
          longitude: getElement("longitude").value,
        },
        "current",
      ),
    );
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
      getElement("draft-message").textContent = "Borrador local recuperado; no está publicado.";
    }
  })
  .catch(() => {
    getElement("draft-message").textContent = "Almacenamiento local no disponible.";
  });
// El registro habilita caché de recursos/consultas; el primer acceso aún necesita descargar recursos.
if ("serviceWorker" in navigator)
  navigator.serviceWorker.register("/sw.js").catch(() => {
    getElement("connection").textContent += " · caché offline no disponible";
  });
loadPublications();
