"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function loadIdentityForms({
  online = true,
  response = { ok: true, parameterName: "_csrf", token: "token-fresco" },
} = {}) {
  const listeners = new Map();
  const tokenField = { value: "token-antiguo" };
  let submitted = false;
  const form = {
    dataset: {},
    addEventListener: (type, listener) => listeners.set(type, listener),
    querySelector: (selector) => (selector === 'input[name="_csrf"]' ? tokenField : null),
    requestSubmit: () => {
      assert.equal(tokenField.value, "token-fresco");
      submitted = true;
      listeners.get("submit")({ preventDefault: () => assert.fail("el envío fresco se bloqueó") });
    },
  };
  const message = { textContent: "", hidden: true };
  const calls = [];
  const context = {
    document: {
      querySelectorAll: (selector) => {
        assert.equal(selector, 'form[method="post"]');
        return [form];
      },
      getElementById: (id) => {
        assert.equal(id, "identity-csrf-message");
        return message;
      },
    },
    navigator: { onLine: online },
    fetch: async (url, options) => {
      calls.push({ url, options });
      if (!response.ok) throw new Error("sin conexión");
      return { ok: true, json: async () => response };
    },
  };
  const source = fs.readFileSync(
    path.join(__dirname, "../../main/resources/static/identity-forms.js"),
    "utf8",
  );
  vm.runInNewContext(source, context, { filename: "identity-forms.js" });
  return { form, listeners, message, calls, wasSubmitted: () => submitted };
}

test("al reconectar, obtiene un token sin caché antes de enviar login o registro", async () => {
  const form = loadIdentityForms();
  let prevented = false;

  await form.listeners.get("submit")({
    preventDefault: () => {
      prevented = true;
    },
    submitter: { name: "submit" },
  });

  assert.equal(prevented, true);
  assert.equal(form.wasSubmitted(), true);
  assert.equal(form.calls[0].url, "/csrf");
  assert.equal(form.calls[0].options.credentials, "same-origin");
  assert.equal(form.calls[0].options.cache, "no-store");
  assert.equal(form.calls[0].options.headers.Accept, "application/json");
  assert.equal(form.message.hidden, true);
});

test("si no se puede obtener un token fresco, no envía el token almacenado", async () => {
  const form = loadIdentityForms({ online: false, response: { ok: false } });

  await form.listeners.get("submit")({ preventDefault: () => {} });

  assert.equal(form.wasSubmitted(), false);
  assert.equal(form.message.hidden, false);
  assert.match(form.message.textContent, /Conéctate a Internet/);
});

test("login y logout usan CSRF y actualizan sesión sin navegación completa", async () => {
  for (const [action, destination, authenticated] of [
    ["/login", "http://localhost/?access=account", true],
    ["/logout", "http://localhost/?access=login&signedout", false],
  ]) {
    const listeners = new Map();
    const events = [];
    const csrfField = { value: "token-antiguo" };
    const message = { textContent: "", hidden: true };
    const form = {
      action: `http://localhost${action}`,
      dataset: {},
      reset() {},
      addEventListener: (name, callback) => listeners.set(name, callback),
      querySelector: (selector) =>
        selector === 'input[name="_csrf"]'
          ? csrfField
          : selector === 'input[type="password"]'
            ? { value: "secreto" }
            : null,
      requestSubmit: async () => listeners.get("submit")({ preventDefault() {}, submitter: null }),
    };
    const context = {
      document: {
        querySelectorAll: () => [form],
        getElementById: () => message,
      },
      navigator: { onLine: true },
      window: {
        location: { origin: "http://localhost" },
        dispatchEvent: (event) => events.push(event),
      },
      CustomEvent: class CustomEvent {
        constructor(type, options) {
          this.type = type;
          this.detail = options.detail;
        }
      },
      FormData: class FormDataMock {
        [Symbol.iterator]() {
          return [
            ["username", "prueba@example.test"],
            ["_csrf", csrfField.value],
          ][Symbol.iterator]();
        }
      },
      URL,
      URLSearchParams,
      fetch: async (url, options) => {
        if (url === "/csrf")
          return {
            ok: true,
            json: async () => ({ parameterName: "_csrf", token: "token-fresco" }),
          };
        assert.equal(options.method, "POST");
        assert.equal(options.credentials, "same-origin");
        assert.match(String(options.body), /token-fresco/);
        return { ok: true, url: destination };
      },
    };
    const source = fs.readFileSync(
      path.join(__dirname, "../../main/resources/static/identity-forms.js"),
      "utf8",
    );
    vm.runInNewContext(source, context, { filename: "identity-forms.js" });
    let navigationPrevented = false;
    await listeners.get("submit")({
      preventDefault: () => {
        navigationPrevented = true;
      },
      submitter: null,
    });

    assert.equal(navigationPrevented, true);
    assert.equal(events[0].type, "redauxilio:identity-change");
    assert.equal(events[0].detail.authenticated, authenticated);
    assert.equal(message.hidden, true);
  }
});
