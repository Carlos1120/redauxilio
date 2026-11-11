"use strict";
const test = require("node:test"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path"),
  vm = require("node:vm");
const source = fs.readFileSync(
  path.join(__dirname, "../../main/resources/static/publication-form.js"),
  "utf8",
);
const response = (data, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => data,
});
/** Formulario real con DOM mínimo y servidor controlado; sin escribir datos reales. */
function setup(status = 201, candidates = []) {
  const nodes = new Map(),
    requests = [],
    stats = { saved: 0, cleared: 0 };
  function get(id) {
    if (!nodes.has(id))
      nodes.set(id, {
        value: "",
        hidden: false,
        disabled: false,
        labels: [{ textContent: "" }],
        dataset: {},
        children: [],
        listeners: {},
        addEventListener(n, f) {
          this.listeners[n] = f;
        },
        setAttribute(n, value) {
          this[n] = value;
        },
        querySelectorAll() {
          return this.controls ?? [];
        },
        replaceChildren() {
          this.children = [];
        },
        append(n) {
          this.children.push(n);
        },
        checkValidity() {
          return true;
        },
        reportValidity() {},
        reset() {
          get("publication-category").value = "ASSISTANCE";
        },
        click() {},
      });
    return nodes.get(id);
  }
  get("publication-category").value = "HELP_REQUEST";
  get("identity-open").dataset.authenticated = "true";
  for (const [id, controls] of [
    ["assistance-fields", ["assistance-subtype", "assistance-status"]],
    ["road-fields", ["road-impact", "road-status"]],
    ["help-fields", ["help-need-type", "help-quantity", "help-urgency", "help-requester"]],
    ["missing-fields", ["missing-age", "missing-seen"]],
  ])
    get(id).controls = controls.map(get);
  for (const [id, value] of Object.entries({
    "publication-title": "Necesidad ficticia",
    "draft-text": "Agua para comunidad ficticia",
    "publication-location": "Sector público",
    latitude: "4.15",
    longitude: "-73.63",
    "help-need-type": "AGUA",
    "help-quantity": "20 familias",
    "help-urgency": "ALTA",
    "help-requester": "Comunidad ficticia",
  }))
    get(id).value = value;
  const ui = {
    saveDraft: async () => stats.saved++,
    clearDraft: async () => stats.cleared++,
    loadPublications: async () => {},
  };
  const context = {
    document: {
      getElementById: get,
      createElement: () => ({ append() {}, addEventListener() {}, setAttribute() {} }),
    },
    window: { RedAuxilioPublicationUI: ui },
    URL,
    FormData,
    Blob,
    File,
    navigator: {},
    fetch: async (url, options) => {
      requests.push({ url, options });
      return response(
        url === "/api/my/publications/possible-duplicates"
          ? candidates
          : url === "/csrf"
            ? { token: "t" }
            : { id: 1000000, latitude: 4.15, longitude: -73.63, message: "Rechazo de prueba" },
        url === "/csrf" || url === "/api/my/publications/possible-duplicates" ? 200 : status,
      );
    },
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "../../main/resources/static/missing-person.js"), "utf8"),
    context,
  );
  vm.runInNewContext(source, context);
  return { get, requests, stats };
}
test("HELP_REQUEST envía datos propios, ABIERTA y CSRF; cambiar tipo desactiva campos ocultos", async () => {
  const h = setup();
  assert.equal(h.get("help-fields").hidden, false);
  assert.equal(h.get("assistance-subtype").disabled, true);
  await h.get("publish-report").listeners.click();
  const post = h.requests.find(
      (r) => r.options.method === "POST" && r.url !== "/api/my/publications/possible-duplicates",
    ),
    body = JSON.parse(post.options.body);
  assert.equal(body.category, "HELP_REQUEST");
  assert.equal(body.operationalStatus, "ABIERTA");
  assert.equal(body.assistanceSubtype, null);
  assert.equal(body.impactType, null);
  assert.equal(body.needType, "AGUA");
  assert.equal(body.requesterReference, "Comunidad ficticia");
  assert.equal(body.approximateQuantity, "20 familias");
  assert.equal(body.declaredUrgency, "ALTA");
  assert.equal(post.options.headers["X-CSRF-TOKEN"], "t");
  assert.equal(h.stats.cleared, 1);
  h.get("publication-category").listeners.change();
  assert.equal(h.get("help-need-type").disabled, true);
  assert.equal(h.get("help-fields").hidden, true);
});
test("un rechazo conserva borrador y no anuncia éxito; referencias sensibles no se envían", async () => {
  const h = setup(400);
  await h.get("publish-report").listeners.click();
  assert.equal(h.stats.saved, 1);
  assert.equal(h.stats.cleared, 0);
  assert.match(h.get("draft-message").textContent, /borrador local se conserva/);
  assert.doesNotMatch(h.get("draft-message").textContent, /Publicación creada/);
  const invalid = setup();
  invalid.get("help-requester").value = "correo@example.invalid";
  await invalid.get("publish-report").listeners.click();
  assert.equal(invalid.requests.length, 0);
});

test("evidencia válida se envía como multipart atómico y conserva JSON tipado", async () => {
  const h = setup();
  const photo = new File([new Uint8Array([1, 2, 3])], "evidencia.png", { type: "image/png" });
  h.get("publication-evidence").files = [photo];
  h.get("publication-evidence").listeners.change();
  await h.get("publish-report").listeners.click();
  const post = h.requests.find(
    (request) =>
      request.options.method === "POST" &&
      request.url !== "/api/my/publications/possible-duplicates",
  );
  assert.equal(post.url, "/api/publications/with-evidence");
  assert.equal(post.options.headers["Content-Type"], undefined);
  assert.equal(post.options.body.getAll("files").length, 1);
  assert.match(await post.options.body.get("publication").text(), /"category":"HELP_REQUEST"/);
  assert.equal(h.stats.cleared, 1);
});

/** RA-09 comparte campos comunes pero envía atributos propios y omite contacto. */
test("MISSING_PERSON envía fecha y datos estructurados sin contacto ni campos de otras categorías", async () => {
  const h = setup();
  h.get("publication-category").value = "MISSING_PERSON";
  h.get("publication-category").listeners.change();
  assert.equal(h.get("missing-seen").disabled, false);
  h.get("publication-title").value = "Referencia ficticia";
  h.get("missing-age").value = "18–29";
  h.get("missing-seen").value = "2026-11-03T08:30";
  await h.get("publish-report").listeners.click();
  const post = h.requests.find(
      (x) => x.options.method === "POST" && x.url !== "/api/my/publications/possible-duplicates",
    ),
    body = JSON.parse(post.options.body);
  assert.equal(body.operationalStatus, "DESAPARECIDA");
  assert.equal(body.assistanceSubtype, null);
  assert.equal(body.impactType, null);
  assert.equal(body.missingPerson.referenceName, "Referencia ficticia");
  assert.match(body.missingPerson.lastSeenAt, /Z$/);
  assert.equal(body.missingPerson.ageRange, "18–29");
  assert.equal(body.needType, undefined);
  assert.equal(body.contact, undefined);
  assert.equal(h.get("help-need-type").disabled, true);
});

test("duplicado advierte, exige motivo, invalida cambios y conserva borrador", async () => {
  const h = setup(201, [{ id: 1000001, title: "Caso publico", location: "Sector" }]);
  await h.get("publish-report").listeners.click();
  assert.equal(h.stats.cleared, 0);
  assert.equal(h.get("duplicate-warning").hidden, false);
  assert.equal(
    h.requests.some((r) => r.url === "/api/publications"),
    false,
  );
  h.get("duplicate-justification").value = "Necesidad distinta en la misma zona";
  await h.get("publish-report").listeners.click();
  const post = h.requests.find((r) => r.url === "/api/publications");
  assert.equal(
    JSON.parse(post.options.body).duplicateJustification,
    "Necesidad distinta en la misma zona",
  );
  assert.equal(h.stats.cleared, 1);
  const changed = setup(201, [{ id: 1000001, title: "Caso", location: "Sector" }]);
  await changed.get("publish-report").listeners.click();
  changed.get("duplicate-justification").value = "Motivo anterior";
  changed.get("draft-form").listeners.input({ target: { id: "latitude" } });
  assert.equal(changed.get("duplicate-justification").value, "");
  assert.equal(changed.get("duplicate-warning").hidden, true);
});
