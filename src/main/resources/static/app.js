"use strict";
/**
 * Coordina la pantalla: consulta la API, sincroniza lista/mapa y conserva el borrador en IndexedDB.
 * map.js se ocupa de Leaflet; sw.js decide si una consulta se obtiene de red o de caché.
 */
const getElement = (id) => document.getElementById(id);
const appPageNames = new Set(["map", "publish", "own"]);
/** Muestra una sección principal y conserva la navegación por fragmento para enlaces directos. */
function setAppPage(page, { updateHistory = false, focus = false } = {}) {
  const selected = appPageNames.has(page) ? page : "map";
  getElement("main-content").dataset.activePage = selected;
  for (const panel of document.querySelectorAll("[data-app-page]")) {
    panel.hidden = panel.dataset.appPage !== selected;
  }
  for (const link of document.querySelectorAll("[data-app-page-link]")) {
    if (link.dataset.appPageLink === selected) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  }
  getElement("search-query").closest(".explore-search").hidden = selected !== "map";
  if (updateHistory) history.pushState({ appPage: selected }, "", `#page-${selected}`);
  if (selected === "map") reportMap?.invalidateSize?.();
  if (selected === "publish") window.RedAuxilioPublicationUI?.refreshLocationPreview?.();
  if (focus) document.querySelector(`[data-app-page="${selected}"]`)?.focus();
}
function appPageFromHash() {
  if (window.location.hash === "#draft-section") return "publish";
  if (window.location.hash === "#own-section") return "own";
  const match = window.location.hash.match(/^#page-(map|publish|own)$/);
  return match ? match[1] : "map";
}
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
  const examples = `${exampleCount} ${exampleCount === 1 ? "reporte de muestra" : "reportes de muestra"}`;
  const community = `${communityCount} ${communityCount === 1 ? "publicación comunitaria" : "publicaciones comunitarias"}`;
  if (communityCount === 0)
    return `${examples} ${exampleCount === 1 ? "encontrado" : "encontrados"}.`;
  if (exampleCount === 0)
    return `${community} ${communityCount === 1 ? "encontrada" : "encontradas"}.`;
  return `${examples} y ${community} encontrados.`;
}
let latestPublicationQueryId = 0;
let mapQueryTimer;
let detailTrigger;
let selectedReportId;
let currentItems = [];
let hasValidPublicQuery = false;
let currentQueryMessage = "";
let currentSourceMessage = "";
let suppressFocusReturn = false;
let exploreMapMovedByUser = false;
// Solo una interacción explícita con Leaflet activa el filtro geográfico.
for (const type of ["pointerdown", "wheel", "keydown"]) {
  getElement("report-map")?.addEventListener(type, () => {
    exploreMapMovedByUser = true;
  });
}
/** El panel usa el objeto ya consultado; en ancho estrecho conserva el diálogo modal. */
function showReportDetail(item, trigger, initial = false) {
  window.RedAuxilioAuthorizedContact?.clear();
  // Usa el objeto de la carga compartido con mapa/lista: la red conserva autoridad del servidor.
  if (trigger) detailTrigger = trigger;
  selectedReportId = item.id;
  getElement("detail-kicker").textContent =
    `${categoryLabels[item.category]} · ${isCommunityPublication(item) ? "publicación comunitaria" : "reporte de muestra"}`;
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
    if (document.querySelector(".explore-map-panel") && !exploreMapMovedByUser) return;
    // Debounce: espera 250 ms tras el último movimiento para no consultar por cada desplazamiento.
    clearTimeout(mapQueryTimer);
    mapQueryTimer = setTimeout(loadPublications, 250);
  },
  showReportDetail,
  categoryLabels,
);
setAppPage(appPageFromHash());
for (const link of document.querySelectorAll("[data-app-page-link]")) {
  link.addEventListener("click", (event) => {
    event.preventDefault();
    const page = link.dataset.appPageLink;
    setAppPage(page, { updateHistory: true, focus: true });
  });
}
window.addEventListener("popstate", () => setAppPage(appPageFromHash()));
window.addEventListener("hashchange", () => setAppPage(appPageFromHash()));
window.RedAuxilioPublicationUI = {
  reportMap,
  // Un módulo diferido puede incorporarse después de la primera respuesta sin repetir la consulta.
  refreshConfirmation: () => {
    const selected = currentItems.find((item) => item.id === selectedReportId);
    if (selected) showReportDetail(selected, null, true);
  },
  loadPublications: () => loadPublications(),
  /** Abre el detalle del elemento confirmado por el servidor, incluso si los filtros lo excluyen. */
  openPublishedReport: (item) => {
    if (!item?.id) return;
    window.location.hash = "#search-section";
    setAppPage("map", { focus: true });
    showReportDetail(item, null, false);
  },
  saveDraft: () => saveDraft(),
  // El borrador de cuenta conserva fotos cifradas; solo se retiran los blobs en memoria.
  clearEvidenceDraft: async () => {
    window.RedAuxilioEvidenceDraft?.clear();
    window.__redauxilioDraftEvidence = [];
  },
  clearDraft: () => window.RedAuxilioDraftVault.clear(),
};
const resetMap = getElement("reset-map");
if (resetMap) {
  resetMap.disabled = !reportMap;
  resetMap.addEventListener("click", () => reportMap?.reset());
}
function createReportElement(tag, className, text) {
  const element = document.createElement(tag);
  element.className = className;
  // Trata los datos como texto, no como HTML ejecutable que pudiera introducir un reporte.
  element.textContent = text;
  return element;
}
/** Resume la fecha del servidor en la tarjeta; la fecha completa sigue disponible en el detalle. */
function describeRelativeDate(value) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
  if (!Number.isFinite(minutes)) return "Fecha no disponible";
  if (minutes < 60) return `Hace ${Math.max(1, minutes)} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `Hace ${hours} ${hours === 1 ? "hora" : "horas"}`;
  const days = Math.floor(hours / 24);
  return `Hace ${days} ${days === 1 ? "día" : "días"}`;
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
    card.dataset.category = item.category;
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
    const status = createReportElement(
      "span",
      "explore-card-status",
      item.confidenceLevel === "VERIFICADA" ? "Verificado" : "Sin verificar",
    );
    card.append(status);
    card.append(
      createReportElement("span", "explore-card-time", describeRelativeDate(item.updatedAt)),
    );
    const detail = createReportElement("button", "secondary-button", "Ver detalle");
    detail.type = "button";
    detail.setAttribute("aria-label", `Ver detalle de ${item.title}`);
    detail.addEventListener("click", () => showReportDetail(item, detail));
    card.append(detail);
    getElement("publications").append(card);
  }
  const selected =
    items.find((item) => item.id === selectedReportId) ??
    (document.querySelector(".explore-map-panel") ? undefined : items[0]);
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
  for (const button of document.querySelectorAll(".quick-filters button[data-category]")) {
    button.setAttribute(
      "aria-pressed",
      String(button.dataset.category === getElement("category").value),
    );
  }
}
for (const button of document.querySelectorAll(".quick-filters button[data-category]")) {
  button.addEventListener("click", () => {
    getElement("category").value = button.dataset.category;
    syncQuickFilters();
    loadPublications();
  });
}
/** Abre el formulario existente desde los controles compactos de Figma. */
for (const trigger of document.querySelectorAll(".explore-filter-trigger, .explore-more-filters")) {
  trigger.addEventListener("click", () => {
    const panel = getElement("explore-filter-panel");
    panel.open = !panel.open;
    document
      .querySelectorAll(".explore-filter-trigger, .explore-more-filters")
      .forEach((button) => button.setAttribute("aria-expanded", String(panel.open)));
  });
}
for (const button of document.querySelectorAll("[data-explore-view]")) {
  button.addEventListener("click", () => {
    const view = button.dataset.exploreView;
    document.querySelector(".search-section").dataset.exploreView = view;
    document
      .querySelectorAll("[data-explore-view]")
      .forEach((control) => control.setAttribute("aria-pressed", String(control === button)));
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
      ...(exploreMapMovedByUser ? reportMap?.bounds() : {}),
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
      )
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    currentSourceMessage = savedAt
      ? `Consulta almacenada el ${new Date(savedAt).toLocaleString("es-CO")}; puede estar desactualizada.`
      : "Datos obtenidos del servidor.";
    hasValidPublicQuery = true;
    const countLabel = describeReportCount(currentItems);
    currentQueryMessage = `${countLabel} ${currentSourceMessage}`;
    renderSearchResults();
  } catch {
    if (query === latestPublicationQueryId) {
      // Una zona sin copia no invalida la ultima consulta: conservar lista y mapa juntos.
      // El aviso conserva origen/fecha y advierte tambien si se intentaron filtros nuevos.
      getElement("message").dataset.error = "true";
      currentQueryMessage = hasValidPublicQuery
        ? `No se pudo consultar la nueva zona o filtros. Se conservan los resultados de la zona y filtros consultados anteriormente; no corresponden a la nueva consulta. ${currentSourceMessage}`
        : "No se pudo consultar. No hay una consulta disponible para esta zona y filtros. Intenta de nuevo con conexión.";
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
/** El almacén cifra cada borrador de cuenta y separa los de visitantes. */
function saveDraft() {
  return window.RedAuxilioDraftVault.save({
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
    evidence: window.RedAuxilioEvidenceDraft?.serialize() || window.__redauxilioDraftEvidence || [],
    missingAge: getElement("missing-age").value,
    missingSeen: getElement("missing-seen").value,
  });
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
    await window.RedAuxilioDraftVault.clear();
    getElement("draft-form").reset();
    window.RedAuxilioEvidenceDraft?.clear();
    window.__redauxilioDraftEvidence = [];
    getElement("publication-category").dispatchEvent(new Event("change"));
    getElement("draft-message").textContent = "Borrador local descartado.";
  } catch {
    getElement("draft-message").textContent = "No se pudo descartar el borrador.";
  }
});
getElement("open-publication")?.addEventListener("click", (event) => {
  event.preventDefault();
  setAppPage("publish", { updateHistory: true, focus: true });
});
function restoreCurrentDraft() {
  return window.RedAuxilioDraftVault.read()
    .then((draft) => {
      if (draft) {
        getElement("draft-text").value = draft.text;
        getElement("latitude").value = draft.latitude;
        getElement("longitude").value = draft.longitude;
        getElement("publication-category").value = draft.category || "ASSISTANCE";
        getElement("publication-title").value = draft.title || "";
        getElement("assistance-subtype").value = draft.assistanceSubtype || "";
        getElement("assistance-status").value = draft.assistanceStatus || "";
        getElement("road-impact").value = draft.roadImpact || "";
        getElement("road-status").value = draft.roadStatus || "";
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
        window.RedAuxilioPublicationUI?.refreshLocationPreview?.();
        window.RedAuxilioPublicationUI?.refreshPublicationProgress?.();
        window.dispatchEvent?.(new Event("redauxilio:draft-restored"));
        getElement("draft-message").textContent = "Borrador local recuperado; no está publicado.";
      }
    })
    .catch(() => {
      getElement("draft-message").textContent = "Almacenamiento local no disponible.";
    });
}

function clearLocalFormOnSessionChange() {
  getElement("draft-form").reset();
  getElement("draft-form").hidden = false;
  getElement("publish-success").hidden = true;
  window.RedAuxilioEvidenceDraft?.clear();
  window.__redauxilioDraftEvidence = [];
  getElement("publication-category").dispatchEvent(new Event("change", { bubbles: true }));
  window.RedAuxilioPublishSteps?.reset?.();
  getElement("draft-message").textContent =
    "Se cambió de sesión. Solo se recuperarán los borradores autorizados.";
  void restoreCurrentDraft();
}
void restoreCurrentDraft();
window.addEventListener("redauxilio:identity-change", clearLocalFormOnSessionChange);
window.addEventListener("redauxilio:draft-session-invalidated", clearLocalFormOnSessionChange);
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
// Un enlace de acceso o un mensaje del servidor se consume una sola vez. Así una recarga,
// un marcador o el relanzamiento de la PWA siempre vuelven al Inicio público.
const initialParameters = new URLSearchParams(window.location?.search || "");
let consumedIdentityParameter = false;
for (const parameter of ["access", "error", "signedout"]) {
  if (initialParameters.has(parameter)) consumedIdentityParameter = true;
  initialParameters.delete(parameter);
}
if (consumedIdentityParameter) {
  const remainingQuery = initialParameters.toString();
  window.history?.replaceState?.(
    {},
    "",
    `${window.location?.pathname || "/"}${remainingQuery ? `?${remainingQuery}` : ""}${window.location?.hash || ""}`,
  );
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
  if (!authenticated && window.location.hash === "#verifier-workspace") {
    window.location.hash = "#main-content";
  }
  for (const notice of identityDialog.querySelectorAll(
    "#identity-csrf-message, [data-access-panel='login'] > .identity-message",
  ))
    notice.hidden = true;
  const status = getElement("identity-action-message");
  status.textContent = message;
  status.classList.toggle("is-error", authenticated && message.includes("no tiene permiso"));
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

// RA-14: un enlace a coincidencia abre solo el detalle publico vigente, nunca datos internos.
const linkedPublication = new URLSearchParams(window.location?.search || "").get("publication");
if (linkedPublication && /^[1-9][0-9]*$/.test(linkedPublication)) {
  fetch(`/api/publications/${linkedPublication}`, { cache: "no-store" })
    .then((response) => {
      if (!response.ok) throw new Error("Caso no disponible");
      return response.json();
    })
    .then((item) => showReportDetail(item, getElement("search-query")))
    .catch(() => {
      getElement("message").textContent = "La publicacion enlazada ya no esta disponible.";
    });
}
