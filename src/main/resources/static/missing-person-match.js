"use strict";
/**
 * Presenta la comparación académica sin inferir identidad ni persistir las fotos elegidas.
 * El servidor vuelve a validar formato, tamaño, sesión y coincidencia predispuesta.
 */
(() => {
  const section = document.getElementById("missing-match-section");
  const form = document.getElementById("missing-match-form");
  const input = document.getElementById("missing-match-files");
  const previews = document.getElementById("missing-match-previews");
  const candidates = document.getElementById("missing-match-candidates");
  const count = document.getElementById("missing-match-count");
  const submit = document.getElementById("missing-match-submit");
  const message = document.getElementById("missing-match-message");
  const result = document.getElementById("missing-match-result");
  const resultContent = document.getElementById("missing-match-result-content");
  const identity = document.getElementById("identity-open");
  if (!section || !form || !input || !previews || !candidates || !submit || !message) return;

  let catalogReady = false;
  let previewUrls = [];

  function clearPreviews() {
    for (const url of previewUrls) URL.revokeObjectURL(url);
    previewUrls = [];
    previews.replaceChildren();
  }

  function setMessage(copy, state = "") {
    message.textContent = copy;
    message.dataset.state = state;
  }

  function candidateCard(candidate) {
    const article = document.createElement("article");
    article.className = "missing-match-candidate";
    const image = document.createElement("img");
    image.src = candidate.photoUrl;
    image.alt = `Foto de referencia de ${candidate.referenceName}`;
    const body = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = candidate.referenceName;
    const meta = document.createElement("p");
    meta.textContent = candidate.ageRange || "Rango de edad no indicado";
    const description = document.createElement("p");
    description.textContent = candidate.description;
    const download = document.createElement("a");
    download.href = candidate.comparisonPhotoUrl;
    download.download = `foto-prueba-${candidate.id}`;
    download.textContent = "Descargar foto de prueba";
    body.append(title, meta, description, download);
    article.append(image, body);
    return article;
  }

  async function loadCandidates() {
    try {
      const response = await fetch("/api/missing-person-match/candidates", {
        headers: { Accept: "application/json" },
        cache: "no-store",
      });
      if (!response.ok) throw new Error();
      const items = await response.json();
      candidates.replaceChildren();
      catalogReady = Array.isArray(items) && items.length > 0;
      count.textContent = `${items.length} ${items.length === 1 ? "caso" : "casos"}`;
      if (!catalogReady) {
        const empty = document.createElement("p");
        empty.className = "missing-match-empty";
        empty.textContent =
          "El espacio está preparado. Agrega al manifiesto las fotos autorizadas para activar la comparación.";
        candidates.append(empty);
        submit.disabled = true;
        return;
      }
      for (const candidate of items) candidates.append(candidateCard(candidate));
      submit.disabled = input.files.length === 0;
      setMessage("Selecciona fotos para ejecutar la comparación.");
    } catch {
      candidates.replaceChildren();
      const empty = document.createElement("p");
      empty.className = "missing-match-empty";
      empty.textContent = "No fue posible consultar los casos preparados.";
      candidates.append(empty);
      submit.disabled = true;
      setMessage("La comparación no está disponible en este momento.", "error");
    }
  }

  input.addEventListener("change", () => {
    clearPreviews();
    const files = [...input.files];
    if (files.length > 3) {
      input.value = "";
      submit.disabled = true;
      setMessage("Selecciona máximo tres fotos.", "error");
      return;
    }
    for (const [index, file] of files.entries()) {
      const figure = document.createElement("figure");
      const image = document.createElement("img");
      const url = URL.createObjectURL(file);
      previewUrls.push(url);
      image.src = url;
      image.alt = `Vista previa de la foto ${index + 1}`;
      const caption = document.createElement("figcaption");
      caption.textContent = `Foto ${index + 1}`;
      figure.append(image, caption);
      previews.append(figure);
    }
    submit.disabled = !catalogReady || files.length === 0;
    if (files.length)
      setMessage(
        `${files.length} foto${files.length === 1 ? "" : "s"} lista${files.length === 1 ? "" : "s"} para comparar.`,
      );
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const files = [...input.files];
    if (!catalogReady || files.length < 1 || files.length > 3) return;
    submit.disabled = true;
    setMessage("Comparando con los casos preparados…", "loading");
    try {
      const csrfResponse = await fetch("/csrf", { cache: "no-store" });
      if (!csrfResponse.ok) throw new Error("csrf");
      const csrf = await csrfResponse.json();
      const data = new FormData();
      for (const file of files) data.append("files", file);
      const response = await fetch("/api/missing-person-match/simulate", {
        method: "POST",
        headers: { [csrf.headerName]: csrf.token, Accept: "application/json" },
        body: data,
      });
      if (response.status === 401 || response.status === 403) {
        identity?.click();
        setMessage("Inicia sesión con tu cuenta ciudadana para ejecutar la comparación.", "error");
        return;
      }
      const payload = await response.json().catch(() => ({}));
      if (!response.ok)
        throw new Error(payload.message || "No fue posible ejecutar la comparación.");
      renderResult(payload, files[0]);
      setMessage("Comparación completada.", "success");
    } catch (error) {
      setMessage(error.message || "No fue posible ejecutar la comparación.", "error");
    } finally {
      submit.disabled = !catalogReady || input.files.length === 0;
    }
  });

  function renderResult(payload, firstFile) {
    result.hidden = false;
    resultContent.replaceChildren();
    if (!payload.matched || !payload.candidate) {
      result.querySelector("h2").textContent = "Sin coincidencias";
      const copy = document.createElement("p");
      copy.className = "missing-match-no-result";
      copy.textContent = payload.message;
      resultContent.append(copy);
      result.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    result.querySelector("h2").textContent = "Posible coincidencia";
    const comparison = document.createElement("div");
    comparison.className = "missing-match-comparison";
    const uploaded = document.createElement("figure");
    const uploadedImage = document.createElement("img");
    const uploadedUrl = URL.createObjectURL(firstFile);
    previewUrls.push(uploadedUrl);
    uploadedImage.src = uploadedUrl;
    uploadedImage.alt = "Primera foto seleccionada para comparar";
    const uploadedCaption = document.createElement("figcaption");
    uploadedCaption.textContent = "Foto seleccionada";
    uploaded.append(uploadedImage, uploadedCaption);
    const reference = document.createElement("figure");
    const referenceImage = document.createElement("img");
    referenceImage.src = payload.candidate.photoUrl;
    referenceImage.alt = `Caso de referencia ${payload.candidate.referenceName}`;
    const referenceCaption = document.createElement("figcaption");
    referenceCaption.textContent = payload.candidate.referenceName;
    reference.append(referenceImage, referenceCaption);
    const summary = document.createElement("div");
    summary.className = "missing-match-summary";
    const score = document.createElement("strong");
    score.textContent = `${payload.candidate.simulatedScore}%`;
    const label = document.createElement("span");
    label.textContent = "posibilidad predispuesta";
    const disclaimer = document.createElement("p");
    disclaimer.textContent = payload.message;
    summary.append(score, label, disclaimer);
    comparison.append(uploaded, summary, reference);
    resultContent.append(comparison);
    result.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  window.addEventListener("pagehide", clearPreviews);
  loadCandidates();
})();
