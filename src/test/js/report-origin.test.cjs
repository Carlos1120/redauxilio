"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const vm = require("node:vm");

/** Ejecuta la consulta real con dobles mínimos de DOM; no simula revisión humana ni cartografía. */
async function loadReportView(items) {
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
      reset() {
        for (const id of ["category", "operational-status", "confidence-level", "review-status"]) {
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
  const context = vm.createContext({
    document: {
      getElementById: element,
      createElement: () => element(Symbol()),
      querySelectorAll: () => [],
    },
    window: {
      matchMedia: () => ({ matches: false, addEventListener() {} }),
      addEventListener(name, fn) {
        if (!listeners.has(name)) listeners.set(name, []);
        listeners.get(name).push(fn);
      },
    },
    navigator: {},
    indexedDB: { open: () => ({}) },
    fetch: async () => ({
      ok: true,
      json: async () => items,
      headers: { get: () => null },
    }),
    URLSearchParams,
    setTimeout,
    clearTimeout,
  });
  vm.runInContext(
    readFileSync(join(__dirname, "../../main/resources/static/app.js"), "utf8"),
    context,
    { filename: "app.js" },
  );
  await new Promise((resolve) => setImmediate(resolve));
  return { nodes, context, listeners, element };
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
