"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function node(tag) {
  return {
    tag,
    children: [],
    dataset: {},
    listeners: {},
    files: [],
    textContent: "",
    append(...items) {
      this.children.push(...items);
    },
    replaceChildren(...items) {
      this.children = items;
    },
    setAttribute(name, value) {
      this[name] = value;
    },
    getAttribute(name) {
      return this[name] ?? null;
    },
    addEventListener(name, listener) {
      this.listeners[name] = listener;
    },
    reset() {},
    click() {
      this.clicked = true;
    },
  };
}

function find(root, predicate) {
  if (predicate(root)) return root;
  for (const child of root.children || []) {
    const result = find(child, predicate);
    if (result) return result;
  }
  return null;
}

function harness(authenticated = false) {
  const requests = [];
  const status = { dataset: { authenticated: String(authenticated) }, click() {} };
  const context = {
    document: {
      createElement: node,
      getElementById: (id) => (id === "identity-open" ? status : null),
    },
    FormData,
    Blob,
    Date,
    Number,
    JSON,
    fetch: async (url, options = {}) => {
      requests.push({ url, options });
      if (url === "/csrf") return { ok: true, json: async () => ({ token: "fresh" }) };
      if (options.method === "POST") return { ok: true, json: async () => ({}) };
      return {
        ok: true,
        json: async () => [
          {
            id: 7,
            observedAt: "2026-11-03T11:30:00Z",
            location: "Parque público",
            latitude: 4.15,
            longitude: -73.63,
            description: "Observación ficticia",
            evidence: [],
          },
        ],
      };
    },
  };
  context.window = context;
  vm.runInNewContext(
    fs.readFileSync(
      path.join(__dirname, "../../main/resources/static/missing-person-sightings.js"),
      "utf8",
    ),
    context,
  );
  const container = node("main");
  context.window.RedAuxilioSightings.render(
    { id: 1000010, category: "MISSING_PERSON", operationalStatus: "DESAPARECIDA", closed: false },
    container,
  );
  return { context, container, requests, status };
}

test("historial de avistamientos es texto público y no revela la cuenta autora", async () => {
  const ui = harness();
  await new Promise(setImmediate);
  const content = JSON.stringify(ui.container.children);
  assert.match(content, /Observación ficticia/);
  assert.match(content, /Parque público/);
  assert.doesNotMatch(content, /@example/);
  assert.equal(ui.requests[0].options.cache, "no-store");
  const login = find(ui.container, (child) => child.tag === "button");
  assert.match(login.textContent, /Inicia sesión/);
});

test("ciudadano autenticado envía formulario con CSRF y espera respuesta real", async () => {
  const ui = harness(true);
  await new Promise(setImmediate);
  const form = find(ui.container, (child) => child.tag === "form");
  assert.ok(form);
  const time = find(form, (child) => child.id?.startsWith("sighting-time-"));
  const location = find(form, (child) => child.id?.startsWith("sighting-location-"));
  const latitude = find(form, (child) => child.id?.startsWith("sighting-latitude-"));
  const longitude = find(form, (child) => child.id?.startsWith("sighting-longitude-"));
  const description = find(form, (child) => child.id?.startsWith("sighting-description-"));
  time.value = "2026-11-03T11:30";
  location.value = "Parque público";
  latitude.value = "4.15";
  longitude.value = "-73.63";
  description.value = "Observación ficticia";
  await form.listeners.submit({ preventDefault() {} });
  const post = ui.requests.find((request) => request.options.method === "POST");
  assert.equal(post.options.headers["X-CSRF-TOKEN"], "fresh");
  assert.equal(post.options.cache, "no-store");
  assert.equal(post.options.body.get("sighting").type, "application/json");
  const payload = JSON.parse(await post.options.body.get("sighting").text());
  assert.deepEqual(payload, {
    observedAt: new Date(time.value).toISOString(),
    location: "Parque público",
    latitude: 4.15,
    longitude: -73.63,
    description: "Observación ficticia",
  });
  assert.match(
    find(form, (child) => child.getAttribute?.("role") === "status").textContent,
    /registrado/,
  );
});

test("caso encontrado no ofrece un formulario para nuevos avistamientos", () => {
  const ui = harness(true);
  const second = node("main");
  ui.context.window.RedAuxilioSightings.render(
    { id: 1000011, category: "MISSING_PERSON", operationalStatus: "ENCONTRADA", closed: false },
    second,
  );
  assert.equal(
    find(second, (child) => child.tag === "form"),
    null,
  );
});
