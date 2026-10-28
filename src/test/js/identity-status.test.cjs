"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function loadIdentityStatus({
  authenticated,
  online,
  reconnectUrl = "http://localhost/?access=account",
}) {
  const listeners = new Map();
  const attributes = {};
  const trigger = {
    dataset: { authenticated: String(authenticated) },
    setAttribute: (key, value) => (attributes[key] = value),
  };
  const connectionIndicator = { dataset: {} };
  const liveStatus = { textContent: "" };
  const accountName = { textContent: "" };
  const accountEmail = { textContent: "" };
  const accountMessage = { textContent: "", hidden: false };
  const context = {
    document: {
      getElementById: (id) =>
        id === "identity-open"
          ? trigger
          : id === "connection-indicator"
            ? connectionIndicator
            : id === "connection"
              ? liveStatus
              : id === "account-name"
                ? accountName
                : id === "account-email"
                  ? accountEmail
                  : accountMessage,
    },
    navigator: { onLine: online },
    URL,
    CustomEvent: class {
      constructor(type, options) {
        this.type = type;
        this.detail = options.detail;
      }
    },
    fetch: async () => ({
      ok: true,
      url: reconnectUrl,
      json: async () => ({ displayName: "Nombre de prueba", email: "prueba@example.test" }),
    }),
    window: {
      addEventListener: (name, listener) => listeners.set(name, listener),
      dispatchEvent: (event) => listeners.get(event.type)?.(event),
    },
  };
  const source = fs.readFileSync(
    path.join(__dirname, "../../main/resources/static/identity-status.js"),
    "utf8",
  );
  vm.runInNewContext(source, context, { filename: "identity-status.js" });
  return {
    trigger,
    connectionIndicator,
    liveStatus,
    accountName,
    accountEmail,
    accountMessage,
    attributes,
    listeners,
    context,
  };
}

test("el indicador combina sesión y conexión en sus cuatro estados", () => {
  for (const authenticated of [false, true]) {
    for (const online of [false, true]) {
      const status = loadIdentityStatus({ authenticated, online });
      assert.equal(status.connectionIndicator.dataset.online, String(online));
      assert.equal(status.liveStatus.textContent.includes("online"), online);
      assert.equal(status.liveStatus.textContent.includes("Sin conexión"), !online);
      assert.match(
        status.attributes["aria-label"],
        authenticated ? /Sesión iniciada/ : /Sin sesión iniciada/,
      );
    }
  }
});

test("cambia solo el punto de conexión y confirma la sesión al reconectar", async () => {
  const status = loadIdentityStatus({ authenticated: true, online: true });
  status.context.navigator.onLine = false;
  status.listeners.get("offline")();
  assert.equal(status.connectionIndicator.dataset.online, "false");
  assert.match(status.liveStatus.textContent, /Sin conexión/);
  status.context.navigator.onLine = true;
  status.listeners.get("online")();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(status.connectionIndicator.dataset.online, "true");
});

test("al reconectar, no marca como activa una sesión que ya no existe", async () => {
  const status = loadIdentityStatus({
    authenticated: true,
    online: false,
    reconnectUrl: "http://localhost/?access=login",
  });
  status.context.navigator.onLine = true;
  status.listeners.get("online")();
  await new Promise((resolve) => setTimeout(resolve, 0));
  assert.equal(status.trigger.dataset.authenticated, "false");
  assert.equal(status.connectionIndicator.dataset.online, "true");
});

test("cambiar sesión no altera el indicador exclusivo de conexión", () => {
  const status = loadIdentityStatus({ authenticated: false, online: true });
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(true);
  assert.equal(status.connectionIndicator.dataset.online, "true");
  assert.match(status.attributes["aria-label"], /Sesión iniciada/);
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(false);
  assert.equal(status.connectionIndicator.dataset.online, "true");
});

test("carga el nombre y correo del perfil y los limpia al cerrar sesión", async () => {
  const status = loadIdentityStatus({ authenticated: false, online: true });
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(true);
  await new Promise((resolve) => setTimeout(resolve, 0));

  assert.equal(status.accountName.textContent, "Nombre de prueba");
  assert.equal(status.accountEmail.textContent, "prueba@example.test");
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(false);
  assert.equal(status.accountName.textContent, "");
  assert.equal(status.accountEmail.textContent, "");
});

test("los cambios de sesión emiten el evento que invalida datos privados", () => {
  const status = loadIdentityStatus({ authenticated: false, online: true });
  const changes = [];
  status.listeners.set("redauxilio:identity-change", (event) =>
    changes.push(event.detail.authenticated),
  );
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(true);
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(false);
  assert.deepEqual(changes, [true, false]);
});

test("perfil tardío después de logout no recupera nombre ni correo", async () => {
  const status = loadIdentityStatus({ authenticated: false, online: true });
  let release;
  status.context.fetch = () =>
    new Promise((resolve) => {
      release = resolve;
    });
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(true);
  status.context.window.RedAuxilioIdentityStatus.setAuthenticated(false);
  release({
    ok: true,
    json: async () => ({ displayName: "Perfil anterior", email: "privado@example.test" }),
  });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(status.accountName.textContent, "");
  assert.equal(status.accountEmail.textContent, "");
});
