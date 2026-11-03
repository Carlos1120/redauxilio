"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const tick = () => new Promise((resolve) => setImmediate(resolve));

function node(tag) {
  return {
    tag,
    children: [],
    value: "",
    textContent: "",
    listeners: {},
    append(...children) {
      this.children.push(...children);
    },
    replaceChildren(...children) {
      this.children = children;
    },
    setAttribute() {},
    addEventListener(name, listener) {
      this.listeners[name] = listener;
    },
  };
}

function harness(canVerify = true) {
  const requests = [];
  let refreshes = 0;
  const container = node("section");
  const context = {
    document: { createElement: node },
    fetch: async (url, options = {}) => {
      requests.push({ url, options });
      if (url === "/csrf") return { ok: true, json: async () => ({ token: "fresh" }) };
      if (url === "/api/my/verification-permission")
        return { ok: true, json: async () => ({ canVerify }) };
      if (options.method === "POST") return { ok: true };
      return {
        ok: true,
        json: async () => [
          {
            accountName: "Entidad pública",
            note: "Inspección",
            reference: "ACTA-9",
            verifiedAt: "2026-11-02T12:00:00Z",
          },
        ],
      };
    },
    window: {
      RedAuxilioPublicationUI: {
        loadPublications: async () => refreshes++,
        refreshConfirmation() {},
      },
    },
  };
  vm.runInNewContext(
    fs.readFileSync(
      path.join(__dirname, "../../main/resources/static/official-verification.js"),
      "utf8",
    ),
    context,
  );
  const item = { id: 1000001 };
  context.window.RedAuxilioOfficialVerification.render(item, container);
  return { context, container, item, requests, refreshes: () => refreshes };
}

test("solo la cuenta autorizada ve el formulario; publicación oficial usa CSRF y respuesta del servidor", async () => {
  const denied = harness(false);
  await tick();
  await tick();
  assert.equal(
    denied.container.children.some((child) => child.tag === "form"),
    false,
  );

  const allowed = harness(true);
  await tick();
  await tick();
  const form = allowed.container.children.find((child) => child.tag === "form");
  assert.ok(form);
  assert.match(
    allowed.container.children[2].children[0].children[0].textContent,
    /Entidad pública/,
  );
  form.children[0].children[0].value = "Verificación institucional";
  form.children[1].children[0].value = "Resolución 9";
  await form.listeners.submit({ preventDefault() {} });
  await tick();
  const post = allowed.requests.find((request) => request.options.method === "POST");
  assert.equal(post.options.headers["X-CSRF-TOKEN"], "fresh");
  assert.deepEqual(JSON.parse(post.options.body), {
    note: "Verificación institucional",
    reference: "Resolución 9",
  });
  assert.equal(allowed.refreshes(), 1);
});
