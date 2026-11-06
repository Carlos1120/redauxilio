"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(
  path.join(__dirname, "../../main/resources/static/account-restrictions.js"),
  "utf8",
);
const flush = () => new Promise((resolve) => setImmediate(resolve));

class Element {
  constructor(id = "") {
    this.id = id;
    this.value = "";
    this.textContent = "";
    this.children = [];
    this.listeners = {};
    this.attributes = {};
    this.disabled = false;
    Object.defineProperty(this, "innerHTML", {
      set() {
        throw new Error("El contenido privado debe renderizarse como texto");
      },
    });
  }
  append(...items) {
    this.children.push(...items);
  }
  replaceChildren(...items) {
    this.children = items;
  }
  addEventListener(event, callback) {
    this.listeners[event] = callback;
  }
  setAttribute(name, value) {
    this.attributes[name] = value;
  }
}

function setup(fetchImpl) {
  const ids = [
    "account-restriction-form",
    "account-restriction-email",
    "account-restriction-action",
    "account-restriction-reason",
    "account-restriction-message",
    "account-restriction-result",
    "account-restriction-submit",
    "account-restriction-inspect",
    "operator-logout",
  ];
  const nodes = new Map(ids.map((id) => [id, new Element(id)]));
  nodes.get("account-restriction-email").value = "persona@example.invalid";
  nodes.get("account-restriction-action").value = "BLOCK";
  nodes.get("account-restriction-reason").value = "Motivo <ficticio>";
  const windowListeners = new Map();
  const documentListeners = new Map();
  const requests = [];
  const document = {
    hidden: false,
    getElementById: (id) => nodes.get(id) ?? null,
    createElement: (tag) => {
      const element = new Element();
      element.tagName = tag;
      return element;
    },
    addEventListener: (event, listener) => documentListeners.set(event, listener),
  };
  const context = {
    document,
    window: { addEventListener: (event, listener) => windowListeners.set(event, listener) },
    navigator: { onLine: true },
    fetch: async (url, options = {}) => {
      requests.push({ url, options });
      return fetchImpl(url, options);
    },
    URL,
    encodeURIComponent,
    Date,
  };
  vm.runInNewContext(source, context);
  return { nodes, requests, windowListeners, documentListeners, context, document };
}

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    redirected: false,
    json: async () => body,
  };
}

function account(overrides = {}) {
  return {
    email: "persona@example.invalid",
    displayName: "<img src=x>",
    blocked: true,
    blockedAt: "2026-11-04T12:00:00Z",
    currentReason: "Motivo <ficticio>",
    history: [
      {
        action: "BLOCK",
        reason: "Motivo <ficticio>",
        id: 10,
        actorId: 42,
        actorDisplayName: "Operador",
        changedAt: "2026-11-04T12:00:00Z",
      },
    ],
    ...overrides,
  };
}

function treeText(node) {
  return `${node.textContent} ${node.children.map(treeText).join(" ")}`;
}

function findNode(node, predicate) {
  if (predicate(node)) return node;
  for (const child of node.children) {
    const match = findNode(child, predicate);
    if (match) return match;
  }
  return null;
}

test("consulta carga estado e historial sin almacenamiento y como texto", async () => {
  const ui = setup(async (url, options) => {
    assert.equal(options.cache, "no-store");
    return response(account());
  });
  await ui.nodes.get("account-restriction-inspect").listeners.click();
  await flush();
  assert.match(ui.requests[0].url, /\/api\/operator\/accounts\/restriction\?email=/);
  const rendered = treeText(ui.nodes.get("account-restriction-result"));
  assert.match(rendered, /<img src=x>/);
  assert.match(rendered, /Motivo <ficticio>/);
  assert.equal(ui.nodes.get("account-restriction-result").children.length, 1);
});

test("envía acción solo con CSRF y confirma el estado del servidor", async () => {
  const ui = setup(async (url, options) => {
    if (url === "/csrf") return response({ token: "token-ficticio" });
    return response(account());
  });
  await ui.nodes.get("account-restriction-form").listeners.submit({
    preventDefault() {},
  });
  await flush();
  const request = ui.requests[1];
  assert.equal(request.options.method, "POST");
  assert.equal(request.options.cache, "no-store");
  assert.equal(request.options.headers["X-CSRF-TOKEN"], "token-ficticio");
  assert.equal(JSON.parse(request.options.body).reason, "Motivo <ficticio>");
  assert.match(ui.nodes.get("account-restriction-message").textContent, /auditada/);
});

test("permite recorrer páginas anteriores con cursor y no-store", async () => {
  const ui = setup(async (url, options) => {
    assert.equal(options.cache, "no-store");
    if (url.includes("/history?"))
      return response({
        events: [
          {
            id: 9,
            action: "UNBLOCK",
            reason: "Decisión anterior",
            actorId: 42,
            actorDisplayName: "Operador",
            changedAt: "2026-11-03T12:00:00Z",
          },
        ],
        hasOlder: false,
      });
    return response(account({ historyHasOlder: true }));
  });
  await ui.nodes.get("account-restriction-inspect").listeners.click();
  await flush();
  const button = findNode(
    ui.nodes.get("account-restriction-result"),
    (node) => node.tagName === "button",
  );
  assert.ok(button);
  await button.listeners.click();
  await flush();
  assert.match(ui.requests[1].url, /beforeId=10/);
  assert.match(treeText(ui.nodes.get("account-restriction-result")), /Decisión anterior/);
  assert.equal(button.hidden, true);
});

test("borra estado privado al perder conexión y al ocultar la página", async () => {
  const ui = setup(async () => response(account()));
  await ui.nodes.get("account-restriction-inspect").listeners.click();
  await flush();
  assert.equal(ui.nodes.get("account-restriction-result").children.length, 1);
  ui.windowListeners.get("offline")();
  assert.equal(ui.nodes.get("account-restriction-result").children.length, 0);
  assert.equal(ui.nodes.get("account-restriction-email").value, "");

  ui.nodes.get("account-restriction-email").value = "persona@example.invalid";
  await ui.nodes.get("account-restriction-inspect").listeners.click();
  await flush();
  ui.document.hidden = true;
  ui.documentListeners.get("visibilitychange")();
  assert.equal(ui.nodes.get("account-restriction-result").children.length, 0);
  assert.equal(ui.nodes.get("account-restriction-email").value, "");
});
