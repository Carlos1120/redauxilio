"use strict";
/**
 * RA-11/P2: mide el DOM real en navegador; no usa JSDOM ni simula la distribución CSS.
 * Entradas: page (Playwright o tab.playwright), setViewport({width,height}), onResult opcional.
 * Recorre cambios dinámicos de categoría en tres tamaños; no envía formularios ni altera datos.
 * Salida: resultados geométricos; rechaza si hay intersección, recorte o overflow horizontal.
 * Ejecutar con navegador local autorizado según VALIDACION_WINDOWS.md. No se incluye en npm test
 * porque requiere una instancia identificada y un navegador; no certifica PWA ni accesibilidad.
 */
export default async function publicationLayout({ page, setViewport, onResult = async () => {} }) {
  const results = [];
  for (const size of [
    { width: 360, height: 800 },
    { width: 768, height: 1024 },
    { width: 1366, height: 768 },
  ]) {
    await setViewport(size);
    // Repite HELP→ASSISTANCE para detectar también controles ocultos que mantengan geometría.
    for (const category of ["ASSISTANCE", "ROAD", "HELP_REQUEST", "ASSISTANCE"]) {
      await page
        .getByRole("combobox", { name: "Tipo de publicación", exact: true })
        .selectOption(category);
      const geometry = await page.evaluate(() => {
        const form = document.querySelector("#draft-form");
        const visible = (el) =>
          el.getClientRects().length > 0 && getComputedStyle(el).visibility !== "hidden";
        const box = (el) => {
          const r = el.getBoundingClientRect();
          return {
            id: el.id || el.className || el.tagName,
            left: r.left,
            right: r.right,
            top: r.top,
            bottom: r.bottom,
          };
        };
        const intersections = (elements) => {
          const overlaps = [];
          for (let i = 0; i < elements.length; i++)
            for (let j = i + 1; j < elements.length; j++) {
              const a = box(elements[i]),
                b = box(elements[j]);
              if (
                Math.min(a.right, b.right) - Math.max(a.left, b.left) > 1 &&
                Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 1
              )
                overlaps.push([a.id, b.id]);
            }
          return overlaps;
        };
        const blocks = [...form.children].filter(visible);
        const controls = [
          ...form.querySelectorAll("input:not([type=hidden]), select, textarea, button"),
        ].filter(visible);
        const clippedText = [...form.querySelectorAll("label, legend, p, small, button")]
          .filter(visible)
          .filter(
            (el) =>
              el.scrollWidth > el.clientWidth + 1 &&
              ["hidden", "clip"].includes(getComputedStyle(el).overflowX),
          )
          .map((el) => el.id || el.textContent.trim());
        const help = document.querySelector("#help-fields");
        const category = document.querySelector("#publication-category").value;
        const incorrectGroups = [
          ["assistance-fields", "ASSISTANCE"],
          ["road-fields", "ROAD"],
          ["help-fields", "HELP_REQUEST"],
        ]
          .filter(([id, type]) => {
            const group = document.getElementById(id);
            return (
              visible(group) !== (category === type) ||
              [...group.querySelectorAll("input, select, textarea")].some(
                (el) => el.disabled !== (category !== type),
              )
            );
          })
          .map(([id]) => id);
        return {
          width: innerWidth,
          scrollWidth: document.documentElement.scrollWidth,
          blocks: blocks.map(box),
          blockIntersections: intersections(blocks),
          controlIntersections: intersections(controls),
          clippedText,
          incorrectGroups,
          helpRow: getComputedStyle(help).gridRow,
        };
      });
      const result = {
        ...size,
        category,
        geometry,
        pass:
          geometry.scrollWidth <= geometry.width &&
          geometry.blockIntersections.length === 0 &&
          geometry.controlIntersections.length === 0 &&
          geometry.clippedText.length === 0 &&
          geometry.incorrectGroups.length === 0,
      };
      results.push(result);
      await onResult(result);
    }
  }
  if (results.some((result) => !result.pass)) {
    const error = new Error(
      "RA-11: geometría inválida en " +
        results
          .filter((result) => !result.pass)
          .map((result) => `${result.width}×${result.height}/${result.category}`)
          .join(", "),
    );
    error.results = results;
    throw error;
  }
  return results;
}
