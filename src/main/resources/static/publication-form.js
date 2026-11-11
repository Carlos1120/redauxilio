"use strict";
/**
 * Publica puntos/vías desde el borrador existente. Los tres métodos de ubicación solo proponen
 * coordenadas: siempre se pueden corregir antes del POST autenticado y protegido por CSRF.
 */
const publicationField = (id) => document.getElementById(id);
const publicationUI = window.RedAuxilioPublicationUI;
const draftForm = publicationField("draft-form");
const publicationCategory = publicationField("publication-category");
const publicationMessage = publicationField("draft-message");
const locationMessage = publicationField("location-message");
const evidenceInput = publicationField("publication-evidence");
const evidencePreview = publicationField("evidence-preview");
let duplicateFingerprint = null;
let duplicateGeneration = 0;
/** Invalida avisos y respuestas pendientes ante cambios manuales o propuestas de ubicacion. */
function invalidateDuplicateDecision() {
  duplicateGeneration++;
  duplicateFingerprint = null;
  publicationField("duplicate-justification").value = "";
  publicationField("duplicate-warning").hidden = true;
}
draftForm.addEventListener("input", (event) => {
  if (event.target.id !== "duplicate-justification") invalidateDuplicateDecision();
});
let evidenceFiles = [];
let evidenceObjectUrls = [];

/** Mantiene imágenes locales en el borrador sin enviarlas hasta guardar la publicación. */
window.RedAuxilioEvidenceDraft = {
  serialize: () => evidenceFiles,
  clear() {
    evidenceFiles = [];
    renderEvidencePreview();
  },
  restore(files) {
    evidenceFiles = files.map((file) => new File([file], file.name, { type: file.type }));
    renderEvidencePreview();
  },
};

function renderEvidencePreview() {
  evidenceObjectUrls.forEach(URL.revokeObjectURL);
  evidenceObjectUrls = [];
  evidencePreview.replaceChildren();
  evidenceFiles.forEach((file, index) => {
    const figure = document.createElement("figure");
    const image = document.createElement("img");
    const caption = document.createElement("figcaption");
    const remove = document.createElement("button");
    image.src = URL.createObjectURL(file);
    evidenceObjectUrls.push(image.src);
    image.alt = `Vista previa de evidencia ${index + 1}`;
    caption.textContent = `${file.name} · ${(file.size / 1024 / 1024).toFixed(2)} MB`;
    remove.type = "button";
    remove.className = "secondary-button";
    remove.textContent = "Quitar";
    remove.setAttribute("aria-label", `Quitar ${file.name}`);
    remove.addEventListener("click", () => {
      evidenceFiles.splice(index, 1);
      renderEvidencePreview();
    });
    figure.append(image, caption, remove);
    evidencePreview.append(figure);
  });
}

evidenceInput.addEventListener("change", () => {
  const additions = [...evidenceInput.files];
  evidenceInput.value = "";
  if (evidenceFiles.length + additions.length > 3) {
    publicationMessage.textContent = "Puedes adjuntar máximo tres imágenes.";
    return;
  }
  const accepted = additions.every(
    (file) =>
      ["image/jpeg", "image/png", "image/webp"].includes(file.type) &&
      file.size > 0 &&
      file.size <= 5 * 1024 * 1024,
  );
  if (!accepted) {
    publicationMessage.textContent = "Usa imágenes JPEG, PNG o WebP de hasta 5 MB cada una.";
    return;
  }
  evidenceFiles.push(...additions);
  publicationMessage.textContent = "Imágenes listas en la vista previa; aún no se han publicado.";
  renderEvidencePreview();
});

function syncPublicationCategory() {
  const category = publicationCategory.value;
  for (const [id, active] of [
    ["assistance-fields", category === "ASSISTANCE"],
    ["road-fields", category === "ROAD"],
    ["help-fields", category === "HELP_REQUEST"],
    ["missing-fields", category === "MISSING_PERSON"],
  ]) {
    const group = publicationField(id);
    group.hidden = !active;
    for (const field of group.querySelectorAll("input, select, textarea")) field.disabled = !active;
  }
  publicationField("draft-text").maxLength = category === "MISSING_PERSON" ? 500 : 1000;
  publicationField("draft-text").labels[0].textContent =
    category === "MISSING_PERSON"
      ? "Descripción física (sin datos sensibles)"
      : "¿Qué quieres reportar?";
  publicationField("publication-location").labels[0].textContent =
    category === "MISSING_PERSON"
      ? "Último sector o lugar público conocido"
      : "Sector o lugar público";
  publicationField("publication-title").labels[0].textContent =
    category === "MISSING_PERSON"
      ? "Nombre o referencia de la persona"
      : category === "ROAD"
        ? "Tramo de la vía"
        : category === "HELP_REQUEST"
          ? "Resumen de la necesidad"
          : "Nombre del punto de asistencia";
}
publicationCategory.addEventListener("change", syncPublicationCategory);
syncPublicationCategory();

/** Actualiza solo la propuesta de ubicación; no publica ni guarda automáticamente. */
function proposeLocation(latitude, longitude, label) {
  invalidateDuplicateDecision();
  publicationField("latitude").value = Number(latitude).toFixed(6);
  publicationField("longitude").value = Number(longitude).toFixed(6);
  if (label) publicationField("publication-location").value = label.slice(0, 160);
  locationMessage.textContent = "Ubicación propuesta. Corrige las coordenadas si hace falta.";
  publicationUI.reportMap?.focusOn(latitude, longitude);
}

publicationField("select-location").disabled = !publicationUI.reportMap;
publicationField("select-location").addEventListener("click", () => {
  locationMessage.textContent =
    "Haz clic en el mapa para proponer un punto; también puedes escribir coordenadas.";
  publicationUI.reportMap?.selectLocation((latitude, longitude) =>
    proposeLocation(
      latitude,
      longitude,
      publicationField("publication-location").value || "Punto seleccionado",
    ),
  );
});

publicationField("use-device-location").addEventListener("click", () => {
  if (!navigator.geolocation) {
    locationMessage.textContent =
      "Este navegador no ofrece ubicación. Señala el mapa o escribe coordenadas.";
    return;
  }
  locationMessage.textContent = "Solicitando permiso de ubicación…";
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => proposeLocation(coords.latitude, coords.longitude, "Ubicación seleccionada"),
    () => {
      locationMessage.textContent =
        "No se obtuvo la ubicación. Puedes señalar el mapa o escribir coordenadas.";
    },
    { enableHighAccuracy: false, timeout: 10000, maximumAge: 0 },
  );
});

publicationField("search-place").addEventListener("click", async () => {
  const query = publicationField("place-query").value.trim();
  const results = publicationField("place-results");
  results.replaceChildren();
  if (query.length < 3 || query.length > 160) {
    locationMessage.textContent = "Escribe entre 3 y 160 caracteres para buscar.";
    return;
  }
  const button = publicationField("search-place");
  button.disabled = true;
  locationMessage.textContent = "Buscando lugares…";
  try {
    const response = await fetch(`/api/places?q=${encodeURIComponent(query)}`, {
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (response.status === 401) {
      locationMessage.textContent =
        "Inicia sesión para buscar; la selección manual sigue disponible.";
      return;
    }
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "Búsqueda no disponible.");
    for (const place of data) {
      const item = document.createElement("li");
      const choose = document.createElement("button");
      choose.type = "button";
      choose.className = "secondary-button";
      choose.textContent = place.label;
      choose.addEventListener("click", () =>
        proposeLocation(place.latitude, place.longitude, place.label),
      );
      item.append(choose);
      results.append(item);
    }
    locationMessage.textContent = data.length
      ? "Selecciona un resultado y corrige el punto si hace falta. Datos: OpenStreetMap/Nominatim."
      : "No hubo resultados. Señala el mapa o escribe coordenadas.";
  } catch (error) {
    locationMessage.textContent = `${error.message || "Búsqueda no disponible."} Señala el mapa o escribe coordenadas.`;
  } finally {
    button.disabled = false;
  }
});

/** Confirma éxito únicamente al recibir 201 del servidor; un fallo conserva el borrador. */
publicationField("publish-report").addEventListener("click", async () => {
  const button = publicationField("publish-report");
  if (publicationField("identity-open").dataset.authenticated !== "true") {
    publicationMessage.textContent =
      "Inicia sesión para publicar. Tu borrador permanece en este navegador.";
    publicationField("identity-open").click();
    return;
  }
  const title = publicationField("publication-title").value.trim();
  const description = publicationField("draft-text").value.trim();
  const location = publicationField("publication-location").value.trim();
  const latitudeText = publicationField("latitude").value.trim();
  const longitudeText = publicationField("longitude").value.trim();
  if (
    !title ||
    !description ||
    !location ||
    !latitudeText ||
    !longitudeText ||
    !draftForm.checkValidity()
  ) {
    publicationMessage.textContent =
      "Completa nombre o tramo, descripción, lugar y coordenadas válidas.";
    draftForm.reportValidity();
    return;
  }
  const road = publicationCategory.value === "ROAD";
  const help = publicationCategory.value === "HELP_REQUEST";
  const missing = publicationCategory.value === "MISSING_PERSON";
  const reference = publicationField("help-requester").value.trim();
  if (help && (reference.includes("@") || /(?:[0-9][\s().+-]*){7,}/.test(reference))) {
    publicationMessage.textContent = "Usa un alias o comunidad sin correo, teléfono ni documento.";
    return;
  }
  const request = {
    category: publicationCategory.value,
    assistanceSubtype:
      road || help || missing ? null : publicationField("assistance-subtype").value,
    title,
    description,
    location,
    operationalStatus: missing
      ? "DESAPARECIDA"
      : help
        ? "ABIERTA"
        : road
          ? publicationField("road-status").value
          : publicationField("assistance-status").value,
    impactType: road ? publicationField("road-impact").value : null,
    latitude: Number(latitudeText),
    longitude: Number(longitudeText),
    ...(missing
      ? {
          missingPerson: {
            referenceName: title,
            physicalDescription: description,
            lastSeenPlace: location,
            ageRange: publicationField("missing-age").value || null,
            lastSeenAt: window.RedAuxilioMissingPerson.readSeenTime(
              publicationField("missing-seen"),
            ),
          },
        }
      : {}),
    ...(help
      ? {
          needType: publicationField("help-need-type").value,
          approximateQuantity: publicationField("help-quantity").value.trim() || null,
          declaredUrgency: publicationField("help-urgency").value,
          requesterReference: reference,
        }
      : {}),
  };
  const checkedGeneration = duplicateGeneration;
  button.disabled = true;
  publicationMessage.textContent = "Enviando publicación…";
  let draftSaved = false;
  try {
    await publicationUI.saveDraft();
    draftSaved = true;
    const tokenResponse = await fetch("/csrf", {
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!tokenResponse.ok) throw new Error("No se pudo validar la sesión. Intenta de nuevo.");
    const csrf = await tokenResponse.json();
    const headers = { Accept: "application/json", "X-CSRF-TOKEN": csrf.token };
    const fingerprint = JSON.stringify(request);
    const check = await fetch("/api/my/publications/possible-duplicates", {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: { ...headers, "Content-Type": "application/json" },
      body: fingerprint,
    });
    if (!check.ok)
      throw new Error(
        "No se pudieron comprobar posibles duplicados. Intenta de nuevo con conexión.",
      );
    const candidates = await check.json();
    if (checkedGeneration !== duplicateGeneration)
      throw new Error("Los datos cambiaron. Revisa y vuelve a publicar.");
    if (!Array.isArray(candidates)) throw new Error("Respuesta de duplicados no válida.");
    if (candidates.length) {
      const list = publicationField("duplicate-candidates");
      list.replaceChildren();
      for (const candidate of candidates) {
        const entry = document.createElement("li");
        const link = document.createElement("a");
        link.textContent = `${candidate.title} · ${candidate.location}${candidate.lastSeenAt ? " · Última vez vista: " + candidate.lastSeenAt : ""}`;
        link.href = `/?publication=${Number(candidate.id)}`;
        link.target = "_blank";
        link.rel = "noopener";
        entry.append(link);
        list.append(entry);
      }
      publicationField("duplicate-warning").hidden = false;
      const reason = publicationField("duplicate-justification").value.trim();
      if (duplicateFingerprint !== fingerprint || !reason) {
        duplicateFingerprint = fingerprint;
        publicationMessage.textContent =
          "Posibles duplicados encontrados. Consulta los casos y justifica si necesitas publicar otro.";
        publicationField("duplicate-justification").focus();
        return;
      }
      request.duplicateJustification = reason;
    }

    let endpoint = "/api/publications";
    let body = JSON.stringify(request);
    if (evidenceFiles.length) {
      endpoint = "/api/publications/with-evidence";
      body = new FormData();
      body.append("publication", new Blob([JSON.stringify(request)], { type: "application/json" }));
      evidenceFiles.forEach((file) => body.append("files", file, file.name));
    } else {
      headers["Content-Type"] = "application/json";
    }
    const response = await fetch(endpoint, {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers,
      body,
    });
    if (response.status === 401) throw new Error("La sesión terminó. Inicia sesión otra vez.");
    const result = await response.json().catch(() => ({}));
    if (response.status !== 201)
      throw new Error(
        result.message ||
          (response.status === 403
            ? "No se pudo validar la sesión. Recarga la página e intenta de nuevo."
            : "No se pudo publicar."),
      );
    let draftWarning = "";
    try {
      await publicationUI.clearDraft();
    } catch {
      draftWarning = " El borrador local no pudo borrarse; descártalo antes de crear otro.";
    }
    draftForm.reset();
    window.RedAuxilioEvidenceDraft.clear();
    syncPublicationCategory();
    publicationUI.reportMap?.focusOn(result.latitude, result.longitude);
    try {
      await publicationUI.loadPublications();
    } catch {
      // El servidor ya confirmó la creación: un fallo al refrescar no cambia ese resultado.
    }
    publicationMessage.textContent = `Publicación creada. Si no aparece en la lista, limpia los filtros.${draftWarning}`;
  } catch (error) {
    publicationMessage.textContent =
      `${error.message || "No se pudo publicar."} ` +
      (draftSaved
        ? "El borrador local se conserva."
        : "Los campos siguen en pantalla, pero no se pudo guardar el borrador local.");
  } finally {
    button.disabled = false;
  }
});
