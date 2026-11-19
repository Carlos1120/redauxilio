"use strict";
/** Presenta el formulario existente en los tres pasos de Figma sin alterar sus datos ni la API. */
(() => {
  const form = document.getElementById("draft-form");
  const category = document.getElementById("publication-category");
  if (!form || !category) return;
  const cards = [...form.querySelectorAll("[data-publish-category]")];
  const next = document.getElementById("publish-next");
  const back = document.getElementById("publish-back");
  const save = document.getElementById("publish-save-draft");
  const review = form.querySelector(".publish-review");
  let step = 1;

  /** Recibe un paso válido y muestra solo sus campos; conserva los valores al volver. */
  function showStep(value) {
    step = Math.max(1, Math.min(3, value));
    form.dataset.publishStep = String(step);
    for (const item of document.querySelectorAll("[data-progress-step]")) {
      const number = Number(item.dataset.progressStep);
      item.dataset.active = String(number === step);
      item.dataset.complete = String(number < step);
      if (number === step) item.setAttribute("aria-current", "step");
      else item.removeAttribute("aria-current");
    }
    back.hidden = step === 1;
    next.hidden = step === 3;
    save.hidden = step === 3;
    if (step === 3) {
      const title = document.getElementById("publication-title").value.trim();
      const location = document.getElementById("publication-location").value.trim();
      const description = document.getElementById("draft-text").value.trim();
      review.replaceChildren();
      for (const [label, value] of [
        ["Tipo", category.selectedOptions[0]?.textContent || ""],
        ["Título", title],
        ["Lugar", location],
        ["Descripción", description],
      ]) {
        const row = document.createElement("p");
        const strong = document.createElement("strong");
        strong.textContent = `${label}: `;
        row.append(strong, document.createTextNode(value || "Pendiente"));
        review.append(row);
      }
    }
    if (step === 2) window.RedAuxilioPublicationUI?.refreshLocationPreview?.();
  }

  function syncCategory() {
    for (const card of cards)
      card.setAttribute("aria-pressed", String(card.dataset.publishCategory === category.value));
  }
  for (const card of cards)
    card.addEventListener("click", () => {
      category.value = card.dataset.publishCategory;
      category.dispatchEvent(new Event("change", { bubbles: true }));
      syncCategory();
    });
  category.addEventListener("change", syncCategory);
  next.addEventListener("click", () => showStep(step + 1));
  back.addEventListener("click", () => showStep(step - 1));
  save.addEventListener("click", () => form.requestSubmit());
  syncCategory();
  showStep(1);
})();
