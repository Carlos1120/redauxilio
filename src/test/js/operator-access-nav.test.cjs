"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

/** Simula una cuenta con ambos permisos y comprueba la navegación de cada modo. */
for (const [mode, operatorVisible, verifierVisible] of [
  ["verifier", false, true],
  ["operator", true, false],
]) {
  test(`la navegación ${mode} presenta solo su espacio`, async () => {
    const elements = Object.fromEntries(
      [
        "operator-nav-link",
        "operator-mobile-link",
        "verifier-nav-link",
        "verifier-mobile-link",
      ].map((id) => [id, { hidden: true }]),
    );
    elements["identity-open"] = { dataset: { authenticated: "true" } };
    const classes = new Set();
    const listeners = {};
    const context = {
      document: {
        getElementById: (id) => elements[id],
        querySelector: () => ({
          classList: {
            toggle: (name, value) => (value ? classes.add(name) : classes.delete(name)),
          },
        }),
        addEventListener() {},
      },
      window: { addEventListener: (name, callback) => (listeners[name] = callback) },
      navigator: { onLine: true },
      sessionStorage: { getItem: () => mode },
      fetch: async (url) => ({
        ok: true,
        redirected: false,
        json: async () => (url.includes("operator") ? { canModerate: true } : { canVerify: true }),
      }),
    };
    vm.runInNewContext(
      fs.readFileSync(
        path.join(__dirname, "../../main/resources/static/operator-access-nav.js"),
        "utf8",
      ),
      context,
    );
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(!elements["operator-nav-link"].hidden, operatorVisible);
    assert.equal(!elements["operator-mobile-link"].hidden, operatorVisible);
    assert.equal(!elements["verifier-nav-link"].hidden, verifierVisible);
    assert.equal(!elements["verifier-mobile-link"].hidden, verifierVisible);
    assert.ok(classes.has("has-role-destination"));
  });
}
