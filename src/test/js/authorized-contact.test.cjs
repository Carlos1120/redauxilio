"use strict";
const test = require("node:test"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path"),
  vm = require("node:vm");
const tick = () => new Promise((r) => setImmediate(r));
const response = (data, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => data,
});
/** DOM mínimo del módulo real: cualquier intento de storage hace fallar la prueba. */
function harness(handler = async () => response({ value: "adulto@example.invalid" })) {
  const nodes = new Map(),
    events = new Map(),
    requests = [];
  const make = () => ({
    value: "",
    checked: false,
    hidden: false,
    dataset: {},
    children: [],
    listeners: {},
    textContent: "",
    addEventListener(n, fn) {
      this.listeners[n] = fn;
    },
    append(...n) {
      this.children.push(...n);
    },
    setAttribute() {},
    querySelectorAll() {
      return [
        find("authorized-contact-value"),
        find("authorized-contact-consent"),
        find("authorized-contact-adult"),
        find("authorized-contact-save"),
        find("authorized-contact-withdraw"),
      ];
    },
    reportValidity() {
      return (
        !!find("authorized-contact-value").value &&
        find("authorized-contact-consent").checked &&
        find("authorized-contact-adult").checked
      );
    },
  });
  const find = (id) => {
    if (!nodes.has(id)) nodes.set(id, Object.assign(make(), { id }));
    return nodes.get(id);
  };
  const window = {
    addEventListener(n, fn) {
      events.set(n, fn);
    },
    dispatchEvent(e) {
      events.get(e.type)?.(e);
    },
  };
  const document = {
    getElementById: find,
    createElement: make,
    addEventListener(n, fn) {
      events.set(n, fn);
    },
    hidden: false,
  };
  const navigator = { onLine: true };
  const storage = new Proxy(
    {},
    {
      get() {
        throw new Error("El contacto no puede almacenarse");
      },
    },
  );
  vm.runInNewContext(
    fs.readFileSync(
      path.join(__dirname, "../../main/resources/static/authorized-contact.js"),
      "utf8",
    ),
    {
      window,
      document,
      navigator,
      localStorage: storage,
      indexedDB: storage,
      CustomEvent: class {
        constructor(type, init = {}) {
          this.type = type;
          this.detail = init.detail;
        }
      },
      fetch: async (url, options) => {
        requests.push({ url, options });
        return url === "/csrf"
          ? response({ token: "fresh", headerName: "X-CSRF-TOKEN" })
          : handler(url, options);
      },
    },
  );
  const event = async (type, detail) => {
    events.get(type)?.({ detail });
    await tick();
  };
  return { find, window, navigator, document, make, requests, event };
}

test("contacto se consulta no-store bajo demanda y se borra al cerrar o salir", async () => {
  const h = harness(),
    container = h.make();
  h.window.RedAuxilioAuthorizedContact.mount(container, 1000000);
  const section = container.children[0],
    button = section.children[1],
    output = section.children[2];
  assert.equal(h.requests.length, 0);
  await button.listeners.click();
  assert.equal(output.textContent, "adulto@example.invalid");
  assert.equal(h.requests[0].options.cache, "no-store");
  assert.equal(h.requests[0].options.method, "GET");
  h.window.RedAuxilioAuthorizedContact.clear();
  assert.equal(output.textContent, "");
  await button.listeners.click();
  await h.event("offline");
  assert.equal(output.textContent, "");
});

test("respuesta de contacto tardía tras selección/logout nunca repone el valor", async () => {
  let finish;
  const h = harness(() => new Promise((r) => (finish = r))),
    container = h.make();
  h.window.RedAuxilioAuthorizedContact.mount(container, 1000000);
  const section = container.children[0];
  const pending = section.children[1].listeners.click();
  await tick();
  await h.event("redauxilio:identity-change");
  finish(response({ value: "privado@example.invalid" }));
  await pending;
  assert.equal(section.children[2].textContent, "");
});

test("consentimiento afirmativo, CSRF y confirmación real antes de éxito; nunca storage", async () => {
  const h = harness(async () => response(null, 204));
  await h.event("redauxilio:publication-selected", {
    id: 1000000,
    category: "MISSING_PERSON",
    closed: false,
  });
  h.find("authorized-contact-value").value = "adulto@example.invalid";
  h.find("authorized-contact-form").listeners.submit({ preventDefault() {} });
  await tick();
  assert.equal(h.requests.length, 0);
  h.find("authorized-contact-consent").checked = true;
  h.find("authorized-contact-adult").checked = true;
  h.find("authorized-contact-form").listeners.submit({ preventDefault() {} });
  await tick();
  await tick();
  const put = h.requests.find((r) => r.options.method === "PUT");
  assert.ok(put);
  assert.equal(put.options.cache, "no-store");
  assert.equal(put.options.headers["X-CSRF-TOKEN"], "fresh");
  assert.equal(JSON.parse(put.options.body).consent, true);
  assert.match(h.find("authorized-contact-message").textContent, /confirmada por el servidor/);
  assert.equal(h.find("authorized-contact-value").value, "");
});

test("error/sesión vencida limpia dato; retiro permitido en cerrado y oculta panel al logout", async () => {
  const h = harness(async () => response(null, 403));
  await h.event("redauxilio:publication-selected", {
    id: 1000000,
    category: "MISSING_PERSON",
    closed: false,
  });
  h.find("authorized-contact-value").value = "adulto@example.invalid";
  h.find("authorized-contact-consent").checked = h.find("authorized-contact-adult").checked = true;
  h.find("authorized-contact-form").listeners.submit({ preventDefault() {} });
  await tick();
  await tick();
  assert.equal(h.find("authorized-contact-value").value, "");
  assert.equal(h.find("own-contact-panel").hidden, true);
  await h.event("redauxilio:publication-selected", {
    id: 1000000,
    category: "MISSING_PERSON",
    closed: true,
  });
  assert.equal(h.find("authorized-contact-save").disabled, true);
  assert.equal(h.find("authorized-contact-withdraw").disabled, false);
  await h.event("pagehide");
  assert.equal(h.find("own-contact-panel").hidden, true);
});

test("offline no consulta contacto, no falsea éxito y descarte durante CSRF impide escritura", async () => {
  const h = harness(),
    container = h.make();
  h.navigator.onLine = false;
  h.window.RedAuxilioAuthorizedContact.mount(container, 1000000);
  await container.children[0].children[1].listeners.click();
  assert.equal(h.requests.length, 0);
  assert.match(container.children[0].children[2].textContent, /Conéctate/);
});
