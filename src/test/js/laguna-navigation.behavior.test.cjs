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
  const desktopTabs = ["#main-content", "#search-section", "#own-section"].map((href) => ({
    href,
    attributes: new Map(),
    getAttribute(name) {
      return name === "href" ? this.href : this.attributes.get(name);
    },
    setAttribute(name, value) {
      this.attributes.set(name, value);
    },
    removeAttribute(name) {
      this.attributes.delete(name);
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
  const roleOptions = ["citizen", "operator", "verifier"].map((mode) => ({
    dataset: { loginAccess: mode },
    attributes: new Map(),
    events: {},
    setAttribute(name, value) {
      this.attributes.set(name, value);
    },
    addEventListener(name, callback) {
      this.events[name] = callback;
    },
  }));
  const submit = { textContent: "Continuar como ciudadano" };
  const loginForm = { dataset: { loginAccess: "citizen" }, querySelector: () => submit };
  const roleHelp = { textContent: "" };
  const window = {
    location: { hash },
    addEventListener(name, callback) {
      listeners[name] = callback;
    },
  };
  const document = {
    body: { dataset: {} },
    querySelectorAll(selector) {
      if (selector === ".site-header .header-content > nav a") return desktopTabs;
      if (selector === ".laguna-bottom-nav [data-laguna-tab]") return tabs;
      if (selector === "[data-login-access][aria-pressed]") return roleOptions;
      return [];
    },
    querySelector() {
      return loginForm;
    },
    getElementById(id) {
      if (id === "identity-access-help") return roleHelp;
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
    selected: () =>
      tabs.filter((item) => item.attributes.has("aria-current")).map((t) => t.dataset.lagunaTab),
    desktopSelected: () =>
      desktopTabs.filter((item) => item.attributes.has("aria-current")).map((item) => item.href),
    page: () => document.body.dataset.lagunaPage,
    role: () => loginForm.dataset.loginAccess,
    roleOptions,
    roleHelp,
    submit,
  };
}

test("Explorar inicia seleccionado si la ubicación lleva al mapa", () => {
  const view = loadNavigation("#search-section");
  assert.equal(view.page(), "explore");
  assert.deepEqual(view.selected(), ["explore"]);
  assert.deepEqual(view.desktopSelected(), ["#search-section"]);
});

test("Verificaciones abre su propia sección sin seleccionar un destino ciudadano", () => {
  const view = loadNavigation("#verifier-workspace");
  assert.equal(view.page(), "verifier");
  assert.deepEqual(view.selected(), []);
  assert.deepEqual(view.desktopSelected(), []);
});

test("la elección de acceso cambia la orientación, sin tocar la sesión", () => {
  const view = loadNavigation();
  view.roleOptions[1].events.click();
  assert.equal(view.role(), "operator");
  assert.equal(view.submit.textContent, "Continuar como auditor");
  assert.match(view.roleHelp.textContent, /permiso de operador/);
  assert.equal(view.roleOptions[1].attributes.get("aria-pressed"), "true");
  assert.equal(view.roleOptions[0].attributes.get("aria-pressed"), "false");
  view.roleOptions[2].events.click();
  assert.equal(view.role(), "verifier");
  assert.match(view.roleHelp.textContent, /no verifica la identidad/);
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
  assert.equal(view.page(), "home");
  assert.deepEqual(view.selected(), ["home"]);
  assert.deepEqual(view.desktopSelected(), ["#main-content"]);
  view.tab("publish").events.click();
  assert.deepEqual(view.selected(), ["publish"]);
  assert.deepEqual(view.desktopSelected(), []);
  view.window.location.hash = "#own-section";
  view.listeners.hashchange();
  assert.equal(view.page(), "own");
  assert.deepEqual(view.selected(), ["account"]);
  assert.deepEqual(view.desktopSelected(), ["#own-section"]);
});

test("No se cambia la pestaña activa mientras el diálogo de cuenta está abierto", () => {
  const view = loadNavigation();
  view.tab("account").events.click();
  view.window.location.hash = "#search-section";
  view.listeners.hashchange();
  assert.deepEqual(view.selected(), ["account"]);
});
