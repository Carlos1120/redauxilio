"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const tick = () => new Promise((resolve) => setImmediate(resolve));
const sample = (id = 1) => ({
  id,
  title: "Refugio propio",
  description: "Descripción privada",
  location: "Sector prueba",
  category: "ASSISTANCE",
  assistanceSubtype: "SHELTER",
  operationalStatus: "DISPONIBLE",
  latitude: 4.15,
  longitude: -73.63,
  closed: false,
});
const response = (data, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => data,
});

/** DOM mínimo: prueba el módulo real, eventos y respuestas diferidas sin sustituir su lógica. */
function harness(
  authenticated = true,
  handler = async (url) => (url === "/csrf" ? response({ token: "fresh" }) : response([])),
) {
  const nodes = new Map();
  function element() {
    return {
      value: "",
      textContent: "",
      hidden: false,
      disabled: false,
      children: [],
      listeners: {},
      dataset: {},
      addEventListener(name, fn) {
        this.listeners[name] = fn;
      },
      replaceChildren(...children) {
        this.children = children;
        this.value = "";
      },
      append(child) {
        this.children.push(child);
        if (!this.value) this.value = child.value || "";
      },
      querySelectorAll() {
        return this.id === "own-status-form"
          ? [find("own-reason"), find("own-next-state"), find("own-change-status")]
          : [find("own-title-input"), find("own-save")];
      },
      reportValidity() {
        return true;
      },
    };
  }
  const find = (id) => {
    if (!nodes.has(id)) nodes.set(id, Object.assign(element(), { id }));
    return nodes.get(id);
  };
  find("identity-open").dataset.authenticated = String(authenticated);
  const listeners = new Map(),
    requests = [];
  const window = {
    addEventListener(name, fn) {
      if (!listeners.has(name)) listeners.set(name, []);
      listeners.get(name).push(fn);
    },
    RedAuxilioPublicationUI: { loadPublications: async () => requests.push("refresh-public") },
  };
  window.RedAuxilioIdentityStatus = {
    setAuthenticated(value) {
      find("identity-open").dataset.authenticated = String(value);
      for (const fn of listeners.get("redauxilio:identity-change") || [])
        fn({ detail: { authenticated: value } });
    },
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "../../main/resources/static/my-publications.js"), "utf8"),
    {
      document: { getElementById: find, createElement: element },
      window,
      navigator: { onLine: true },
      fetch: async (url, options) => {
        requests.push({ url, options });
        return handler(url, options);
      },
      Date,
      JSON,
    },
  );
  return {
    find,
    requests,
    event: async (name, detail) => {
      for (const fn of listeners.get(name) || []) fn({ detail });
      await tick();
    },
    click: async (id, name = "click") => {
      find(id).listeners[name]({ preventDefault() {} });
      await tick();
    },
    session: async (value) => {
      window.RedAuxilioIdentityStatus.setAuthenticated(value);
      await tick();
    },
  };
}

test("visitante inicia sin solicitudes privadas ni controles habilitados", async () => {
  const h = harness(false);
  await tick();
  assert.equal(h.find("own-workspace").hidden, true);
  assert.equal(h.find("own-save").disabled, true);
  assert.equal(h.requests.length, 0);
});

test("logout limpia selección, campos e historial y descarta respuesta anterior", async () => {
  let release;
  const h = harness(true, async (url) =>
    url.endsWith("/history")
      ? response([])
      : new Promise((resolve) => {
          release = resolve;
        }),
  );
  await h.click("own-refresh");
  h.find("own-title-input").value = "Secreto anterior";
  h.find("own-history").append({ textContent: "Historial privado" });
  await h.session(false);
  release(response([sample()]));
  await tick();
  assert.equal(h.find("own-select").children.length, 0);
  assert.equal(h.find("own-title-input").value, "");
  assert.equal(h.find("own-history").children.length, 0);
  assert.equal(h.find("own-workspace").hidden, true);
});

test("selección y edición usan CSRF fresco y actualizan consulta e historial legible", async () => {
  const h = harness(true, async (url, options) => {
    if (url === "/csrf") return response({ token: "fresh" });
    if (url.endsWith("/history"))
      return response([
        {
          action: "EDIT",
          changedAt: "2026-10-27T12:00:00Z",
          beforeSnapshot: JSON.stringify(sample()),
          afterSnapshot: JSON.stringify({ ...sample(), title: "Nuevo título" }),
        },
      ]);
    if (options.method !== "GET") return response(sample());
    return response([sample(), sample(2)]);
  });
  await h.click("own-refresh");
  assert.equal(h.find("own-title-input").value, "Refugio propio");
  h.find("own-title-input").value = "Nuevo título";
  await h.click("own-edit-form", "submit");
  await tick();
  const patch = h.requests.find((r) => r.options?.method === "PATCH");
  assert.equal(patch.options.headers["X-CSRF-TOKEN"], "fresh");
  assert.equal(JSON.parse(patch.options.body).title, "Nuevo título");
  assert.ok(h.requests.includes("refresh-public"));
  assert.match(h.find("own-history").children[0].textContent, /Refugio propio.*Nuevo título/);
});

for (const status of [401, 403])
  test("respuesta " + status + " invalida datos privados", async () => {
    let expired = false;
    const h = harness(true, async (url) =>
      expired
        ? response({ message: "Sesión no válida" }, status)
        : response(url.endsWith("/history") ? [] : [sample()]),
    );
    await h.click("own-refresh");
    expired = true;
    await h.click("own-refresh");
    assert.equal(h.find("own-select").children.length, 0);
    assert.equal(h.find("own-title-input").value, "");
    assert.equal(h.find("own-save").disabled, true);
  });

test("un error HTTP se informa sin anunciar éxito", async () => {
  const h = harness(true, async () => response({ message: "Fallo temporal" }, 500));
  await h.click("own-refresh");
  assert.match(h.find("own-message").textContent, /Fallo temporal/);
  assert.equal(h.find("own-workspace").hidden, true);
});

test("historial tardío de una selección anterior no reemplaza la actual", async () => {
  let oldHistory;
  const h = harness(true, async (url) => {
    if (url.endsWith("/1/history"))
      return new Promise((resolve) => {
        oldHistory = resolve;
      });
    if (url.endsWith("/2/history"))
      return response([
        { action: "STATUS", changedAt: "2026-10-27T12:00:00Z", reason: "Segundo reporte" },
      ]);
    return response([sample(), sample(2)]);
  });
  await h.click("own-refresh");
  h.find("own-select").value = "2";
  await h.click("own-select", "change");
  oldHistory(
    response([{ action: "EDIT", changedAt: "2026-10-27T12:00:00Z", reason: "Primero obsoleto" }]),
  );
  await tick();
  assert.match(h.find("own-history").children[0].textContent, /Segundo reporte/);
  assert.doesNotMatch(
    h
      .find("own-history")
      .children.map((n) => n.textContent)
      .join(""),
    /obsoleto/,
  );
});

test("cierre y reapertura usan POST, motivo y controles coherentes con closed", async () => {
  let item = sample();
  const h = harness(true, async (url, options) => {
    if (url === "/csrf") return response({ token: "fresh" });
    if (url.endsWith("/history")) return response([]);
    if (options.method === "POST") {
      const body = JSON.parse(options.body);
      item = {
        ...item,
        operationalStatus: body.operationalStatus,
        closed: body.operationalStatus === "CERRADO",
      };
      return response(item);
    }
    return response([item]);
  });
  await h.click("own-refresh");
  h.find("own-next-state").value = "CERRADO";
  await h.click("own-status-form", "submit");
  await tick();
  assert.equal(h.find("own-save").disabled, true);
  assert.equal(h.find("own-reason").required, true);
  h.find("own-next-state").value = "DISPONIBLE";
  h.find("own-reason").value = "Reapertura comprobada";
  await h.click("own-status-form", "submit");
  await tick();
  assert.equal(h.find("own-save").disabled, false);
  assert.equal(
    JSON.parse(h.requests.filter((r) => r.options?.method === "POST").at(-1).options.body).reason,
    "Reapertura comprobada",
  );
});

test("reconexión confirmada y cambio de cuenta recargan sin conservar datos anteriores", async () => {
  let title = "Cuenta A";
  const h = harness(true, async (url) =>
    response(url.endsWith("/history") ? [] : [{ ...sample(), title }]),
  );
  await h.click("own-refresh");
  await h.event("offline");
  assert.equal(h.find("own-title-input").value, "");
  title = "Cuenta B";
  await h.session(true);
  assert.equal(h.find("own-title-input").value, "Cuenta B");
});

test("error al guardar conserva la corrección escrita para reintentar", async () => {
  const h = harness(true, async (url, options) => {
    if (url === "/csrf") return response({ token: "fresh" });
    if (options.method === "PATCH") return response({ message: "Error temporal" }, 500);
    return response(url.endsWith("/history") ? [] : [sample()]);
  });
  await h.click("own-refresh");
  h.find("own-title-input").value = "Cambio sin confirmar";
  await h.click("own-edit-form", "submit");
  assert.equal(h.find("own-title-input").value, "Cambio sin confirmar");
  assert.match(h.find("own-message").textContent, /Error temporal/);
});

test("logout durante renovación CSRF impide enviar escritura de la sesión anterior", async () => {
  let tokenReady;
  const h = harness(true, async (url) => {
    if (url === "/csrf")
      return new Promise((resolve) => {
        tokenReady = resolve;
      });
    return response(url.endsWith("/history") ? [] : [sample()]);
  });
  await h.click("own-refresh");
  await h.click("own-edit-form", "submit");
  await h.session(false);
  tokenReady(response({ token: "anterior" }));
  await tick();
  assert.equal(
    h.requests.some((r) => r.options?.method === "PATCH"),
    false,
  );
  assert.equal(h.find("own-save").disabled, true);
});

test("respuesta de escritura tardía después de logout no anuncia éxito ni rehabilita controles", async () => {
  let saved;
  const h = harness(true, async (url, options) => {
    if (url === "/csrf") return response({ token: "fresh" });
    if (options.method === "PATCH")
      return new Promise((resolve) => {
        saved = resolve;
      });
    return response(url.endsWith("/history") ? [] : [sample()]);
  });
  await h.click("own-refresh");
  await h.click("own-edit-form", "submit");
  await h.session(false);
  saved(response(sample()));
  await tick();
  assert.equal(h.find("own-workspace").hidden, true);
  assert.equal(h.find("own-save").disabled, true);
  assert.doesNotMatch(h.find("own-message").textContent, /Corrección guardada/);
});
