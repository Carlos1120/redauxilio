"use strict";

const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const { lagunaSectionFromHash, lagunaConnectionCopy } = require("../../main/resources/static/laguna-ui.js");

test("la barra inferior distingue Inicio, Explorar, Publicar y Cuenta", () => {
  assert.equal(lagunaSectionFromHash(""), "inicio");
  assert.equal(lagunaSectionFromHash("#laguna-home"), "inicio");
  assert.equal(lagunaSectionFromHash("#search-section"), "explorar");
  assert.equal(lagunaSectionFromHash("#publications"), "explorar");
  assert.equal(lagunaSectionFromHash("#draft-section"), "publicar");
  assert.equal(lagunaSectionFromHash("#own-section"), "cuenta");
});

test("el indicador no anuncia online cuando el dispositivo está desconectado", () => {
  assert.equal(lagunaConnectionCopy(true), "● En línea");
  assert.equal(lagunaConnectionCopy(false), "● Sin conexión");
});

test("no duplica identificadores consumidos por los módulos actuales", () => {
  const html = readFileSync(join(__dirname, "../../main/resources/templates/index.html"), "utf8");
  for (const id of [
    "identity-open",
    "search-query",
    "laguna-search-query",
    "laguna-account-mobile",
    "report-map",
    "draft-form",
  ]) {
    assert.equal((html.match(new RegExp('id="' + id + '"', "g")) || []).length, 1, id);
  }
  assert.match(html, /class="laguna-bottom-nav"/);
  assert.match(html, /data-laguna-category="ASSISTANCE"/);
});
