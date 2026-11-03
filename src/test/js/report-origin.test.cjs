"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const vm = require("node:vm");

/** Ejecuta la consulta real con dobles mínimos de DOM; no simula revisión humana ni cartografía. */
async function loadReportView(items, savedAt = null, options = {}) {
  let mapItems = [];
  const nodes = new Map();
  const listeners = new Map();
  function element(id = "") {
    if (nodes.has(id)) return nodes.get(id);
    const node = {
      id,
      dataset: {},
      value: "",
      children: [],
      open: false,
      listeners: {},
      addEventListener(name, fn) {
        this.listeners[name] = fn;
      },
      dispatchEvent(event) {
        this.listeners[event.type]?.(event);
      },
      reset() {
        for (const id of [
          "category",
          "operational-status",
          "confidence-level",
          "review-status",
          "freshness-status",
        ]) {
          element(id).value = "";
        }
        element("include-closed").checked = false;
      },
      append(...children) {
        this.children.push(...children);
      },
      replaceChildren(...children) {
        this.children = children;
      },
      setAttribute() {},
      querySelectorAll: () => [],
      querySelector: () => null,
      focus() {},
      close() {
        this.open = false;
      },
      show() {
        this.open = true;
      },
    };
    nodes.set(id, node);
    return node;
  }
  element("freshness-status").value = options.filter ?? "";
  const context = vm.createContext({
    Date:
      options.now === undefined
        ? Date
        : class extends Date {
            static now() {
              return options.now;
            }
          },
    document: {
      getElementById: element,
      createElement: () => element(Symbol()),
      querySelectorAll: () => [],
    },
    window: {
      RedAuxilioMap: options.captureMap
        ? {
            create: () => ({
              bounds: () => ({}),
              render(items) {
                mapItems = items;
              },
            }),
          }
        : undefined,
      matchMedia: () => ({ matches: false, addEventListener() {} }),
      addEventListener(name, fn) {
        if (!listeners.has(name)) listeners.set(name, []);
        listeners.get(name).push(fn);
      },
    },
    navigator: {},
    Event: class {
      constructor(type) {
        this.type = type;
      }
    },
    indexedDB: {
      open: () => {
        const request = {};
        if (options.draftStorage)
          setImmediate(() => {
            request.result = {
              transaction: () => {
                const transaction = {};
                const done = (result) => {
                  const operation = { result };
                  setImmediate(() => {
                    operation.onsuccess?.();
                    transaction.oncomplete?.();
                  });
                  return operation;
                };
                transaction.objectStore = () => ({
                  get: () => done(options.draftStorage.value),
                  put: (value) => {
                    options.draftStorage.value = value;
                    return done();
                  },
                  delete: () => {
                    delete options.draftStorage.value;
                    return done();
                  },
                });
                return transaction;
              },
            };
            request.onsuccess?.();
          });
        return request;
      },
    },
    fetch: async () => ({
      ok: true,
      json: async () => items,
      headers: { get: () => savedAt },
    }),
    URLSearchParams,
    setTimeout,
    clearTimeout,
  });
  vm.runInContext(
    readFileSync(join(__dirname, "../../main/resources/static/freshness.js"), "utf8"),
    context,
  );
  vm.runInContext(
    readFileSync(join(__dirname, "../../main/resources/static/app.js"), "utf8"),
    context,
    { filename: "app.js" },
  );
  await new Promise((resolve) => setImmediate(resolve));
  return { nodes, context, listeners, element, getMapItems: () => mapItems };
}

function report(id) {
  return {
    id,
    category: "ASSISTANCE",
    title: `Reporte ${id}`,
    location: "Ubicación de prueba",
    description: "Descripción de prueba",
    operationalStatus: "Disponible",
    confidenceLevel: "REPORTADA",
    latitude: 4.15,
    longitude: -73.63,
    createdAt: "2026-10-14T12:00:00Z",
    updatedAt: "2026-10-14T12:00:00Z",
  };
}

test("consulta y detalle distinguen ejemplos, publicaciones y mezcla", async () => {
  for (const [items, expected, detailOrigin] of [
    [[report(1)], "1 reporte ficticio encontrado.", "reporte ficticio"],
    [[report(1000000)], "1 publicación comunitaria encontrada.", "publicación comunitaria"],
    [
      [report(1), report(1000000)],
      "1 reporte ficticio y 1 publicación comunitaria encontrados.",
      "publicación comunitaria",
    ],
  ]) {
    const { nodes, context } = await loadReportView(items);
    assert.match(nodes.get("message").textContent, new RegExp(expected, "u"));
    context.selectedItem = items.at(-1);
    vm.runInContext("showReportDetail(selectedItem, null, true)", context);
    assert.match(nodes.get("detail-kicker").textContent, new RegExp(detailOrigin, "u"));
  }
});

test("mapa y rótulos estáticos describen una consulta de origen mixto", () => {
  const template = readFileSync(
    join(__dirname, "../../main/resources/templates/index.html"),
    "utf8",
  );
  const map = readFileSync(join(__dirname, "../../main/resources/static/map.js"), "utf8");
  assert.match(template, /Ejemplos y publicaciones/u);
  assert.match(template, /Mapa de ejemplos ficticios y publicaciones comunitarias/u);
  assert.match(map, /Ubicaciones de reportes\. El fondo cartográfico requiere conexión/u);
});

test("app e identidad sincronizan una vez sin realimentar el evento de sesión", async () => {
  const { nodes, context, listeners } = await loadReportView([]);
  let dispatched = 0;
  context.navigator.onLine = true;
  context.CustomEvent = class {
    constructor(type, options) {
      this.type = type;
      this.detail = options.detail;
    }
  };
  context.window.dispatchEvent = (event) => {
    if (++dispatched > 5) throw new Error("Bucle de eventos de identidad");
    for (const fn of listeners.get(event.type) || []) fn(event);
  };
  nodes.get("identity-open").dataset.authenticated = "false";
  vm.runInContext(
    readFileSync(join(__dirname, "../../main/resources/static/identity-status.js"), "utf8"),
    context,
  );
  context.window.RedAuxilioIdentityStatus.setAuthenticated(true);
  assert.equal(dispatched, 1);
  assert.equal(nodes.get("identity-open").dataset.authenticated, "true");
});

test("revisión de contenido se muestra sin reemplazar confianza comunitaria", async () => {
  const reviewed = {
    ...report(1000000),
    reviewStatus: "EN_REVISION",
    confidenceLevel: "CONFIRMADA",
  };
  const { nodes, context } = await loadReportView([reviewed]);
  context.selectedItem = reviewed;
  vm.runInContext("showReportDetail(selectedItem, null, true)", context);
  function text(node) {
    return [node.textContent || "", ...(node.children || []).map(text)].join(" ");
  }
  assert.match(text(nodes.get("publications")), /En revisión · contenido cuestionado/);
  assert.match(text(nodes.get("detail-content")), /Confirmada por la comunidad/);
  assert.match(text(nodes.get("detail-content")), /Revisión de contenido/);
  assert.equal(reviewed.confidenceLevel, "CONFIRMADA");
});

/** Ejecuta app.js real: ambos filtros deben viajar separados en la misma consulta HTTP. */
test("consulta combina revisión de contenido y confianza sin sustituirlas", async () => {
  const { context, element } = await loadReportView([]);
  element("confidence-level").value = "CONFIRMADA";
  element("review-status").value = "EN_REVISION";
  let request;
  context.fetch = async (url) => {
    request = url;
    return { ok: true, json: async () => [], headers: { get: () => null } };
  };
  await context.window.RedAuxilioPublicationUI.loadPublications();
  const params = new URLSearchParams(request.split("?")[1]);
  assert.equal(params.get("confidenceLevel"), "CONFIRMADA");
  assert.equal(params.get("reviewStatus"), "EN_REVISION");
});

test("plantilla ofrece revisión independiente y confianza sin estados ajenos", () => {
  const html = readFileSync(join(__dirname, "../../main/resources/templates/index.html"), "utf8");
  const confidence = html.match(/<select id="confidence-level">([\s\S]*?)<\/select>/)[1];
  assert.doesNotMatch(confidence, /EN_REVISION|DESACTUALIZADA/);
  const review = html.match(/<select id="review-status">([\s\S]*?)<\/select>/)[1];
  assert.match(review, /SIN_REVISION/);
  assert.match(review, /EN_REVISION/);
});

/** El selector consulta al cambiar y el reset nativo limpia también la revisión. */
test("cambiar revisión actualiza consulta y limpiar filtros restablece ambas dimensiones", async () => {
  const { context, element } = await loadReportView([]);
  const requests = [];
  context.fetch = async (url) => {
    requests.push(new URLSearchParams(url.split("?")[1]));
    return { ok: true, json: async () => [], headers: { get: () => null } };
  };
  element("confidence-level").value = "CONFIRMADA";
  element("review-status").value = "EN_REVISION";
  element("review-status").listeners.change();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(requests[0].get("reviewStatus"), "EN_REVISION");
  assert.equal(requests[0].get("confidenceLevel"), "CONFIRMADA");
  element("clear-filters").listeners.click();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(requests[1].get("reviewStatus"), "");
  assert.equal(requests[1].get("confidenceLevel"), "");
});

/** La respuesta almacenada se degrada antes de compartirse con lista y detalle. */
test("respuesta offline expirada conserva revisión/confianza y advierte su antigüedad", async () => {
  const item = {
    ...report(1000000),
    reviewStatus: "EN_REVISION",
    confidenceLevel: "CONFIRMADA",
    freshnessStatus: "VIGENTE",
    lastValidActivityAt: "2020-01-01T00:00:00Z",
    freshnessExpiresAt: "2020-01-02T00:00:00Z",
  };
  const { nodes, context } = await loadReportView([item], "2020-01-01T10:00:00Z");
  const text = (n) => [n.textContent || "", ...(n.children || []).map(text)].join(" ");
  const cards = text(nodes.get("publications"));
  assert.match(cards, /Desactualizada/);
  assert.match(cards, /Confirmada por la comunidad/);
  assert.ok(cards.indexOf("En revisión") < cards.indexOf("Desactualizada"));
  assert.match(nodes.get("message").textContent, /puede estar desactualizada/);
  assert.match(text(nodes.get("detail-content")), /Información desactualizada/);
  const requests = [];
  context.fetch = async (url) => {
    requests.push(url);
    return { ok: true, json: async () => [], headers: { get: () => null } };
  };
  nodes.get("freshness-status").value = "DESACTUALIZADA";
  await vm.runInContext("loadPublications()", context);
  assert.match(requests[0], /freshnessStatus=DESACTUALIZADA/);
});

/** P2: un reloj cliente adelantado solo puede degradar copias marcadas por el SW. */
for (const cached of [false, true])
  test(`reloj adelantado: ${cached ? "caché" : "red"}, filtro Vigente y detalle`, async () => {
    const item = {
      ...report(1000000),
      freshnessStatus: "VIGENTE",
      freshnessExpiresAt: "2030-01-02T00:00:00Z",
      lastValidActivityAt: "2030-01-01T00:00:00Z",
      confidenceLevel: "CONFIRMADA",
      reviewStatus: "EN_REVISION",
    };
    const options = { now: Date.parse("2040-01-01T00:00:00Z"), captureMap: true };
    const view = await loadReportView([item], cached ? "2030-01-01T00:00:00Z" : null, options);
    const text = (n) => [n.textContent || "", ...(n.children || []).map(text)].join(" ");
    const expected = cached ? "DESACTUALIZADA" : "VIGENTE";
    assert.equal(view.getMapItems()[0].freshnessStatus, expected);
    assert.match(text(view.nodes.get("publications")), new RegExp(`Vigencia: ${expected}`));
    assert.match(
      text(view.nodes.get("detail-content")),
      cached ? /Información desactualizada/ : /Vigente/,
    );
    if (!cached)
      assert.doesNotMatch(text(view.nodes.get("detail-content")), /Información desactualizada/);
    assert.equal(view.getMapItems()[0].confidenceLevel, "CONFIRMADA");
    assert.equal(view.getMapItems()[0].reviewStatus, "EN_REVISION");
    const filtered = await loadReportView([item], cached ? "2030-01-01T00:00:00Z" : null, {
      ...options,
      filter: "VIGENTE",
    });
    assert.equal(filtered.getMapItems().length, cached ? 0 : 1);
    assert.match(
      filtered.nodes.get("message").textContent,
      cached ? /puede estar desactualizada/ : /Datos obtenidos del servidor/,
    );
  });

/** A7 ejecuta saveDraft/restauración reales de app.js con transacciones locales controladas. */
test("borrador HELP_REQUEST conserva los cuatro campos al recargar sin enviar", async () => {
  const storage = {};
  const first = await loadReportView([], null, { draftStorage: storage });
  first.element("publication-category").value = "HELP_REQUEST";
  for (const [id, value] of [
    ["help-need-type", "ALIMENTOS"],
    ["help-quantity", "20 familias"],
    ["help-urgency", "MEDIA"],
    ["help-requester", "Comunidad ficticia"],
  ])
    first.element(id).value = value;
  await vm.runInContext("saveDraft()", first.context);
  assert.equal(storage.value.needType, "ALIMENTOS");
  assert.equal(storage.value.approximateQuantity, "20 familias");
  const second = await loadReportView([], null, { draftStorage: storage });
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(second.element("publication-category").value, "HELP_REQUEST");
  assert.equal(second.element("help-need-type").value, "ALIMENTOS");
  assert.equal(second.element("help-quantity").value, "20 familias");
  assert.equal(second.element("help-urgency").value, "MEDIA");
  assert.equal(second.element("help-requester").value, "Comunidad ficticia");
  assert.match(second.element("draft-message").textContent, /recuperado/);
});
test("cerrar sesión elimina evidencias locales y conserva el resto del borrador", async () => {
  const storage = {
    value: { text: "reporte offline", title: "Punto", evidence: [{ name: "foto.webp" }] },
  };
  const view = await loadReportView([], null, { draftStorage: storage });
  await view.context.window.RedAuxilioPublicationUI.clearEvidenceDraft();
  assert.equal(storage.value.text, "reporte offline");
  assert.equal(storage.value.title, "Punto");
  assert.deepEqual(Array.from(storage.value.evidence), []);
});
test("detalle público de ayuda muestra datos propios y urgencia no oficial como texto", async () => {
  const item = {
    ...report(1000000),
    category: "HELP_REQUEST",
    operationalStatus: "ABIERTA",
    needType: "SALUD",
    approximateQuantity: "20 familias",
    declaredUrgency: "ALTA",
    requesterReference: "<img onerror=prueba>",
  };
  const view = await loadReportView([item]);
  const text = (n) => [n.textContent || "", ...(n.children || []).map(text)].join(" ");
  const detail = text(view.element("detail-content"));
  assert.match(detail, /Tipo de necesidad/);
  assert.match(detail, /20 familias/);
  assert.match(detail, /Urgencia declarada \(no oficial\)/);
  assert.match(detail, /<img onerror=prueba>/);
});

test("borrador MISSING_PERSON restaura solo datos seguros y jamás guarda contacto", async () => {
  const storage = {},
    first = await loadReportView([], null, { draftStorage: storage });
  first.element("publication-category").value = "MISSING_PERSON";
  first.element("missing-age").value = "13–17";
  first.element("missing-seen").value = "2026-11-03T08:30";
  first.element("authorized-contact-value").value = "secreto@example.invalid";
  first.element("authorized-contact-consent").checked = true;
  await vm.runInContext("saveDraft()", first.context);
  assert.equal(storage.value.missingAge, "13–17");
  assert.equal(storage.value.missingSeen, "2026-11-03T08:30");
  assert.doesNotMatch(JSON.stringify(storage.value), /secreto|contact|consent/i);
  const second = await loadReportView([], null, { draftStorage: storage });
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));
  assert.equal(second.element("missing-age").value, "13–17");
  assert.equal(second.element("missing-seen").value, "2026-11-03T08:30");
});
test("detalle desaparecido muestra datos estructurados y omite contacto incluido indebidamente en objeto", async () => {
  const item = {
    ...report(1000000),
    category: "MISSING_PERSON",
    operationalStatus: "DESAPARECIDA",
    missingPerson: {
      referenceName: "Referencia ficticia",
      ageRange: "18–29",
      physicalDescription: "Chaqueta gris",
      lastSeenPlace: "Parque",
      lastSeenAt: "2026-11-03T08:30:00Z",
    },
    contactValue: "secreto@example.invalid",
  };
  const ui = await loadReportView([item]);
  const text = (n) => [n.textContent || "", ...(n.children || []).map(text)].join(" ");
  const visible = text(ui.nodes.get("detail-content"));
  assert.match(visible, /Referencia ficticia/);
  assert.match(visible, /18–29/);
  assert.doesNotMatch(visible, /secreto@example.invalid/);
});
