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
    category: "ASSISTANCE",
    assistanceSubtype: "SHELTER",
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
      attributes: {},
      setAttribute(name, value) {
        this.attributes[name] = value;
      },
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
  assert.match(dump(ui.element("moderation-list")), /En revisión.*Confirmada por la comunidad/);
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
  await find(
    ui.element("moderation-detail"),
    (node) => node.textContent === "Confirmar decisión",
  ).listeners.click();
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
  await hide.listeners.click();
  const csrfAction = find(
    waitingForCsrf.element("moderation-detail"),
    (node) => node.textContent === "Confirmar decisión",
  ).listeners.click();
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
  await postButton.listeners.click();
  const pendingAction = find(
    waitingForPost.element("moderation-detail"),
    (node) => node.textContent === "Confirmar decisión",
  ).listeners.click();
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

test("operador consulta, concede y audita permiso de verificación con CSRF", async () => {
  const ui = await setup(async (url, options) => {
    if (url === "/csrf") return response({ token: "fresh-token" });
    if (url === "/api/operator/verifier-permissions" && options.method === "POST")
      return response({ granted: true });
    if (url === "/api/operator/verifier-permissions")
      return response([
        {
          accountId: 7,
          email: "entidad@example.test",
          displayName: "Entidad pública",
          grantedAt: "2026-11-02T12:00:00Z",
        },
      ]);
    if (url.startsWith("/api/operator/verifier-permissions/history"))
      return response([
        {
          email: "entidad@example.test",
          actorEmail: "operador@example.test",
          action: "GRANT",
          beforeGranted: false,
          afterGranted: true,
          reason: "Acreditación comprobada",
          changedAt: "2026-11-02T12:00:00Z",
        },
      ]);
    return response(page());
  });
  ui.element("verification-permission-admin").listeners.toggle({ currentTarget: { open: true } });
  await flush();
  assert.match(dump(ui.element("verifier-list")), /Entidad pública.*entidad@example\.test/);
  assert.match(dump(ui.element("verifier-events")), /Acreditación comprobada/);
  ui.element("verifier-email").value = "entidad@example.test";
  ui.element("verifier-action").value = "GRANT";
  ui.element("verifier-reason").value = "Acreditación comprobada";
  await ui.element("verifier-permission-form").listeners.submit({ preventDefault() {} });
  const request = ui.requests.find((item) => item.options.method === "POST");
  assert.equal(request.options.headers["X-CSRF-TOKEN"], "fresh-token");
  assert.deepEqual(JSON.parse(request.options.body), {
    email: "entidad@example.test",
    action: "GRANT",
    reason: "Acreditación comprobada",
  });
  assert.equal(ui.element("verifier-reason").value, "");
});

/** RF-16 comparte el endpoint seguro y nunca anuncia éxito antes de la respuesta. */
test("revisión manual espera servidor, renueva CSRF y refresca cola sin denuncias", async () => {
  let finish;
  const ui = await setup(async (url, options) => {
    if (url === "/csrf") return response({ headerName: "X-CSRF-TOKEN", token: "nuevo" });
    if (options.method === "POST")
      return new Promise((resolve) => {
        finish = resolve;
      });
    if (url.endsWith("/1000000"))
      return response({
        context: { publication: publication(), visible: true },
        reports: [],
        history: [],
        decisions: [
          {
            action: "REVIEW",
            reason: "Revisar contexto",
            actorId: 9,
            actedAt: "hoy",
            beforeSnapshot: "SIN_REVISION",
            afterSnapshot: "EN_REVISION",
          },
        ],
      });
    const result = page();
    result.items[0].activeReports = 0;
    result.items[0].totalReports = 0;
    return response(result);
  });
  ui.element("manual-review-id").value = "1000000";
  ui.element("manual-review-reason").value = "  Revisar contexto  ";
  const submit = () => ui.element("manual-review-form").listeners.submit({ preventDefault() {} });
  const pending = submit();
  await flush();
  assert.match(ui.element("moderation-message").textContent, /Enviando/);
  assert.equal(ui.element("manual-review-submit").disabled, true);
  await submit();
  const writes = ui.requests.filter((r) => r.options.method === "POST");
  assert.equal(writes.length, 1);
  assert.equal(writes[0].url, "/api/operator/publications/1000000/actions");
  assert.equal(writes[0].options.headers["X-CSRF-TOKEN"], "nuevo");
  assert.deepEqual(JSON.parse(writes[0].options.body), {
    action: "REVIEW",
    reason: "Revisar contexto",
  });
  finish(response({ reviewStatus: "EN_REVISION" }));
  await pending;
  assert.match(ui.element("moderation-message").textContent, /enviada a revisión con auditoría/);
  assert.equal(ui.element("manual-review-id").value, "");
  assert.equal(ui.element("manual-review-reason").value, "");
  assert.equal(ui.element("manual-review-submit").disabled, false);
  assert.ok(ui.requests.some((r) => r.url.includes("offset=0")));
  assert.match(dump(ui.element("moderation-list")), /0 denuncias activas \/ 0 totales/);
  assert.match(dump(ui.element("moderation-detail")), /Envío a revisión.*Revisar contexto/);
});

test("revisión manual valida campos, rechazos y desconexión sin falso éxito", async () => {
  const ui = await setup(async (url, options) =>
    response(
      url === "/csrf" ? { headerName: "X-CSRF-TOKEN", token: "t" } : page(),
      options.method === "POST" ? 409 : 200,
    ),
  );
  const submit = () => ui.element("manual-review-form").listeners.submit({ preventDefault() {} });
  for (const [id, reason] of [
    ["0", "Motivo"],
    ["1.5", "Motivo"],
    ["1000000", " "],
    ["1000000", "x".repeat(501)],
  ]) {
    ui.element("manual-review-id").value = id;
    ui.element("manual-review-reason").value = reason;
    await submit();
    assert.match(ui.element("moderation-message").textContent, /válido/);
  }
  assert.equal(ui.requests.filter((r) => r.options.method === "POST").length, 0);
  ui.element("manual-review-id").value = "1000000";
  ui.element("manual-review-reason").value = "Motivo";
  await submit();
  assert.match(ui.element("moderation-message").textContent, /No se aplicó/);
  ui.navigator.onLine = false;
  await submit();
  assert.match(ui.element("moderation-message").textContent, /Sin conexión/);
  assert.equal(ui.requests.filter((r) => r.options.method === "POST").length, 1);
});

test("revisión manual descarta respuesta tardía después de cambiar sesión", async () => {
  let finish;
  const ui = await setup(async (url, options) => {
    if (url === "/csrf") return response({ headerName: "X-CSRF-TOKEN", token: "t" });
    if (options.method === "POST")
      return new Promise((resolve) => {
        finish = resolve;
      });
    return response(page());
  });
  ui.element("manual-review-id").value = "1000000";
  ui.element("manual-review-reason").value = "Motivo privado";
  const pending = ui.element("manual-review-form").listeners.submit({ preventDefault() {} });
  await flush();
  ui.listeners.get("redauxilio:identity-change")();
  finish(response({}));
  await pending;
  assert.doesNotMatch(ui.element("moderation-message").textContent, /enviada a revisión/);
  assert.equal(ui.element("manual-review-reason").value, "");
  assert.equal(ui.element("moderation-list").children.length, 0);
});

test("revisión manual cancelada durante CSRF no escribe; un fallo de red nunca anuncia éxito", async () => {
  let finish;
  const ui = await setup(async (url) =>
    url === "/csrf"
      ? new Promise((resolve) => {
          finish = resolve;
        })
      : response(page()),
  );
  ui.element("manual-review-id").value = "1000000";
  ui.element("manual-review-reason").value = "Motivo";
  const pending = ui.element("manual-review-form").listeners.submit({ preventDefault() {} });
  await flush();
  ui.listeners.get("offline")();
  finish(response({ headerName: "X-CSRF-TOKEN", token: "t" }));
  await pending;
  assert.equal(ui.requests.filter((r) => r.options.method === "POST").length, 0);
  const network = await setup(async (url, options) => {
    if (url === "/csrf") return response({ headerName: "X-CSRF-TOKEN", token: "t" });
    if (options.method === "POST") throw new Error("Red no disponible");
    return response(page());
  });
  network.element("manual-review-id").value = "1000000";
  network.element("manual-review-reason").value = "Motivo";
  await network.element("manual-review-form").listeners.submit({ preventDefault() {} });
  assert.match(network.element("moderation-message").textContent, /No se pudo/);
  assert.doesNotMatch(network.element("moderation-message").textContent, /enviada a revisión/);
  assert.equal(network.element("manual-review-submit").disabled, false);
});

/** El dashboard conserva contratos privados; la confirmación es una protección visual local. */
test("cancelar ocultamiento o cierre no solicita CSRF ni escribe; KEEP sin denuncias es explícito", async () => {
  const detail = {
    context: { publication: publication(), visible: true, activeReports: 0 },
    reports: [],
    history: [],
    decisions: [],
  };
  const ui = await setup(async (url) => response(url.endsWith("/1000000") ? detail : page()));
  await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  const all = [];
  function collect(node) {
    all.push(node);
    node.children.forEach(collect);
  }
  collect(ui.element("moderation-detail"));
  assert.ok(all.some((n) => n.textContent === "Finalizar revisión y mantener publicación"));
  const reason = all.find((n) => n.tagName === "textarea");
  reason.value = "Motivo ficticio";
  for (const label of ["Ocultar publicación", "Cerrar según estado terminal"])
    await all.find((n) => n.textContent === label).listeners.click();
  await all.find((n) => n.textContent === "Cancelar").listeners.click();
  assert.match(ui.element("moderation-message").textContent, /Decisión cancelada/);
  await all.find((n) => n.textContent === "Ocultar publicación").listeners.click();
  const confirmation = all.find((n) => n.className === "operator-confirmation");
  assert.equal(confirmation.hidden, false);
  confirmation.listeners.keydown({ key: "Escape", preventDefault() {} });
  assert.equal(confirmation.hidden, true);
  assert.equal(reason.focused, true);
  assert.ok(ui.requests.every((r) => !r.options.method && r.url !== "/csrf"));
  assert.doesNotMatch(ui.element("moderation-message").textContent, /Decisión registrada/);
});

test("tarjeta identifica ID y categoría; historial muestra cambios y pliega el original hostil", async () => {
  const detail = {
    context: { publication: publication(), visible: true, activeReports: 0 },
    reports: [],
    history: [],
    decisions: [
      {
        action: "CORRECT",
        actedAt: "2026-11-02T18:00:00Z",
        actorId: 4,
        reason: "Motivo",
        beforeSnapshot: JSON.stringify({ title: "Antes", reviewStatus: "EN_REVISION" }),
        afterSnapshot: JSON.stringify({
          title: "<img onerror=alert(1)>",
          reviewStatus: "SIN_REVISION",
          internal: "solo-tecnico",
        }),
      },
    ],
  };
  const ui = await setup(async (url) => response(url.endsWith("/1000000") ? detail : page()));
  assert.match(dump(ui.element("moderation-list")), /#1000000.*Punto de asistencia/);
  await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  const all = [];
  function collect(node) {
    all.push(node);
    node.children.forEach(collect);
  }
  collect(ui.element("moderation-detail"));
  assert.ok(all.some((n) => n.textContent === "Antes: Antes"));
  assert.ok(all.some((n) => n.textContent === "Después: <img onerror=alert(1)>"));
  const technical = all.find(
    (n) =>
      n.tagName === "details" &&
      n.children[0]?.textContent === "Detalle técnico · registro original",
  );
  assert.ok(technical);
  assert.notEqual(technical.open, true);
  assert.match(dump(technical), /solo-tecnico/);
  assert.ok(all.filter((n) => n.tagName === "pre").every((n) => technical.children.includes(n)));
  assert.equal(
    ui.element("moderation-list").children[0].children.at(-1).attributes["aria-pressed"],
    "true",
  );
});

test("CSS de operador se carga después del compartido y no se incorpora al service worker", () => {
  const html = fs.readFileSync(
    path.join(__dirname, "../../main/resources/templates/moderation.html"),
    "utf8",
  );
  const sw = fs.readFileSync(path.join(__dirname, "../../main/resources/static/sw.js"), "utf8");
  assert.ok(html.indexOf('href="/app.css"') < html.indexOf('href="/moderation.css"'));
  assert.doesNotMatch(sw, /["']\/moderation\.(css|js)["']/);
  assert.match(html, /id="manual-review-form"/);
});

test("logout durante refresco posterior a una decisión no anuncia éxito ni repuebla contexto", async () => {
  let finishQueue,
    queueReads = 0;
  const detail = {
    context: { publication: publication(), visible: true },
    reports: [],
    history: [],
    decisions: [],
  };
  const ui = await setup(async (url, options) => {
    if (url === "/csrf") return response({ headerName: "X-CSRF-TOKEN", token: "t" });
    if (options.method === "POST") return response({ action: "HIDE" });
    if (url.endsWith("/1000000")) return response(detail);
    if (++queueReads > 1)
      return new Promise((resolve) => {
        finishQueue = resolve;
      });
    return response(page());
  });
  await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  const all = [];
  function collect(node) {
    all.push(node);
    node.children.forEach(collect);
  }
  collect(ui.element("moderation-detail"));
  all.find((n) => n.tagName === "textarea").value = "Motivo";
  await all.find((n) => n.textContent === "Ocultar publicación").listeners.click();
  const pending = all.find((n) => n.textContent === "Confirmar decisión").listeners.click();
  await flush();
  ui.element("operator-logout").listeners.submit();
  finishQueue(response(page()));
  await pending;
  assert.equal(ui.element("moderation-detail").children.length, 0);
  assert.equal(ui.element("moderation-list").children.length, 0);
  assert.doesNotMatch(ui.element("moderation-message").textContent, /Decisión registrada/);
});

test("script sin página operativa no consulta ni altera la pantalla pública", () => {
  vm.runInNewContext(source, { document: { getElementById: () => null } });
});

// QA: el refresco cambia selecci�n; el rechazo sigue visible sin revivir otra sesi�n.
for (const status of [400, 409])
  test(`rechazo ${status} conserva mensaje despu�s de refrescar contexto`, async () => {
    const detail = {
      context: { publication: publication(), visible: true, activeReports: 0 },
      reports: [],
      history: [],
      decisions: [],
    };
    const ui = await setup(async (url, options) => {
      if (url === "/csrf") return response({ headerName: "X-CSRF-TOKEN", token: "t" });
      if (options.method === "POST") return response({}, status);
      return response(url.endsWith("/1000000") ? detail : page());
    });
    await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
    const all = [];
    function collect(n) {
      all.push(n);
      n.children.forEach(collect);
    }
    collect(ui.element("moderation-detail"));
    all.find((n) => n.tagName === "textarea").value = "Motivo";
    await all.find((n) => n.textContent.includes("mantener")).listeners.click();
    assert.match(ui.element("moderation-message").textContent, /no se aplic/);
    assert.doesNotMatch(ui.element("moderation-message").textContent, /Decisi�n registrada/);
    ui.listeners.get("redauxilio:identity-change")();
    assert.equal(ui.element("moderation-detail").children.length, 0);
  });

/** A5: el operador corrige los datos de ayuda sin convertirla en una vía. */
test("moderación HELP_REQUEST conserva campos propios al corregir y usa cierre SATISFECHA", async () => {
  const p = {
    ...publication(),
    category: "HELP_REQUEST",
    operationalStatus: "ABIERTA",
    needType: "AGUA",
    approximateQuantity: "20 familias",
    declaredUrgency: "MEDIA",
    requesterReference: "Comunidad ficticia",
  };
  const data = {
    context: { publication: p, visible: true, activeReports: 0 },
    reports: [],
    history: [],
    decisions: [],
  };
  const ui = await setup(async (url, options) =>
    url === "/csrf"
      ? response({ headerName: "X-CSRF-TOKEN", token: "t" })
      : options.method === "POST"
        ? response({ action: "CORRECT" })
        : url.endsWith("/1000000")
          ? response(data)
          : response(page()),
  );
  await ui.element("moderation-list").children[0].children.at(-1).listeners.click();
  const all = [];
  function collect(n) {
    all.push(n);
    n.children.forEach(collect);
  }
  collect(ui.element("moderation-detail"));
  all.find((n) => n.tagName === "textarea").value = "Motivo ficticio";
  const quantity = all.find((n) => n.textContent === "Cantidad aproximada (opcional)").children[0];
  quantity.value = "30 familias";
  await all
    .find((n) => n.textContent === "Aplicar corrección y anular respaldos vigentes")
    .listeners.click();
  const payload = JSON.parse(ui.requests.find((r) => r.options.method === "POST").options.body);
  assert.equal(payload.correction.needType, "AGUA");
  assert.equal(payload.correction.approximateQuantity, "30 familias");
  assert.equal(payload.correction.declaredUrgency, "MEDIA");
  assert.equal(payload.correction.requesterReference, "Comunidad ficticia");
  assert.equal(payload.correction.impactType, null);
  all.length = 0;
  collect(ui.element("moderation-detail"));
  all.find((n) => n.tagName === "textarea").value = "Cerrar ayuda ficticia";
  await all.find((n) => n.textContent === "Cerrar según estado terminal").listeners.click();
  await all.find((n) => n.textContent === "Confirmar decisión").listeners.click();
  const close = JSON.parse(
    ui.requests.filter((r) => r.options.method === "POST").at(-1).options.body,
  );
  assert.equal(close.operationalStatus, "SATISFECHA");
});
