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
  const requiredChoices = {
    ASSISTANCE: ["assistance-subtype", "assistance-status"],
    ROAD: ["road-impact", "road-status"],
    HELP_REQUEST: ["help-need-type", "help-urgency"],
    MISSING_PERSON: [],
  };
  const groups = new Map();
  /* La selección accesible actualiza el mismo select que usa la petición al servidor. */
  for (const id of [...new Set(Object.values(requiredChoices).flat())]) {
    const select = document.getElementById(id);
    const holder = document.createElement("div");
    holder.className = "publish-choice-options";
    holder.setAttribute("role", "group");
    holder.setAttribute("aria-label", select.labels?.[0]?.textContent || id);
    select.classList.add("publish-choice-native");
    select.tabIndex = -1;
    select.setAttribute("aria-hidden", "true");
    for (const option of select.options) {
      if (!option.value) continue;
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = option.textContent.toLocaleLowerCase("es").replace(/^./u, x => x.toLocaleUpperCase("es"));
      button.dataset.choiceValue = option.value;
      button.setAttribute("aria-pressed", "false");
      button.addEventListener("click", () => {
        select.value = option.value;
        select.dispatchEvent(new Event("change", { bubbles: true }));
        refreshChoices();
      });
      holder.append(button);
    }
    select.after(holder);
    groups.set(id, holder);
  }
  function refreshChoices() {
    for (const [id, holder] of groups)
      for (const button of holder.querySelectorAll("button"))
        button.setAttribute("aria-pressed", String(button.dataset.choiceValue === document.getElementById(id).value));
  }
  function validateChoice() {
    for (const id of requiredChoices[category.value] || []) {
      if (document.getElementById(id).value) continue;
      stepError.textContent = "Selecciona una respuesta para " + document.querySelector('label[for="' + id + '"]').textContent + ".";
      stepError.hidden = false;
      groups.get(id)?.querySelector("button")?.focus();
      return false;
    }
    return true;
  }

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
      review.replaceChildren();
      const heading = document.createElement("h4");
      heading.textContent = "Así aparecerá tu reporte";
      review.append(heading);
      const value = (id) => document.getElementById(id).value.trim();
      const selected = (id) => document.getElementById(id).selectedOptions[0]?.textContent?.trim() || "";
      const rows = [
        ["CATEGORÍA", selected("publication-category")],
        ["IDENTIFICACIÓN", value("publication-title")],
        ["INFORMACIÓN", value("draft-text")],
        ["UBICACIÓN", value("publication-location")],
      ];
      if (category.value === "ASSISTANCE") rows.push(["TIPO DE PUNTO", selected("assistance-subtype")], ["DISPONIBILIDAD DECLARADA", selected("assistance-status")]);
      if (category.value === "ROAD") rows.push(["AFECTACIÓN", selected("road-impact")], ["TRANSITABILIDAD DECLARADA", selected("road-status")]);
      if (category.value === "HELP_REQUEST") {
        rows.push(["NECESIDAD", selected("help-need-type")], ["URGENCIA DECLARADA (NO OFICIAL)", selected("help-urgency")], ["ALIAS O COMUNIDAD", value("help-requester")]);
        if (value("help-quantity")) rows.push(["CANTIDAD APROXIMADA", value("help-quantity")]);
      }
      if (category.value === "MISSING_PERSON") {
        rows.push(["ÚLTIMA VEZ VISTA", value("missing-seen").replace("T", " ")]);
        if (value("missing-age")) rows.push(["RANGO DE EDAD", value("missing-age")]);
      }
      for (const [label, data] of rows) {
        const row = document.createElement("div");
        const caption = document.createElement("small");
        const content = document.createElement("p");
        caption.textContent = label;
        content.textContent = data || "Pendiente";
        row.append(caption, content);
        review.append(row);
      }
      for (const [label, target] of [["Corregir datos", 1], ["Corregir ubicación", 2]]) {
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "✎ " + label;
        edit.addEventListener("click", () => showStep(target));
        review.append(edit);
      }
    }
    if (step === 2) window.RedAuxilioPublicationUI?.refreshLocationPreview?.();
    syncCategory();
    // No desplazar automáticamente: la cabecera y el progreso conservan su posición.
  }

  function syncCategory() {
    form.dataset.publishCategory = category.value;
    const description = document.getElementById("draft-text");
    if (category.value === "ASSISTANCE" || category.value === "ROAD") {
      description.labels[0].textContent = "¿Qué sabes sobre el lugar?";
      description.placeholder =
        "Ej.: Describe lo que viste y cualquier dato útil para la comunidad.";
    }
    for (const card of cards)
      card.setAttribute(
        "aria-pressed",
        String(step > 0 && card.dataset.publishCategory === category.value),
      );
    refreshChoices();
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
        stepError.textContent = "Completa la identificación y la descripción antes de continuar.";
        stepError.hidden = false;
        missing.focus();
        return;
      }
      if (!validateChoice()) return;
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
  save.addEventListener("click", () => {
    form.requestSubmit();
  });
  window.RedAuxilioPublishSteps = { reset: () => showStep(0), refresh: refreshChoices };
  window.addEventListener("redauxilio:draft-restored", refreshChoices);
  showStep(0);
  syncCategory();
})();
