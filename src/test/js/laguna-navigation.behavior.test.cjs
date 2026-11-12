"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const script = fs.readFileSync(
  path.join(__dirname, "../../main/resources/static/laguna-navigation.js"),
  "utf8",
);

/**
 * Reproduce el DOM mínimo requerido por la barra, sin sustituir la sesión real.
 * Las acciones se disparan igual que un clic, cierre de diálogo o cambio de hash.
 */
function loadNavigation(hash = "#main-content") {
  const tabs = ["home", "explore", "publish", "account"].map((key) => ({
    dataset: { lagunaTab: key },
    attributes: new Map(),
    events: {},
    setAttribute(name, value) {
      this.attributes.set(name, value);
    },
    removeAttribute(name) {
      this.attributes.delete(name);
    },
    addEventListener(name, callback) {
      this.events[name] = callback;
    },
  }));
  const dialog = {
    open: false,
    events: {},
    addEventListener(name, callback) {
      this.events[name] = callback;
    },
  };
  let openCount = 0;
  const existingTrigger = {
    click() {
      openCount++;
      dialog.open = true;
    },
  };
  const listeners = {};
  const window = {
    location: { hash },
    addEventListener(name, callback) {
      listeners[name] = callback;
    },
  };
  const document = {
    querySelectorAll() {
      return tabs;
    },
    getElementById(id) {
      if (id === "laguna-mobile-account") return tabs[3];
      if (id === "identity-open") return existingTrigger;
      if (id === "identity-dialog") return dialog;
      return null;
    },
  };
  vm.runInNewContext(script, { window, document }, { filename: "laguna-navigation.js" });
  return {
    tab(key) {
      return tabs.find((item) => item.dataset.lagunaTab === key);
    },
    dialog,
    window,
    listeners,
    opened: () => openCount,
    selected: () => tabs.filter((item) => item.attributes.has("aria-current")).map((t) => t.dataset.lagunaTab),
  };
}

test("Explorar inicia seleccionado si la ubicación lleva al mapa", () => {
  const view = loadNavigation("#search-section");
  assert.deepEqual(view.selected(), ["explore"]);
});

test("Cuenta móvil reutiliza el disparador de sesión y nunca duplica el acceso", () => {
  const view = loadNavigation("#search-section");
  view.tab("account").events.click();
  assert.equal(view.opened(), 1);
  assert.equal(view.dialog.open, true);
  assert.deepEqual(view.selected(), ["account"]);
  view.dialog.open = false;
  view.dialog.events.close();
  assert.deepEqual(view.selected(), ["explore"]);
});

test("La barra alterna una sola selección al navegar entre pantallas", () => {
  const view = loadNavigation();
  assert.deepEqual(view.selected(), ["home"]);
  view.tab("publish").events.click();
  assert.deepEqual(view.selected(), ["publish"]);
  view.window.location.hash = "#own-section";
  view.listeners.hashchange();
  assert.deepEqual(view.selected(), ["account"]);
});

test("No se cambia la pestaña activa mientras el diálogo de cuenta está abierto", () => {
  const view = loadNavigation();
  view.tab("account").events.click();
  view.window.location.hash = "#search-section";
  view.listeners.hashchange();
  assert.deepEqual(view.selected(), ["account"]);
});
