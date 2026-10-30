"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(
  path.join(__dirname, "../../main/resources/static/moderation.js"),
  "utf8",
);
const flush = () => new Promise((resolve) => setImmediate(resolve));
function response(data, status = 200, redirected = false, reference = null) {
  return {
    ok: status === 200,
    status,
    redirected,
    headers: { get: () => reference },
    json: async () => data,
  };
}
function publication(id = 1000000) {
  return {
    id,
    title: "Caso ficticio",
    description: "Contexto",
    location: "Zona ficticia",
    latitude: 4.15,
    longitude: -73.63,
    reviewStatus: "EN_REVISION",
    confidenceLevel: "CONFIRMADA",
    operationalStatus: "DISPONIBLE",
    closed: false,
    createdAt: "2026-10-29",
    updatedAt: "2026-10-29",
  };
}
function page(offset = 0, hasMore = false) {
  return {
    items: [{ publication: publication(), visible: false, activeReports: 3, totalReports: 4 }],
    offset,
    limit: 20,
    hasMore,
  };
}
function dump(node) {
  return `${node.textContent} ${node.children.map(dump).join(" ")}`;
}
/** DOM mínimo que prohíbe innerHTML y almacenamiento: comprueba el JS real, no sustituye revisión visual. */
async function setup(implementation = async () => response(page())) {
  const nodes = new Map(),
    listeners = new Map(),
    requests = [];
  function element(id) {
    if (nodes.has(id)) return nodes.get(id);
    const node = {
      textContent: "",
      children: [],
      disabled: false,
      listeners: {},
      append(...items) {
        this.children.push(...items);
      },
      replaceChildren(...items) {
        this.children = items;
      },
      addEventListener(event, fn) {
        this.listeners[event] = fn;
      },
      setAttribute() {},
      focus() {
        this.focused = true;
      },
    };
    Object.defineProperty(node, "innerHTML", {
      set() {
        throw new Error("HTML privado inseguro");
      },
    });
    nodes.set(id, node);
    return node;
  }
  const document = {
    hidden: false,
    getElementById: element,
    createElement: (tagName) => {
      const node = element(Symbol());
      node.tagName = tagName;
      return node;
    },
    addEventListener: (event, fn) => listeners.set(`document:${event}`, fn),
  };
  const navigator = { onLine: true };
  const context = {
    document,
    navigator,
    window: { addEventListener: (event, fn) => listeners.set(event, fn) },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return implementation(url, options);
    },
    localStorage: {
      setItem() {
        throw new Error("No guardar datos privados");
      },
    },
    indexedDB: {
      open() {
        throw new Error("No guardar datos privados");
      },
    },
  };
  vm.runInNewContext(source, context);
  await flush();
  return { element, listeners, requests, navigator, document };
}

test("cola privada muestra dimensiones y contexto como texto, sin escribir ni cachear", async () => {
  const detail = {
    context: { publication: publication(), visible: true },
    reports: [{ id: 50, reportedAt: "hoy", active: true, reason: "<script>privado</script>" }],
    history: [
      {
        id: 40,
        changedAt: "hoy",
        action: "REVIEW",
        actorId: 9,
        reason: "Motivo",
        beforeSnapshot: "{}",
        afterSnapshot: "<img onerror=malicioso>",
      },
    ],
    moreReports: true,
    moreHistory: true,
    decisions: [],
  };
  const ui = await setup(async (url) => response(url.includes("/1000000") ? detail : page()));
  assert.match(dump(ui.element("moderation-list")), /EN_REVISION.*CONFIRMADA/);
  assert.match(dump(ui.element("moderation-list")), /Oculta.*3 denuncias activas/);
  const card = ui.element("moderation-list").children[0];
  await card.children.at(-1).listeners.click();
  assert.match(dump(ui.element("moderation-detail")), /<script>privado<\/script>/);
  assert.match(dump(ui.element("moderation-detail")), /<img onerror=malicioso>/);
  assert.match(dump(ui.element("moderation-detail")), /Cargar 50 entradas anteriores/);
  assert.equal(ui.element("moderation-detail").focused, true);
  assert.ok(
    ui.requests.every(
      (r) =>
        r.options.cache === "no-store" &&
        r.options.credentials === "same-origin" &&
        !r.options.method,
    ),
  );
});

test("carga páginas anteriores de cada historial privado con cursor y sin HTML", async () => {
  const detail = {
    context: { publication: publication(), visible: true },
    reports: [{ id: 300, reportedAt: "hoy", active: false, reason: "Denuncia reciente" }],
    history: [
      {
        id: 200,
        changedAt: "hoy",
        action: "EDIT",
        actorId: 9,
        reason: "Cambio reciente",
        beforeSnapshot: "{}",
        afterSnapshot: "{}",
      },
    ],
    decisions: [
      {
        id: 100,
        actedAt: "hoy",
        action: "HIDE",
        actorId: 9,
        reason: "Decisión reciente",
        beforeSnapshot: "{}",
        afterSnapshot: "{}",
      },
    ],
    moreReports: true,
    moreHistory: true,
    moreDecisions: true,
  };
  const ui = await setup(async (url) => {
    if (url.includes("/history/")) {
      const stream = url.split("/history/")[1].split("?")[0];
      return response({
        stream,
        items: [
          {
            id: 1,
            reportedAt: "ayer",
            active: false,
            reason: "<script>entrada privada anterior</script>",
            changedAt: "ayer",
            actedAt: "ayer",
            action: "KEEP",
            actorId: 9,
            beforeSnapshot: "{}",
            afterSnapshot: "{}",
          },
        ],
        hasMore: false,
      });
    }
    return response(url.includes("/1000000") ? detail : page());
  });
  await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  const buttons = [];
  const collect = (node) => {
    if (node.tagName === "button" && node.textContent === "Cargar 50 entradas anteriores")
      buttons.push(node);
    for (const child of node.children) collect(child);
  };
  collect(ui.element("moderation-detail"));
  assert.equal(buttons.length, 3);
  for (const loadButton of buttons) await loadButton.listeners.click();
  assert.ok(ui.requests.some((request) => request.url.includes("/history/reports?beforeId=300")));
  assert.ok(ui.requests.some((request) => request.url.includes("/history/changes?beforeId=200")));
  assert.ok(ui.requests.some((request) => request.url.includes("/history/decisions?beforeId=100")));
  assert.match(dump(ui.element("moderation-detail")), /<script>entrada privada anterior<\/script>/);
  assert.ok(buttons.every((loadButton) => loadButton.disabled));
  assert.ok(ui.requests.every((request) => request.options.cache === "no-store"));
});

test("una decisión usa motivo obligatorio, CSRF y espera confirmación del servidor", async () => {
  const detail = {
    context: { publication: publication(), visible: true },
    reports: [],
    history: [],
    decisions: [],
  };
  const ui = await setup(async (url, options) => {
    if (url === "/csrf")
      return response({ parameterName: "_csrf", headerName: "X-CSRF-TOKEN", token: "csrf-demo" });
    if (options.method === "POST") return response({ action: "HIDE" });
    if (url.includes("/1000000")) return response(detail);
    return response(page());
  });
  const find = (node, predicate) => {
    if (predicate(node)) return node;
    for (const child of node.children) {
      const found = find(child, predicate);
      if (found) return found;
    }
    return null;
  };
  await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  const reason = find(ui.element("moderation-detail"), (node) => node.tagName === "textarea");
  // The minimal DOM records the generated element type for locating the required field.
  assert.ok(reason);
  reason.value = "Motivo de prueba";
  const hide = find(
    ui.element("moderation-detail"),
    (node) => node.textContent === "Ocultar publicación",
  );
  await hide.listeners.click();
  const post = ui.requests.find((request) => request.options.method === "POST");
  assert.equal(post.options.headers["X-CSRF-TOKEN"], "csrf-demo");
  assert.equal(JSON.parse(post.options.body).reason, "Motivo de prueba");
  assert.match(ui.element("moderation-message").textContent, /Decisión registrada/);
});

test("logout cancela una decisión que aún renueva CSRF o espera respuesta", async () => {
  const detail = {
    context: { publication: publication(), visible: true },
    reports: [],
    history: [],
  };
  const find = (node, predicate) => {
    if (predicate(node)) return node;
    for (const child of node.children) {
      const found = find(child, predicate);
      if (found) return found;
    }
    return null;
  };
  let finishCsrf;
  const waitingForCsrf = await setup(async (url) => {
    if (url === "/csrf") return new Promise((resolve) => (finishCsrf = resolve));
    if (url.includes("/1000000")) return response(detail);
    return response(page());
  });
  await waitingForCsrf.element("moderation-list").children[0].children.at(-1).listeners.click();
  const reason = find(
    waitingForCsrf.element("moderation-detail"),
    (node) => node.tagName === "textarea",
  );
  reason.value = "Motivo";
  const hide = find(
    waitingForCsrf.element("moderation-detail"),
    (node) => node.textContent === "Ocultar publicación",
  );
  const csrfAction = hide.listeners.click();
  await flush();
  waitingForCsrf.element("operator-logout").listeners.submit();
  finishCsrf(response({ parameterName: "_csrf", headerName: "X-CSRF-TOKEN", token: "csrf" }));
  await csrfAction;
  assert.equal(
    waitingForCsrf.requests.some((request) => request.options.method === "POST"),
    false,
  );
  assert.equal(waitingForCsrf.element("moderation-detail").children.length, 0);

  let finishPost;
  const waitingForPost = await setup(async (url, options) => {
    if (url === "/csrf")
      return response({ parameterName: "_csrf", headerName: "X-CSRF-TOKEN", token: "csrf" });
    if (options.method === "POST") return new Promise((resolve) => (finishPost = resolve));
    if (url.includes("/1000000")) return response(detail);
    return response(page());
  });
  await waitingForPost.element("moderation-list").children[0].children.at(-1).listeners.click();
  const postReason = find(
    waitingForPost.element("moderation-detail"),
    (node) => node.tagName === "textarea",
  );
  postReason.value = "Motivo";
  const postButton = find(
    waitingForPost.element("moderation-detail"),
    (node) => node.textContent === "Ocultar publicación",
  );
  const pendingAction = postButton.listeners.click();
  await flush();
  waitingForPost.element("operator-logout").listeners.submit();
  finishPost(response({ action: "HIDE" }));
  await pendingAction;
  assert.equal(waitingForPost.element("moderation-detail").children.length, 0);
  assert.doesNotMatch(
    waitingForPost.element("moderation-message").textContent,
    /Decisión registrada/,
  );
});

test("sesión vencida, permiso retirado o redirección limpian datos sin anunciar éxito", async () => {
  for (const [status, redirected] of [
    [401, false],
    [403, false],
    [200, true],
  ]) {
    const ui = await setup(async () => response({}, status, redirected));
    assert.equal(ui.element("moderation-list").children.length, 0);
    assert.match(ui.element("moderation-message").textContent, /cuenta autorizada/);
  }
});

test("offline invalida una respuesta tardía y reconectar consulta de nuevo", async () => {
  let finish;
  let first = true;
  const ui = await setup(() => {
    if (first) {
      first = false;
      return new Promise((resolve) => {
        finish = resolve;
      });
    }
    return response(page());
  });
  ui.navigator.onLine = false;
  ui.listeners.get("offline")();
  finish(response(page()));
  await flush();
  assert.equal(ui.element("moderation-list").children.length, 0);
  assert.match(ui.element("moderation-message").textContent, /Sin conexión/);
  ui.navigator.onLine = true;
  await ui.listeners.get("online")();
  assert.equal(ui.element("moderation-list").children.length, 1);
});

test("logout, identidad, página oculta y bfcache no conservan contexto privado", async () => {
  const ui = await setup();
  ui.element("operator-logout").listeners.submit();
  assert.equal(ui.element("moderation-list").children.length, 0);
  await ui.element("moderation-refresh").listeners.click();
  ui.listeners.get("redauxilio:identity-change")();
  assert.equal(ui.element("moderation-list").children.length, 0);
  ui.document.hidden = true;
  ui.listeners.get("document:visibilitychange")();
  ui.document.hidden = false;
  await ui.listeners.get("document:visibilitychange")();
  await flush();
  assert.equal(ui.element("moderation-list").children.length, 1);
  ui.listeners.get("pagehide")();
  assert.equal(ui.element("moderation-list").children.length, 0);
  ui.listeners.get("pageshow")({ persisted: true });
  await flush();
  assert.equal(ui.element("moderation-list").children.length, 1);
});

test("contexto tardío de una sesión anterior no repuebla pantalla", async () => {
  let finish;
  const ui = await setup((url) =>
    url.endsWith("/1000000")
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : response(page()),
  );
  ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  ui.listeners.get("redauxilio:identity-change")();
  finish(response({ context: { publication: publication() }, reports: [], history: [] }));
  await flush();
  assert.equal(ui.element("moderation-detail").children.length, 0);
});

test("paginación solicita offset acotado y mensajes de cola vacía", async () => {
  const ui = await setup(async (url) => {
    const offset = Number(new URL(url, "http://localhost").searchParams.get("offset"));
    return response(offset ? { items: [], offset: 20, hasMore: false } : page(0, true));
  });
  assert.equal(ui.element("moderation-next").disabled, false);
  await ui.element("moderation-next").listeners.click();
  assert.match(ui.requests.at(-1).url, /offset=20/);
  assert.match(ui.element("moderation-message").textContent, /No hay casos/);
  await ui.element("moderation-previous").listeners.click();
  assert.match(ui.requests.at(-1).url, /offset=0/);
});

test("auditoría de permisos privada y errores con referencia segura", async () => {
  const ui = await setup(async (url) =>
    response(
      url.includes("permissions/history")
        ? [
            {
              changedAt: "hoy",
              actor: "Prueba",
              accountId: 4,
              action: "GRANT",
              beforeGranted: false,
              afterGranted: true,
              reason: "Motivo privado",
            },
          ]
        : page(),
    ),
  );
  await ui.element("permission-history").listeners.click();
  assert.match(dump(ui.element("permission-events")), /Prueba.*GRANT.*Motivo privado/);
  ui.navigator.onLine = false;
  await ui.element("moderation-refresh").listeners.click();
  assert.equal(ui.element("permission-events").children.length, 0);
  const error = await setup(async () =>
    response({}, 500, false, "12345678-1234-1234-1234-123456789012"),
  );
  assert.match(error.element("moderation-message").textContent, /Referencia: 12345678/);
  assert.equal(error.element("moderation-list").children.length, 0);
});

test("script sin página operativa no consulta ni altera la pantalla pública", () => {
  vm.runInNewContext(source, { document: { getElementById: () => null } });
});
