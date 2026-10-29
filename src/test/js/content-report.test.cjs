"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const tick = () => new Promise((resolve) => setImmediate(resolve));
const publication = {
  id: 1000001,
  reviewStatus: "SIN_REVISION",
  confidenceLevel: "CONFIRMADA",
  closed: false,
};
const response = (value, status = 200) => ({ ok: status < 300, status, json: async () => value });

/** DOM mínimo con controles reales del módulo; comprueba respuestas diferidas y limpieza de motivos. */
function harness({ authenticated = true, online = true, handler } = {}) {
  const events = {},
    requests = [],
    identity = { dataset: { authenticated: String(authenticated) } };
  function node(tag) {
    return {
      tag,
      children: [],
      textContent: "",
      value: "",
      disabled: false,
      listeners: {},
      setAttribute() {},
      append(...children) {
        this.children.push(...children);
      },
      replaceChildren() {
        this.children = [];
      },
      addEventListener(name, fn) {
        this.listeners[name] = fn;
      },
      reportValidity() {
        return true;
      },
      querySelector(selector) {
        for (const child of this.children) {
          if (child.tag === selector) return child;
          const nested = child.querySelector(selector);
          if (nested) return nested;
        }
        return null;
      },
    };
  }
  const host = node("div");
  const context = {
    document: { createElement: node, getElementById: () => identity },
    navigator: { onLine: online },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return handler
        ? handler(url, options)
        : response(
            url === "/csrf" ? { token: "fresco" } : { ...publication, reviewStatus: "EN_REVISION" },
          );
    },
    window: {
      addEventListener: (name, fn) => (events[name] = fn),
      RedAuxilioPublicationUI: { loadPublications: async () => requests.push("refresh") },
      RedAuxilioIdentityStatus: {
        setAuthenticated(value) {
          identity.dataset.authenticated = String(value);
          events["redauxilio:identity-change"]();
        },
      },
    },
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "../../main/resources/static/content-report.js"), "utf8"),
    context,
  );
  context.window.RedAuxilioContentReport.render(publication, host);
  const form = () => host.children[0];
  return {
    host,
    context,
    identity,
    requests,
    form,
    field: () => form().children[1],
    button: () => form().children[4],
    message: () => form().children[3].textContent,
    enter(value) {
      const field = form().children[1];
      field.value = value;
      field.listeners.input();
    },
    submit: async () => {
      form().listeners.submit({ preventDefault() {} });
      await tick();
    },
    render: (item) => context.window.RedAuxilioContentReport.render(item, host),
    event: (name) => events[name](),
  };
}

for (const options of [{ authenticated: false }, { online: false }])
  test("no reporta sin sesión/conexión " + JSON.stringify(options), async () => {
    const h = harness(options);
    h.enter("Motivo privado");
    await h.submit();
    assert.equal(h.requests.length, 0);
    assert.equal(h.button().disabled, true);
    assert.doesNotMatch(h.message(), /recibido/);
  });

test("ejemplos no tienen acción y cerrados visibles sí se pueden denunciar", async () => {
  const h = harness();
  h.render({ ...publication, closed: true });
  h.enter("Contenido incorrecto aunque cerró");
  await h.submit();
  assert.equal(h.requests.filter((r) => r.options?.method === "POST").length, 1);
  h.render({ ...publication, id: 1 });
  assert.equal(h.host.children.length, 0);
});

test("motivo obligatorio y límite se validan antes de solicitar CSRF", async () => {
  const h = harness();
  for (const reason of ["", " \n\t", "x".repeat(501)]) {
    h.enter(reason);
    await h.submit();
    assert.match(h.message(), /1 y 500/);
  }
  assert.equal(h.requests.length, 0);
});

test("CSRF fresco y solo motivo; recepción únicamente tras respuesta persistida", async () => {
  let accepted;
  const h = harness({
    handler: async (url) =>
      url === "/csrf"
        ? response({ token: "fresco" })
        : new Promise((resolve) => {
            accepted = resolve;
          }),
  });
  h.enter("  Motivo privado  ");
  await h.submit();
  assert.equal(h.button().disabled, true);
  assert.doesNotMatch(h.message(), /recibido/);
  const post = h.requests[1];
  assert.equal(post.options.method, "POST");
  assert.equal(post.options.cache, "no-store");
  assert.equal(post.options.headers["X-CSRF-TOKEN"], "fresco");
  assert.deepEqual(JSON.parse(post.options.body), { reason: "Motivo privado" });
  accepted(response({ ...publication, reviewStatus: "EN_REVISION" }));
  await tick();
  assert.match(h.message(), /recibido por el servidor/);
  assert.equal(h.field().value, "");
  assert.ok(h.requests.includes("refresh"));
});

for (const status of [400, 403, 404, 409, 500])
  test(
    "error " + status + " conserva motivo para reintento explícito sin falso éxito",
    async () => {
      const h = harness({
        handler: async (url) =>
          url === "/csrf"
            ? response({ token: "fresco" })
            : response({ message: "Rechazo comprobado" }, status),
      });
      h.enter("Razón privada");
      await h.submit();
      assert.match(h.message(), /Rechazo comprobado/);
      assert.doesNotMatch(h.message(), /recibido/);
      assert.equal(h.field().value, "Razón privada");
      assert.equal(h.requests.length, 2);
    },
  );

test("401 y logout limpian motivos y controles", async () => {
  const h = harness({
    handler: async (url) =>
      url === "/csrf"
        ? response({ token: "fresco" })
        : response({ message: "Sesión expirada" }, 401),
  });
  h.enter("Motivo privado");
  await h.submit();
  assert.equal(h.field().value, "");
  assert.equal(h.button().disabled, true);
  assert.doesNotMatch(h.message(), /recibido/);
});

test("cambio de selección durante CSRF descarta motivo y evita POST obsoleto", async () => {
  let token;
  const h = harness({
    handler: async () =>
      new Promise((resolve) => {
        token = resolve;
      }),
  });
  h.enter("Privado A");
  await h.submit();
  const oldField = h.field();
  h.render({ ...publication, id: publication.id + 1 });
  assert.equal(oldField.value, "");
  token(response({ token: "anterior" }));
  await tick();
  assert.equal(h.requests.length, 1);
  assert.equal(h.field().value, "");
  assert.doesNotMatch(h.message(), /recibido/);
});

test("logout/desconexión durante POST descartan respuestas y razones anteriores", async () => {
  for (const event of ["redauxilio:identity-change", "offline"]) {
    let accepted;
    const h = harness({
      handler: async (url) =>
        url === "/csrf"
          ? response({ token: "fresco" })
          : new Promise((resolve) => {
              accepted = resolve;
            }),
    });
    h.enter("Motivo privado");
    await h.submit();
    const oldField = h.field();
    if (event === "offline") h.context.navigator.onLine = false;
    else h.identity.dataset.authenticated = "false";
    h.event(event);
    accepted(response(publication));
    await tick();
    assert.equal(oldField.value, "");
    assert.equal(h.field().value, "");
    assert.equal(h.button().disabled, true);
    assert.doesNotMatch(h.message(), /recibido/);
    assert.equal(h.requests.length, 2);
  }
});

test("fallo de red y reconexión nunca crean cola ni reenvían", async () => {
  const h = harness({
    handler: async () => {
      throw new Error("Sin red");
    },
  });
  h.enter("Privado");
  await h.submit();
  assert.match(h.message(), /Sin red/);
  assert.doesNotMatch(h.message(), /recibido/);
  h.context.navigator.onLine = false;
  h.event("offline");
  h.context.navigator.onLine = true;
  h.event("online");
  await tick();
  assert.equal(h.requests.length, 1);
  assert.equal(h.field().value, "");
});

test("token CSRF inválido no produce escritura", async () => {
  const h = harness({ handler: async () => response({}, 500) });
  h.enter("Privado");
  await h.submit();
  assert.equal(h.requests.length, 1);
  assert.doesNotMatch(h.message(), /recibido/);
});

test("fallo de fetch se explica en español sin afirmar recepción ni reenviar", async () => {
  const h = harness({
    handler: async () => {
      throw new TypeError("Failed to fetch");
    },
  });
  h.enter("Motivo privado");
  await h.submit();
  assert.match(h.message(), /No hay confirmación de recepción/);
  assert.doesNotMatch(h.message(), /Failed to fetch|recibido por el servidor/);
  assert.equal(h.requests.length, 1);
});
