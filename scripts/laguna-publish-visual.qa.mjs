"use strict";
import { chromium } from "playwright";
import assert from "node:assert/strict";
import fs from "node:fs/promises";

const scenarios = [
  { width: 360, height: 780, category: "ASSISTANCE" },
  { width: 390, height: 844, category: "ROAD" },
  { width: 768, height: 1000, category: "HELP_REQUEST" },
  { width: 1366, height: 880, category: "MISSING_PERSON" },
];
await fs.mkdir("laguna-visual-evidence", { recursive: true });
const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
const results = [];
try {
  for (const scenario of scenarios) {
    const page = await browser.newPage({
      viewport: { width: scenario.width, height: scenario.height },
      deviceScaleFactor: 1,
      reducedMotion: "reduce",
    });
    try {
      await page.goto("http://127.0.0.1:18080/#draft-section", {
        waitUntil: "domcontentloaded",
      });
      await page.waitForFunction(() => document.body.dataset.lagunaPage === "publish");
      const cards = page.locator('#draft-form [data-publish-step="0"] button[data-publish-category]');
      assert.equal(await cards.count(), 4);
      for (const image of await cards.locator("img").all()) {
        assert.equal(await image.isVisible(), true);
        assert.equal(
          await image.evaluate((element) => element.complete && element.naturalWidth > 0),
          true,
        );
      }
      await page.screenshot({
        path: `laguna-visual-evidence/${scenario.width}-categories.png`,
        fullPage: true,
      });
      await page
        .locator(`#draft-form [data-publish-step="0"] button[data-publish-category="${scenario.category}"]`)
        .click();
      assert.equal(await page.locator("#draft-form").getAttribute("data-publish-step"), "1");
      assert.equal(await page.locator(".publish-categories-inline button:visible").count(), 4);
      assert.equal(
        await page
          .locator(`.publish-categories-inline button[data-publish-category="${scenario.category}"]`)
          .getAttribute("aria-pressed"),
        "true",
      );
      await page.locator("#publication-title").fill("Punto de referencia de prueba");
      await page.locator("#draft-text").fill("Información ficticia para validar Publicar Laguna.");
      async function choose(id) {
        const button = page.locator(`#${id} + .publish-choice-options button`).first();
        assert.equal(await button.count(), 1, "Faltan botones para " + id);
        await button.click();
        assert.equal(await button.getAttribute("aria-pressed"), "true");
      }
      if (scenario.category === "ASSISTANCE") {
        await choose("assistance-subtype");
        await choose("assistance-status");
      } else if (scenario.category === "ROAD") {
        await choose("road-impact");
        await choose("road-status");
      } else if (scenario.category === "HELP_REQUEST") {
        await choose("help-need-type");
        await choose("help-urgency");
        await page.locator("#help-requester").fill("Comunidad de prueba");
      } else {
        await page.locator("#missing-seen").fill("2026-11-01T10:00");
      }
      await page.screenshot({
        path: `laguna-visual-evidence/${scenario.width}-fields.png`,
        fullPage: true,
      });
      await page.locator("#publish-next").click();
      assert.equal(
        await page.locator("#draft-form").getAttribute("data-publish-step"),
        "2",
        "No avanzó a Ubicación",
      );
      await page.locator("#publication-location").fill("Parque público de prueba");
      await page.locator("#publication-location-map").click({ position: { x: 110, y: 85 } });
      await page.screenshot({
        path: `laguna-visual-evidence/${scenario.width}-location.png`,
        fullPage: true,
      });
      await page.locator("#publish-next").click();
      assert.equal(
        await page.locator("#draft-form").getAttribute("data-publish-step"),
        "3",
        "No avanzó a Revisar",
      );
      const review = await page.locator(".publish-review").innerText();
      assert.match(review, /Parque público de prueba/);
      assert.match(review, /Información ficticia/);
      if (scenario.category === "ROAD") assert.match(review, /TRANSITABILIDAD/);
      if (scenario.category === "HELP_REQUEST") assert.match(review, /URGENCIA/);
      if (scenario.category === "MISSING_PERSON") assert.match(review, /ÚLTIMA VEZ VISTA/);
      await page.screenshot({
        path: `laguna-visual-evidence/${scenario.width}-review.png`,
        fullPage: true,
      });
      const contentWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      assert.ok(contentWidth <= scenario.width + 2, "Desbordamiento horizontal");
      results.push({ width: scenario.width, category: scenario.category, status: "PASS" });
    } finally {
      await page.close();
    }
  }
} finally {
  await browser.close();
}
await fs.writeFile("laguna-visual-evidence/results.json", JSON.stringify(results, null, 2));
console.log(JSON.stringify(results, null, 2));
