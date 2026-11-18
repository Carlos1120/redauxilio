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

test("La barra inferior móvil expone cinco destinos y Cuenta una sola vez", () => {
  const block = html.match(/<nav class="laguna-bottom-nav"[\s\S]*?<\/nav>/)?.[0];
  assert.ok(block, "Se necesita navegación inferior");
  assert.deepEqual(
    [...block.matchAll(/data-laguna-tab="([^"]+)"/g)].map((m) => m[1]),
    ["home", "explore", "match", "publish", "account"],
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

test("la vista de exposición no muestra avisos ni créditos técnicos", () => {
  for (const text of [
    "Prototipo conceptual",
    "Proyecto académico",
    "DEMOSTRACIÓN · información comunitaria sin verificar",
    "Resultado orientativo y simulado",
    "Carlos Mario Peña y Santiago Ortiz Ochoa",
  ])
    assert.doesNotMatch(html, new RegExp(text));
  assert.doesNotMatch(html, /class="notice notice-demo"/);
  assert.doesNotMatch(html, /class="laguna-home-footer"/);
  assert.doesNotMatch(html, /class="missing-match-warning"/);
});

test("las categorías conservan pictogramas y acentos Laguna diferentes", () => {
  const assets = ["shelter", "road", "person", "help"].map((name) =>
    fs.readFileSync(path.join(root, `static/laguna-assets/map-${name}.svg`), "utf8"),
  );
  for (const name of ["shelter", "road", "person", "help"])
    assert.match(html, new RegExp(`/laguna-assets/map-${name}\\.svg`));
  assert.equal(new Set(assets).size, 4);
  assert.doesNotMatch(css, /content:\s*url\("\/laguna-assets\/desktop-map-/);
  assert.match(css, /\.report-marker--assistance\s*\{[\s\S]*?border-color:\s*#0c747c/);
  assert.match(css, /\.report-marker--road\s*\{[\s\S]*?border-color:\s*#b17a18/);
  assert.match(css, /\.report-marker--missing_person\s*\{[\s\S]*?border-color:\s*#766da6/);
  assert.match(css, /\.report-marker--help_request\s*\{[\s\S]*?border-color:\s*#c45d50/);
});

test("CSS respeta mobile/tablet, safe-area y las funciones del mapa", () => {
  assert.match(css, /@media \(max-width: 1099px\)/);
  assert.match(css, /@media \(max-width: 520px\)/);
  assert.match(css, /\.map-section \{/);
  assert.match(css, /\.draft-section form \{/);
  assert.match(css, /env\(safe-area-inset-bottom\)/);
});

test("las ondas del header recorren todo el ancho en escritorio", () => {
  assert.match(css, /\.site-header::after\s*\{\s*clip-path:\s*none;/);
  assert.doesNotMatch(css, /clip-path:\s*inset\(0 0 0 calc\(clamp/);
});

test("cada destino de escritorio contiene su propio indicador activo", () => {
  assert.match(css, /\.site-header \.header-content > nav a\s*\{[\s\S]*?position:\s*relative;/);
  assert.doesNotMatch(css, /nav a:nth-child\([^)]*\)\s*\{\s*position:\s*relative/);
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
  assert.match(sw, /"\/app-loader\.js"/);
  assert.match(sw, /"\/missing-person-match\.js"/);
  assert.match(html, /href="\/laguna\.css"/);
  assert.match(html, /src="\/app-loader\.js"/);
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
