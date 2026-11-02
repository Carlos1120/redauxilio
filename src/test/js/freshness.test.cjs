"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const context = { window: {} };
vm.runInNewContext(
  fs.readFileSync(path.join(__dirname, "../../main/resources/static/freshness.js"), "utf8"),
  context,
);
const normalize = context.window.RedAuxilioFreshness.normalize;
test("caché: antes/en/después del vencimiento; copia inmutable y confianza independiente", () => {
  const item = {
    freshnessStatus: "VIGENTE",
    freshnessExpiresAt: "2026-11-02T12:00:00Z",
    confidenceLevel: "CONFIRMADA",
    reviewStatus: "EN_REVISION",
  };
  const expiry = Date.parse(item.freshnessExpiresAt);
  assert.equal(normalize(item, expiry - 1).freshnessStatus, "VIGENTE");
  assert.equal(normalize(item, expiry).freshnessStatus, "VIGENTE");
  const stale = normalize(item, expiry + 1);
  assert.equal(stale.freshnessStatus, "DESACTUALIZADA");
  assert.equal(stale.confidenceLevel, "CONFIRMADA");
  assert.equal(stale.reviewStatus, "EN_REVISION");
  assert.equal(item.freshnessStatus, "VIGENTE");
  assert.equal(
    normalize({ ...item, freshnessStatus: "DESACTUALIZADA" }, expiry - 1).freshnessStatus,
    "DESACTUALIZADA",
  );
});
test("respuesta anterior sin vigencia nunca inventa VIGENTE ni fecha", () => {
  const result = normalize({ title: "Ficticio" }, 0);
  assert.equal(result.freshnessStatus, undefined);
});
