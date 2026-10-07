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
