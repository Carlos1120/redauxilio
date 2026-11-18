"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "../../main/resources");
const index = fs.readFileSync(path.join(root, "templates/index.html"), "utf8");
const moderation = fs.readFileSync(path.join(root, "templates/moderation.html"), "utf8");
const css = fs.readFileSync(path.join(root, "static/laguna.css"), "utf8");
const loader = fs.readFileSync(path.join(root, "static/app-loader.js"), "utf8");
const sw = fs.readFileSync(path.join(root, "static/sw.js"), "utf8");

test("la transición de carga cubre las superficies pública y privada", () => {
  for (const html of [index, moderation]) {
    assert.match(html, /id="app-loader" class="app-loader"/);
    assert.match(html, /role="status" aria-label="Cargando RedAuxilio"/);
    assert.match(html, /src="\/app-loader\.js" defer/);
  }
});

test("la animación es responsive y respeta movimiento reducido", () => {
  assert.match(css, /\.app-loader\s*\{[\s\S]*?position:\s*fixed/);
  assert.match(css, /@media \(max-width: 520px\)[\s\S]*?\.app-loader__content/);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
  assert.match(loader, /prefers-reduced-motion: reduce/);
  assert.match(loader, /window\.setTimeout\(close, maximumDuration\)/);
  assert.match(loader, /loader\.hidden = true/);
});

test("el cargador queda disponible en la instalación PWA", () => {
  assert.match(sw, /"\/app-loader\.js"/);
  assert.match(sw, /redauxilio-demo-v\d+/);
});
