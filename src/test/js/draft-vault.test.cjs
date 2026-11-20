"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");
const { File } = require("node:buffer");
const { TextEncoder, TextDecoder } = require("node:util");

const script = fs.readFileSync(
  path.join(__dirname, "../../main/resources/static/draft-vault.js"),
  "utf8",
);

function environment(storage = new Map(), session = new Map()) {
  const state = { active: false, owner: "101", connected: true };
  const callbacks = new Map();
  const keys = new Map([
    ["101", Buffer.alloc(32, 11).toString("base64url")],
    ["102", Buffer.alloc(32, 22).toString("base64url")],
  ]);
  const localSession = {
    getItem: (key) => session.get(key) ?? null,
    setItem: (key, value) => session.set(key, String(value)),
  };
  function indexedDB() {
    return {
      open() {
        const request = {};
        const database = {
          createObjectStore() {},
          transaction() {
            const tx = {};
            const requestFor = (getValue) => {
              const operation = {};
              setImmediate(() => {
                operation.result = getValue();
                operation.onsuccess?.();
                tx.oncomplete?.();
              });
              return operation;
            };
            tx.objectStore = () => ({
              get: (key) => requestFor(() => storage.get(key)),
              put: (value, key) => requestFor(() => storage.set(key, value)),
              delete: (key) => requestFor(() => storage.delete(key)),
            });
            return tx;
          },
        };
        request.result = database;
        setImmediate(() => {
          request.onupgradeneeded?.();
          request.onsuccess?.();
        });
        return request;
      },
    };
  }
  const db = indexedDB();
  const window = {
    indexedDB: db,
    addEventListener(event, callback) {
      callbacks.set(event, callback);
    },
    dispatchEvent() {},
  };
  const context = {
    window,
    indexedDB: db,
    sessionStorage: localSession,
    crypto: webcrypto,
    TextEncoder,
    TextDecoder,
    File,
    Buffer,
    atob: (data) => Buffer.from(data, "base64").toString("binary"),
    btoa: (data) => Buffer.from(data, "binary").toString("base64"),
    Event: class {
      constructor(type) {
        this.type = type;
      }
    },
    document: {
      getElementById: () => ({
        dataset: { authenticated: String(state.active) },
      }),
    },
    fetch: async () => {
      if (!state.connected) throw new TypeError("Failed to fetch");
      return {
        ok: true,
        json: async () => ({ owner: state.owner, key: keys.get(state.owner) }),
      };
    },
    setImmediate,
  };
  vm.runInNewContext(script, context, { filename: "draft-vault.js" });
  return {
    vault: window.RedAuxilioDraftVault,
    state,
    storage,
    session,
    update() {
      callbacks.get("redauxilio:identity-change")?.();
    },
  };
}

test("borrador visitante queda cifrado y se recupera sin conexión durante la pestaña", async () => {
  const storage = new Map();
  const session = new Map();
  const view = environment(storage, session);
  const draft = { text: "Mensaje de ejemplo", evidence: [], category: "ASSISTANCE" };
  view.state.connected = false;
  await view.vault.save(draft);
  const item = storage.get("visitor");
  assert.equal(item.protected, true);
  assert.equal(item.draft, undefined);
  assert.equal(JSON.stringify(item).includes("Mensaje de ejemplo"), false);
  assert.equal((await view.vault.read()).text, draft.text);

  const reload = environment(storage, session);
  reload.state.connected = false;
  assert.equal((await reload.vault.read()).text, draft.text);
});

test("cifrado de cuentas no expone datos al visitante ni a otra cuenta", async () => {
  const view = environment();
  view.state.active = true;
  view.state.owner = "101";
  const draft = {
    text: "Información reservada de A",
    category: "MISSING_PERSON",
    evidence: [new File(["imagen de prueba"], "prueba.webp", { type: "image/webp" })],
  };
  await view.vault.save(draft);
  const raw = view.storage.get("account:101");
  assert.equal(raw.protected, true);
  assert.equal(JSON.stringify(raw).includes(draft.text), false);
  assert.equal(JSON.stringify(raw).includes("imagen de prueba"), false);
  assert.equal((await view.vault.read()).text, draft.text);

  view.state.active = false;
  view.update();
  assert.equal(await view.vault.read(), null);
  view.state.active = true;
  view.state.owner = "102";
  view.update();
  assert.equal(await view.vault.read(), null);
  await view.vault.save({ text: "B", evidence: [] });
  assert.notEqual(view.storage.get("account:101"), view.storage.get("account:102"));
  view.state.owner = "101";
  view.update();
  const restored = await view.vault.read();
  assert.equal(restored.text, draft.text);
  assert.equal(restored.evidence.length, 1);
  assert.equal(restored.evidence[0].name, "prueba.webp");
});

test("la cuenta autenticada no obtiene su clave por primera vez sin conexión", async () => {
  const view = environment();
  view.state.active = true;
  view.state.connected = false;
  await assert.rejects(view.vault.save({ text: "Prueba" }), /Conéctate/);
  assert.equal(view.storage.size, 0);
});
