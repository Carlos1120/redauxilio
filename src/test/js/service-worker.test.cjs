"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function loadWorker(fetchImpl = async () => new Response("contenido de prueba", { status: 200 })) {
  const listeners = new Map();
  const self = {
    location: { origin: "http://localhost:8081" },
    clients: { claim: async () => {} },
    addEventListener: (type, listener) => listeners.set(type, listener),
  };
  const entries = new Map();
  const precached = [];
  const cache = {
    addAll: async (paths) => precached.push(...paths),
    put: async (request, response) =>
      entries.set(typeof request === "string" ? request : request.url, response.clone()),
    match: async (request) =>
      entries.get(typeof request === "string" ? request : request.url)?.clone(),
  };
  const context = {
    URL,
    Headers,
    Response,
    Promise,
    Date,
    self,
    caches: {
      open: async () => cache,
      keys: async () => [],
      delete: async () => true,
    },
    fetch: fetchImpl,
  };
  const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/sw.js"), "utf8");
  vm.runInNewContext(source, context, { filename: "sw.js" });
  return { listeners, entries, precached };
}

function fetchEvent(worker, url, method = "GET") {
  let response;
  const event = {
    request: { method, url, mode: "navigate" },
    respondWith: (promise) => {
      response = promise;
    },
  };
  worker.listeners.get("fetch")(event);
  return response;
}

test("la consulta pública sin parámetros queda bajo la estrategia de caché", async () => {
  const worker = loadWorker();
  const response = await fetchEvent(worker, "http://localhost:8081/");

  assert.equal(response.status, 200);
  assert.equal(await response.text(), "contenido de prueba");
});

test("el shell precarga el script que mantiene el indicador de sesión y conexión", async () => {
  const worker = loadWorker();
  let installation;
  worker.listeners.get("install")({ waitUntil: (promise) => (installation = promise) });
  await installation;

  assert.ok(worker.precached.includes("/identity-status.js"));
});

test("el service worker no intercepta pantallas de acceso ni escrituras", () => {
  const worker = loadWorker();
  assert.equal(fetchEvent(worker, "http://localhost:8081/?access=register"), undefined);
  assert.equal(fetchEvent(worker, "http://localhost:8081/?access=login"), undefined);
  assert.equal(fetchEvent(worker, "http://localhost:8081/register"), undefined);
  assert.equal(fetchEvent(worker, "http://localhost:8081/login", "POST"), undefined);
  assert.equal(fetchEvent(worker, "http://localhost:8081/logout", "POST"), undefined);
  assert.equal(fetchEvent(worker, "http://localhost:8081/csrf"), undefined);
});

test("la copia offline del shell no conserva tokens CSRF de la sesión anterior", async () => {
  let isOnline = true;
  const worker = loadWorker(async () => {
    if (!isOnline) throw new Error("sin conexión");
    return new Response(
      '<button data-authenticated="true"></button><form method="post"><input type="hidden" name="_csrf" value="token-antiguo"></form>',
      { status: 200, headers: { "Content-Type": "text/html", "Content-Length": "101" } },
    );
  });

  const networkResponse = await fetchEvent(worker, "http://localhost:8081/");
  assert.match(await networkResponse.text(), /token-antiguo/);
  const cached = worker.entries.get("http://localhost:8081/").clone();
  const offlineHtml = await cached.text();
  assert.doesNotMatch(offlineHtml, /token-antiguo/);
  assert.match(offlineHtml, /data-authenticated="false"/);
  assert.match(offlineHtml, /name="_csrf" value=""/);
  assert.equal(cached.headers.get("content-length"), null);

  isOnline = false;
  const offlineResponse = await fetchEvent(worker, "http://localhost:8081/");
  assert.match(await offlineResponse.text(), /name="_csrf" value=""/);
});

test("la precarga inicial también elimina los tokens CSRF antes de guardar el shell", async () => {
  const worker = loadWorker(
    async () =>
      new Response('<input type="hidden" name="_csrf" value="token-de-instalación">', {
        status: 200,
        headers: { "Content-Type": "text/html" },
      }),
  );
  let installation;
  worker.listeners.get("install")({ waitUntil: (promise) => (installation = promise) });
  await installation;

  const cached = await worker.entries.get("http://localhost:8081/").text();
  assert.doesNotMatch(cached, /token-de-instalación/);
  assert.match(cached, /name="_csrf" value=""/);
});

test("flujo offline → reconexión renueva CSRF antes de permitir login", async () => {
  let isOnline = true;
  const worker = loadWorker(async () => {
    if (!isOnline) throw new Error("sin conexión");
    return new Response(
      '<form action="/login" method="post"><input type="hidden" name="_csrf" value="sesion-anterior"></form>',
      { status: 200, headers: { "Content-Type": "text/html" } },
    );
  });
  await fetchEvent(worker, "http://localhost:8081/");
  isOnline = false;
  const offlineResponse = await fetchEvent(worker, "http://localhost:8081/");
  const offlineHtml = await offlineResponse.text();
  const tokenValue = offlineHtml.match(/name="_csrf" value="([^"]*)"/)?.[1];
  assert.equal(tokenValue, "");

  const submitListeners = new Map();
  const tokenField = { value: tokenValue };
  let submittedToken;
  const form = {
    dataset: {},
    addEventListener: (type, listener) => submitListeners.set(type, listener),
    querySelector: () => tokenField,
    requestSubmit: () => {
      let prevented = false;
      submitListeners.get("submit")({ preventDefault: () => (prevented = true) });
      if (!prevented) submittedToken = tokenField.value;
    },
  };
  const message = { textContent: "", hidden: true };
  const context = {
    document: {
      querySelectorAll: () => [form],
      getElementById: () => message,
    },
    navigator: {
      get onLine() {
        return isOnline;
      },
    },
    fetch: async (url, options) => {
      assert.equal(url, "/csrf");
      assert.equal(options.cache, "no-store");
      if (!isOnline) throw new Error("sin conexión");
      return {
        ok: true,
        json: async () => ({ parameterName: "_csrf", token: "token-fresco" }),
      };
    },
  };
  const formScript = fs.readFileSync(
    path.join(__dirname, "../../main/resources/static/identity-forms.js"),
    "utf8",
  );
  vm.runInNewContext(formScript, context, { filename: "identity-forms.js" });

  await submitListeners.get("submit")({ preventDefault: () => {} });
  assert.equal(submittedToken, undefined);
  assert.match(message.textContent, /Conéctate a Internet/);

  isOnline = true;
  await submitListeners.get("submit")({ preventDefault: () => {} });
  assert.equal(submittedToken, "token-fresco");
});
