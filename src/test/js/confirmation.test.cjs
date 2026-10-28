"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const tick = () => new Promise((resolve) => setImmediate(resolve));
const item = { id: 1000001, confirmationCount: 2, confirmationVersion: 1, closed: false };
const response = (value, status = 200) => ({ ok: status < 300, status, json: async () => value });

/** Ejecuta el módulo real con DOM mínimo; las respuestas diferidas comprueban cambios de contexto. */
function harness({ authenticated = true, online = true, handler } = {}) {
  const events = {},
    requests = [],
    identity = { dataset: { authenticated: String(authenticated) } };
  const node = () => ({
    children: [],
    textContent: "",
    disabled: false,
    addEventListener(name, fn) {
      this[name] = fn;
    },
    setAttribute() {},
    replaceChildren() {
      this.children = [];
    },
    append(...nodes) {
      this.children.push(...nodes);
    },
  });
  const host = node();
  const context = {
    document: { createElement: node, getElementById: () => identity },
    navigator: { onLine: online },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return handler
        ? handler(url, options)
        : response(
            url === "/csrf"
              ? { token: "actual" }
              : { ...item, confirmationCount: 3, confidenceLevel: "CONFIRMADA" },
          );
    },
    window: {
      addEventListener: (name, fn) => (events[name] = fn),
      RedAuxilioPublicationUI: { loadPublications: async () => requests.push("refresh") },
      RedAuxilioIdentityStatus: {
        setAuthenticated: (value) => (identity.dataset.authenticated = String(value)),
      },
    },
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "../../main/resources/static/confirmation.js"), "utf8"),
    context,
  );
  context.window.RedAuxilioConfirmation.render(item, host);
  return {
    host,
    requests,
    context,
    identity,
    button: () => host.children[2],
    text: () => host.children.map((n) => n.textContent).join(" "),
    click: async () => {
      host.children[2].click();
      await tick();
    },
    event: (name) => events[name](),
    render: (value) => context.window.RedAuxilioConfirmation.render(value, host),
  };
}

for (const options of [{ authenticated: false }, { online: false }])
  test(
    "sin sesión o conexión no envía ni guarda confirmaciones: " + JSON.stringify(options),
    async () => {
      const h = harness(options);
      assert.equal(h.button().disabled, true);
      await h.click();
      assert.equal(h.requests.length, 0);
      assert.doesNotMatch(h.text(), /aceptada/);
    },
  );

test("caso cerrado deshabilita confirmación y ejemplos no ofrecen acción", async () => {
  const h = harness();
  h.render({ ...item, closed: true });
  assert.equal(h.button().disabled, true);
  await h.click();
  assert.equal(h.requests.length, 0);
  h.render({ ...item, id: 1 });
  assert.equal(h.host.children.length, 0);
});

test("CSRF vigente y versión observada; éxito únicamente después del servidor", async () => {
  let accepted;
  const h = harness({
    handler: async (url) =>
      url === "/csrf"
        ? response({ token: "actual" })
        : new Promise((resolve) => {
            accepted = resolve;
          }),
  });
  await h.click();
  assert.doesNotMatch(h.text(), /Confirmación aceptada/);
  assert.equal(h.button().disabled, true);
  const post = h.requests[1];
  assert.equal(post.options.method, "POST");
  assert.equal(post.options.headers["X-CSRF-TOKEN"], "actual");
  assert.deepEqual(JSON.parse(post.options.body), { version: 1 });
  assert.equal(post.options.cache, "no-store");
  accepted(response({ ...item, confirmationCount: 3 }));
  await tick();
  assert.match(h.text(), /Confirmación aceptada/);
  assert.match(h.text(), /3 confirmaciones/);
  assert.ok(h.requests.includes("refresh"));
});

for (const status of [401, 403, 409, 429, 500])
  test("error HTTP " + status + " no anuncia confirmación ni reintenta", async () => {
    const h = harness({
      handler: async (url) =>
        url === "/csrf"
          ? response({ token: "actual" })
          : response({ message: "Rechazo comprobado" }, status),
    });
    await h.click();
    assert.match(h.text(), /Rechazo comprobado/);
    assert.doesNotMatch(h.text(), /Confirmación aceptada/);
    assert.equal(h.requests.length, 2);
    if (status === 401) assert.equal(h.button().disabled, true);
  });

test("selección distinta durante CSRF impide confirmar el reporte anterior", async () => {
  let token;
  const h = harness({
    handler: async () =>
      new Promise((resolve) => {
        token = resolve;
      }),
  });
  await h.click();
  h.render({ ...item, id: item.id + 1 });
  token(response({ token: "actual" }));
  await tick();
  assert.equal(h.requests.length, 1);
  assert.doesNotMatch(h.text(), /aceptada/);
});

test("logout o desconexión durante POST descarta respuestas y no guarda cola", async () => {
  for (const event of ["offline", "redauxilio:identity-change"]) {
    let accepted;
    const h = harness({
      handler: async (url) =>
        url === "/csrf"
          ? response({ token: "actual" })
          : new Promise((resolve) => {
              accepted = resolve;
            }),
    });
    await h.click();
    if (event === "offline") h.context.navigator.onLine = false;
    else h.identity.dataset.authenticated = "false";
    h.event(event);
    accepted(response({ ...item, confirmationCount: 3 }));
    await tick();
    assert.doesNotMatch(h.text(), /Confirmación aceptada/);
    assert.equal(h.button().disabled, true);
    assert.equal(h.requests.length, 2);
  }
});

test("fallo de red conserva el conteo y permite nueva acción explícita tras reconectar", async () => {
  const h = harness({
    handler: async () => {
      throw new Error("Sin red");
    },
  });
  await h.click();
  assert.match(h.text(), /Sin red/);
  assert.match(h.text(), /2 confirmaciones/);
  assert.doesNotMatch(h.text(), /aceptada/);
  h.context.navigator.onLine = false;
  h.event("offline");
  h.context.navigator.onLine = true;
  h.event("online");
  assert.equal(h.button().disabled, false);
  assert.equal(h.requests.length, 1);
});
