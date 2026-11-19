"use strict";
/** Presenta las pantallas Laguna y conserva los campos necesarios para la API de publicaciones. */
(() => {
  const form = document.getElementById("draft-form");
  const category = document.getElementById("publication-category");
  if (!form || !category) return;
  const cards = [...form.querySelectorAll("[data-publish-category]")];
  const next = document.getElementById("publish-next");
  const back = document.getElementById("publish-back");
  const save = document.getElementById("publish-save-draft");
  const stepError = document.getElementById("publish-step-error");
  const review = form.querySelector(".publish-review");
  const pageTitle = document.getElementById("draft-title");
  const pageHelp = document.getElementById("draft-help");
  const progress = document.querySelector(".publish-progress");
  const mobile = window.matchMedia("(max-width: 800px)");
  let step = 0;

  /** Recibe un paso válido y muestra solo sus campos; conserva los valores al volver. */
  function showStep(value) {
    step = Math.max(0, Math.min(3, value));
    form.dataset.publishStep = String(step);
    stepError.hidden = true;
    pageTitle.textContent = mobile.matches
      ? "Publicar un reporte"
      : step === 0
        ? "Publica información que ayude"
        : "Comparte información que ayude";
    pageHelp.textContent = mobile.matches
      ? "Te guiamos paso a paso"
      : step === 0
        ? "Primero elige el tipo de reporte. Después te guiamos paso a paso."
        : "Solo preguntaremos lo necesario. Podrás revisar todo antes de publicar.";
    progress.dataset.current = String(Math.max(1, step));
    form.querySelector('[data-publish-step="2"] h3').textContent = mobile.matches
      ? "¿Dónde ocurrió?"
      : "Ubica tu publicación en el mapa";
    form.querySelector('[data-publish-step="2"] p').textContent = mobile.matches
      ? "Elige un lugar sin escribir coordenadas."
      : "Elige un lugar aproximado. No es necesario compartir una dirección privada.";
    form.querySelector('[data-publish-step="3"] h3').textContent = mobile.matches
      ? "¿Está todo bien?"
      : "Revisa la información";
    form.querySelector('[data-publish-step="3"] p').textContent = mobile.matches
      ? "Revisa tu reporte antes de enviarlo."
      : "Puedes volver a cualquier paso para corregir algo.";
    form.querySelector('[data-publish-step="1"] p').textContent = mobile.matches
      ? "Selecciona una opción y cuéntanos lo esencial."
      : "Selecciona una opción. Después cuéntanos lo que sabes.";
    const placeLabel = form.querySelector('label[for="publication-location"]');
    placeLabel.textContent = mobile.matches
      ? "¿En qué lugar ocurrió?"
      : category.value === "MISSING_PERSON"
        ? "Último sector o lugar público conocido"
        : "Sector o lugar público";
    document.getElementById("publication-location").placeholder = mobile.matches
      ? "⌕ Barrio, parque o sitio conocido"
      : "";
    for (const item of document.querySelectorAll("[data-progress-step]")) {
      const number = Number(item.dataset.progressStep);
      item.lastChild.textContent =
        step === 0
          ? [" Datos", " Ubicación", " Revisar"][number - 1]
          : [" Contenido", " Ubicación", " Revisión"][number - 1];
      item.dataset.active = String(number === Math.max(1, step));
      item.dataset.complete = String(number < step);
      if (number === Math.max(1, step)) item.setAttribute("aria-current", "step");
      else item.removeAttribute("aria-current");
    }
    back.hidden = step === 0;
    next.hidden = step === 0 || step === 3;
    save.hidden = step === 0 || step === 3;
    if (step === 3) {
      const location = document.getElementById("publication-location").value.trim();
      const description = document.getElementById("draft-text").value.trim();
      review.replaceChildren();
      const heading = document.createElement("h4");
      heading.textContent = "Tu reporte";
      review.append(heading);
      for (const [label, value] of [
        ["TIPO", category.selectedOptions[0]?.textContent || ""],
        ["LUGAR", location],
        ["INFORMACIÓN", description],
      ]) {
        const row = document.createElement("div");
        const caption = document.createElement("small");
        const content = document.createElement("p");
        caption.textContent = label;
        content.textContent = value || "Pendiente";
        row.append(caption, content);
        review.append(row);
      }
      const edit = document.createElement("button");
      edit.type = "button";
      edit.textContent = "✎ Cambiar información";
      edit.addEventListener("click", () => showStep(1));
      review.append(edit);
    }
    if (step === 2) window.RedAuxilioPublicationUI?.refreshLocationPreview?.();
    syncCategory();
    if (step > 0) document.getElementById("draft-section")?.scrollIntoView({ block: "start" });
  }

  function syncCategory() {
    for (const card of cards)
      card.setAttribute(
        "aria-pressed",
        String(step > 0 && card.dataset.publishCategory === category.value),
      );
  }
  for (const card of cards)
    card.addEventListener("click", () => {
      category.value = card.dataset.publishCategory;
      category.dispatchEvent(new Event("change", { bubbles: true }));
      showStep(1);
      syncCategory();
    });
  category.addEventListener("change", syncCategory);
  mobile.addEventListener("change", () => showStep(step));
  next.addEventListener("click", () => {
    if (step === 1) {
      const title = document.getElementById("publication-title");
      const description = document.getElementById("draft-text");
      const missing = [title, description].find((field) => !field.value.trim());
      if (missing) {
        stepError.textContent = "Completa el nombre o tramo y la descripción antes de continuar.";
        stepError.hidden = false;
        missing.focus();
        return;
      }
      const specific = form.querySelector(
        category.value === "HELP_REQUEST"
          ? "#help-fields"
          : category.value === "MISSING_PERSON"
            ? "#missing-fields"
            : "#publication-category",
      );
      if (!specific.checkValidity()) {
        specific.reportValidity();
        return;
      }
    }
    if (step === 2) {
      const required = ["publication-location", "latitude", "longitude"].map((id) =>
        document.getElementById(id),
      );
      const missing = required.find((field) => !field.value.trim() || !field.checkValidity());
      if (missing) {
        stepError.textContent = "Indica el lugar y marca un punto en el mapa antes de revisar.";
        stepError.hidden = false;
        missing.focus();
        return;
      }
    }
    showStep(step + 1);
  });
  back.addEventListener("click", () => showStep(step - 1));
  save.addEventListener("click", () => form.requestSubmit());
  showStep(0);
  syncCategory();
})();
