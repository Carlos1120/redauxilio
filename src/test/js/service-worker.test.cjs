"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function loadWorker() {
  const listeners = new Map();
  const self = {
    location: { origin: "http://localhost:8081" },
    clients: { claim: async () => {} },
    addEventListener: (type, listener) => listeners.set(type, listener),
  };
  const context = {
    URL,
    Headers,
    Response,
    Promise,
    Date,
    self,
    caches: {
      open: async () => ({ addAll: async () => {}, put: async () => {}, match: async () => null }),
      keys: async () => [],
      delete: async () => true,
    },
    fetch: async () => new Response("contenido de prueba", { status: 200 }),
  };
  const source = fs.readFileSync(
    path.join(__dirname, "../../main/resources/static/sw.js"),
    "utf8",
  );
  vm.runInNewContext(source, context, { filename: "sw.js" });
  return listeners;
}

function fetchEvent(url, method = "GET") {
  let response;
  const event = {
    request: { method, url },
    respondWith: (promise) => {
      response = promise;
    },
  };
  loadWorker().get("fetch")(event);
  return response;
}

test("la consulta pública sin parámetros queda bajo la estrategia de caché", async () => {
  const response = await fetchEvent("http://localhost:8081/");

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "contenido de prueba");
});

test("el service worker no intercepta pantallas de acceso ni escrituras", () => {
  assert.equal(fetchEvent("http://localhost:8081/?access=register"), undefined);
  assert.equal(fetchEvent("http://localhost:8081/?access=login"), undefined);
  assert.equal(fetchEvent("http://localhost:8081/register"), undefined);
  assert.equal(fetchEvent("http://localhost:8081/login", "POST"), undefined);
  assert.equal(fetchEvent("http://localhost:8081/logout", "POST"), undefined);
});
