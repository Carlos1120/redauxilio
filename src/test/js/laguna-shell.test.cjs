"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "../../main/resources");
const html = fs.readFileSync(path.join(root, "templates/index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "static/laguna.css"), "utf8");
const js = fs.readFileSync(path.join(root, "static/laguna-navigation.js"), "utf8");
const sw = fs.readFileSync(path.join(root, "static/sw.js"), "utf8");

test("Laguna conserva los ID de las funciones existentes", () => {
  for (const id of [
    "identity-open",
    "identity-dialog",
    "connection-indicator",
    "search-query",
    "draft-section",
    "own-section",
    "report-map",
  ]) {
    assert.match(html, new RegExp('id="' + id + '"'));
  }
});

test("La barra inferior móvil expone cuatro destinos y Cuenta una sola vez", () => {
  const block = html.match(/<nav class="laguna-bottom-nav"[\s\S]*?<\/nav>/)?.[0];
  assert.ok(block, "Se necesita navegación inferior");
  assert.deepEqual(
    [...block.matchAll(/data-laguna-tab="([^"]+)"/g)].map((m) => m[1]),
    ["home", "explore", "publish", "account"],
  );
  assert.equal((block.match(/id="laguna-mobile-account"/g) || []).length, 1);
  assert.match(css, /\.site-header \.identity-trigger\s*\{\s*display: none;\s*\}/);
  assert.match(js, /existingAccountTrigger\.click\(\)/);
});

test("Inicio conserva el texto de cada dispositivo y el escudo original", () => {
  assert.match(html, /Encuentra ayuda y comparte lo que sucede\./);
  assert.match(html, /Encuentra orientación y comparte lo que sucede\./);
  assert.match(
    html,
    /class="laguna-home-seal"[\s\S]*?src="\/laguna-assets\/desktop-footer-shield\.svg"/,
  );
});

test("CSS respeta mobile/tablet, safe-area y las funciones del mapa", () => {
  assert.match(css, /@media \(max-width: 1099px\)/);
  assert.match(css, /@media \(max-width: 520px\)/);
  assert.match(css, /\.map-section \{/);
  assert.match(css, /\.draft-section form \{/);
  assert.match(css, /env\(safe-area-inset-bottom\)/);
});

test("Publicar y Mis publicaciones conservan el ancho disponible en tablet", () => {
  assert.match(html, /id="draft-section" class="draft-section"/);
  assert.match(html, /id="own-section" class="draft-section"/);
  const sizing = /\.draft-section\s*\{\s*width:\s*auto;\s*margin:\s*24px 16px;/;
  assert.match(css, sizing);
});

test("La PWA precarga los nuevos recursos Laguna", () => {
  assert.match(sw, /"\/laguna\.css"/);
  assert.match(sw, /"\/laguna-navigation\.js"/);
  assert.match(html, /href="\/laguna\.css"/);
  assert.match(html, /src="\/laguna-navigation\.js"/);
});

test("Los recursos visuales públicos de Inicio existen y están disponibles sin red", () => {
  const usedAssets = new Set(
    [
      ...html.matchAll(/(?:src|srcset)="(\/laguna-assets\/[^" ]+)"/g),
      ...css.matchAll(/url\("(\/laguna-assets\/[^" ]+)"\)/g),
    ].map((match) => match[1]),
  );
  assert.ok(usedAssets.size > 10, "Inicio debe utilizar recursos originales locales");
  for (const asset of usedAssets) {
    assert.ok(fs.statSync(path.join(root, "static", asset.slice(1))).size > 0, asset);
    assert.ok(sw.includes(`"${asset}"`), `Falta ${asset} en la PWA`);
  }
});
