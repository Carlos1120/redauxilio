"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "../../main/resources");
const html = fs.readFileSync(path.join(root, "templates/index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "static/laguna.css"), "utf8");
const js = fs.readFileSync(path.join(root, "static/missing-person-match.js"), "utf8");
const manifest = fs.readFileSync(path.join(root, "demo-match/candidates.json"), "utf8");

test("la sección presenta la comparación con lenguaje breve", () => {
  assert.match(html, /id="missing-match-section"/);
  assert.match(html, /PERSONAS DESAPARECIDAS/);
  assert.match(html, /Compara hasta tres fotos con los casos preparados/);
  assert.doesNotMatch(html, /simulaci|simulad|fictici|demostraci/i);
  assert.match(html, /Las fotos se comparan en memoria y no\s+se guardan/);
});

test("el catálogo prepara dos casos con referencias y comparación separadas", () => {
  const candidates = JSON.parse(manifest);
  assert.deepEqual(
    candidates.map(({ referenceName }) => referenceName),
    ["Jesús Peña", "Santiago Ortiz"],
  );
  for (const candidate of candidates) {
    assert.equal(candidate.images.length, 3);
    assert.equal(candidate.comparisonImages.length, 1);
    for (const location of [...candidate.images, ...candidate.comparisonImages]) {
      const relative = location.replace("classpath:", "");
      assert.ok(fs.statSync(path.join(root, relative)).size > 0, relative);
    }
  }
  assert.match(js, /download\.href = candidate\.comparisonPhotoUrl/);
});

test("la carga admite máximo tres fotos y pide sesión al recibir 401", () => {
  assert.match(html, /id="missing-match-files"[\s\S]*?multiple/);
  assert.match(js, /files\.length > 3/);
  assert.match(js, /response\.status === 401 \|\| response\.status === 403/);
  assert.match(js, /identity\?\.click\(\)/);
});

test("la pantalla cuenta con adaptación móvil y movimiento reducido", () => {
  assert.match(css, /@media \(max-width: 760px\)[\s\S]*?\.missing-match-layout/);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)[\s\S]*?#missing-match-submit/);
});
