const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { runInNewContext } = require("node:vm");

/**
 * Dobles mínimos de DOM/Leaflet para comprobar el ciclo de consulta y capas sin CDN.
 * No simulan geometría del popup: el autoPan y la selección reales se verifican en navegador.
 */
function createMapHarness() {
  const layers = new Set();
  const events = {};
  const tileEvents = {};
  const status = { dataset: {} };
  let scale = 1;
  let moves = 0;
  const map = {
    setView() {
      return this;
    },
    project([latitude, longitude]) {
      return { x: longitude * scale, y: latitude * scale };
    },
    on(name, callback) {
      events[name] = callback;
    },
  };
  const layer = {
    addTo() {
      return this;
    },
    removeLayer(marker) {
      layers.delete(marker);
      marker.open = false;
    },
    clearLayers() {
      for (const marker of [...layers]) this.removeLayer(marker);
    },
  };
  const L = {
    map: () => map,
    tileLayer: () => ({
      addTo() {
        return this;
      },
      on(name, callback) {
        tileEvents[name] = callback;
      },
    }),
    layerGroup: () => layer,
    divIcon: (options) => options,
    marker: () => ({
      open: false,
      bindPopup(popup) {
        this.popup = popup;
        return this;
      },
      addTo() {
        layers.add(this);
        return this;
      },
    }),
  };
  const document = {
    getElementById: () => status,
    createElement: () => ({
      children: [],
      append(...nodes) {
        this.children.push(...nodes);
      },
      addEventListener(name, callback) {
        this[name] = callback;
      },
    }),
  };
  const window = { L };
  runInNewContext(readFileSync(join(__dirname, "../../main/resources/static/map.js"), "utf8"), {
    window,
    document,
    L,
  });
  const selected = [];
  const api = window.RedAuxilioMap.create(
    () => moves++,
    (item) => selected.push(item),
    {
      ASSISTANCE: "Punto de asistencia",
    },
  );
  return {
    api,
    layers,
    selected,
    status,
    tileEvent: (name, tile) => tileEvents[name]?.({ tile }),
    move: () => events.moveend(),
    get moves() {
      return moves;
    },
    zoom: () => {
      scale = 64;
      events.zoomend();
    },
  };
}
const reports = [
  { id: 1, latitude: 10, longitude: 10, title: "Refugio", category: "ASSISTANCE" },
  { id: 5, latitude: 12, longitude: 12, title: "Refugio cerrado", category: "ASSISTANCE" },
];

test("fin de carga conserva fallo cartográfico y mantiene los reportes utilizables", () => {
  const harness = createMapHarness();
  harness.tileEvent("loading");
  harness.tileEvent("tileerror", {});
  harness.tileEvent("load");
  assert.match(harness.status.textContent, /no está disponible/);
  harness.api.render(reports);
  assert.equal(harness.layers.size, 1);
});

// Cada evento lleva la identidad de la tesela, como TileEvent de Leaflet 1.9.4.
for (const recoveryEvent of ["tileload", "tileunload"]) {
  test("carga parcial conserva tesela fallida hasta " + recoveryEvent, () => {
    const harness = createMapHarness();
    const tileA = {};
    const tileB = {};
    harness.tileEvent("loading");
    harness.tileEvent("tileerror", tileA);
    harness.tileEvent("load");
    assert.match(harness.status.textContent, /no está disponible/);
    harness.tileEvent("loading");
    harness.tileEvent("tileload", tileB);
    harness.tileEvent("load");
    assert.match(harness.status.textContent, /no está disponible/);
    harness.tileEvent(recoveryEvent, tileA);
    assert.match(harness.status.textContent, /requiere conexión/);
  });
}

test("recuperar o retirar una tesela no oculta el fallo de otra", () => {
  const harness = createMapHarness();
  const tileA = {};
  const tileB = {};
  harness.tileEvent("tileerror", tileA);
  harness.tileEvent("tileerror", tileB);
  harness.tileEvent("tileerror", tileA);
  harness.tileEvent("tileload", tileA);
  harness.tileEvent("tileunload", {});
  harness.tileEvent("load");
  assert.match(harness.status.textContent, /no está disponible/);
  harness.tileEvent("tileunload", tileB);
  assert.match(harness.status.textContent, /requiere conexión/);
});

test("consulta por movimiento conserva el grupo abierto y permite seleccionar cada detalle", () => {
  const harness = createMapHarness();
  harness.api.render(reports);
  const marker = [...harness.layers][0];
  marker.open = true;
  harness.move();
  harness.api.render(JSON.parse(JSON.stringify(reports)));
  assert.equal(harness.moves, 1);
  assert.equal(harness.layers.size, 1);
  assert.equal([...harness.layers][0], marker);
  assert.equal(marker.open, true);
  for (const button of marker.popup.children.filter((node) =>
    node.textContent?.startsWith("Refugio"),
  )) {
    button.click();
  }
  assert.deepEqual(
    harness.selected.map((item) => item.id),
    [1, 5],
  );
});

test("cambios de datos, zoom y consulta vacía retiran capas obsoletas", () => {
  const harness = createMapHarness();
  harness.api.render(reports);
  const original = [...harness.layers][0];
  original.open = true;
  harness.api.render(reports.map((item) => ({ ...item, title: item.title + " actualizado" })));
  assert.equal(original.open, false);
  assert.equal(harness.layers.has(original), false);
  assert.equal(harness.layers.size, 1);
  harness.zoom();
  assert.equal(harness.layers.size, 2);
  harness.api.render([]);
  assert.equal(harness.layers.size, 0);
});
