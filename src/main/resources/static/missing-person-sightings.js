"use strict";
/** Presenta el historial y permite añadir observaciones sin cambiar el estado del caso. */
(() => {
  let renderVersion = 0;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }

  function field(labelText, input) {
    const wrapper = element("div", "field");
    const label = element("label", "", labelText);
    label.htmlFor = input.id;
    wrapper.append(label, input);
    return wrapper;
  }

  function dateTimeValue(date) {
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }

  function render(item, container) {
    if (item.category !== "MISSING_PERSON" || item.id < 1000000) return;
    const version = ++renderVersion;
    const section = element("section", "sighting-section");
    section.dataset.sightingCase = String(item.id);
    section.append(
      element("h3", "", "Avistamientos"),
      element(
        "p",
        "sighting-guidance",
        "Son observaciones ciudadanas posibles. No confirman el caso ni cambian su estado.",
      ),
    );
    const history = element("div", "sighting-list");
    history.setAttribute("aria-live", "polite");
    history.append(element("p", "sighting-message", "Cargando historial…"));
    section.append(history);
    container.append(section);

    loadHistory(item.id, version, history);
    if (item.operationalStatus === "DESAPARECIDA" && !item.closed) {
      if (document.getElementById("identity-open")?.dataset.authenticated === "true")
        mountForm(item.id, version, section, history);
      else {
        const access = element(
          "button",
          "secondary-button",
          "Inicia sesión para aportar un avistamiento",
        );
        access.type = "button";
        access.addEventListener("click", () => document.getElementById("identity-open")?.click());
        section.append(access);
      }
    } else {
      section.append(
        element(
          "p",
          "sighting-guidance",
          "El caso no admite nuevos avistamientos en su estado actual.",
        ),
      );
    }
  }

  async function loadHistory(publicationId, version, history, offset = 0) {
    try {
      const response = await fetch(
        `/api/publications/${publicationId}/sightings?limit=20&offset=${offset}`,
        {
          cache: "no-store",
          headers: { Accept: "application/json" },
        },
      );
      if (!response.ok) throw new Error("No se pudo consultar el historial.");
      const sightings = await response.json();
      if (version !== renderVersion) return;
      if (offset === 0) history.replaceChildren();
      if (!Array.isArray(sightings) || sightings.length === 0) {
        history.append(element("p", "sighting-message", "Aún no hay avistamientos registrados."));
        return;
      }
      for (const sighting of sightings) {
        const entry = element("article", "sighting-entry");
        entry.append(
          element(
            "h4",
            "",
            `Avistamiento #${sighting.id} · ${new Date(sighting.observedAt).toLocaleString("es-CO")}`,
          ),
          element("p", "", sighting.location),
          element("p", "", sighting.description),
          element(
            "small",
            "sighting-coordinates",
            `Ubicación aproximada: ${Number(sighting.latitude).toFixed(3)}, ${Number(sighting.longitude).toFixed(3)}`,
          ),
        );
        for (const [index, image] of (sighting.evidence || []).entries()) {
          const figure = element("figure", "sighting-image");
          const photo = element("img");
          photo.src = image.url;
          photo.alt = `Fotografía del avistamiento ${index + 1}`;
          photo.loading = "lazy";
          figure.append(photo);
          entry.append(figure);
        }
        history.append(entry);
      }
      // Solo se solicita una pagina acotada por accion; se preservan las entradas anteriores.
      if (sightings.length === 20) {
        const more = element("button", "secondary-button", "Cargar mas avistamientos");
        more.type = "button";
        more.addEventListener("click", async () => {
          more.disabled = true;
          await loadHistory(publicationId, version, history, offset + sightings.length);
          more.remove();
        });
        history.append(more);
      }
    } catch {
      if (version === renderVersion)
        history.replaceChildren(
          element(
            "p",
            "sighting-message",
            "Historial no disponible. Comprueba la conexión e inténtalo de nuevo.",
          ),
        );
    }
  }

  function mountForm(publicationId, version, section, history) {
    const form = element("form", "sighting-form");
    form.setAttribute("aria-describedby", `sighting-help-${publicationId}`);
    const help = element(
      "p",
      "sighting-guidance",
      "Usa un sector público y una ubicación aproximada. No incluyas datos de contacto, domicilios ni información médica.",
    );
    help.id = `sighting-help-${publicationId}`;
    const observedAt = element("input");
    observedAt.type = "datetime-local";
    observedAt.required = true;
    observedAt.id = `sighting-time-${publicationId}`;
    observedAt.value = dateTimeValue(new Date());
    const location = element("input");
    location.type = "text";
    location.required = true;
    location.maxLength = 160;
    location.id = `sighting-location-${publicationId}`;
    const coordinates = element("div", "sighting-coordinates-fields");
    const latitude = element("input");
    latitude.type = "number";
    latitude.required = true;
    latitude.min = "-90";
    latitude.max = "90";
    latitude.step = "0.001";
    latitude.id = `sighting-latitude-${publicationId}`;
    const longitude = element("input");
    longitude.type = "number";
    longitude.required = true;
    longitude.min = "-180";
    longitude.max = "180";
    longitude.step = "0.001";
    longitude.id = `sighting-longitude-${publicationId}`;
    coordinates.append(
      field("Latitud aproximada", latitude),
      field("Longitud aproximada", longitude),
    );
    const description = element("textarea");
    description.required = true;
    description.maxLength = 1000;
    description.id = `sighting-description-${publicationId}`;
    const files = element("input");
    files.type = "file";
    files.accept = "image/jpeg,image/png,image/webp";
    files.multiple = true;
    files.id = `sighting-files-${publicationId}`;
    const status = element("p", "sighting-message", "");
    status.setAttribute("role", "status");
    const submit = element("button", "header-action", "Registrar avistamiento");
    submit.type = "submit";
    form.append(
      element("h4", "", "Aportar una observación"),
      help,
      field("Fecha y hora observadas", observedAt),
      field("Sector o lugar público", location),
      coordinates,
      field("Descripción", description),
      field("Fotografías opcionales (hasta tres)", files),
      status,
      submit,
    );
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (files.files.length > 3) {
        status.textContent = "Adjunta máximo tres fotografías.";
        return;
      }
      submit.disabled = true;
      status.textContent = "Enviando al servidor…";
      try {
        const tokenResponse = await fetch("/csrf", {
          credentials: "same-origin",
          cache: "no-store",
        });
        if (!tokenResponse.ok) throw new Error("No se pudo renovar la protección de la sesión.");
        const token = await tokenResponse.json();
        const data = new FormData();
        data.append(
          "sighting",
          new Blob(
            [
              JSON.stringify({
                observedAt: new Date(observedAt.value).toISOString(),
                location: location.value,
                latitude: Number(latitude.value),
                longitude: Number(longitude.value),
                description: description.value,
              }),
            ],
            { type: "application/json" },
          ),
        );
        for (const file of files.files) data.append("files", file, file.name);
        const response = await fetch(`/api/my/publications/${publicationId}/sightings`, {
          method: "POST",
          credentials: "same-origin",
          cache: "no-store",
          headers: { [token.headerName || "X-CSRF-TOKEN"]: token.token },
          body: data,
        });
        if (!response.ok) {
          const result = await response.json().catch(() => ({}));
          throw new Error(result.message || "No se pudo registrar el avistamiento.");
        }
        form.reset();
        status.textContent = "Avistamiento registrado en el historial del caso.";
        await loadHistory(publicationId, version, history);
      } catch (error) {
        status.textContent = error.message || "No se pudo registrar el avistamiento.";
      } finally {
        submit.disabled = false;
      }
    });
    section.append(form);
  }

  window.RedAuxilioSightings = { render };
})();
