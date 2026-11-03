/* Cola y decisiones RA-08: datos privados efímeros y confirmación explícita del servidor. */
(() => {
  const get = (id) => document.getElementById(id);
  if (!get("moderation-workspace")) return;
  const list = get("moderation-list");
  const detail = get("moderation-detail");
  const events = get("permission-events");
  const message = get("moderation-message");
  let generation = 0;
  let selection = 0;
  let offset = 0;

  /** Invalida peticiones anteriores y elimina contexto al cambiar sesión, red o visibilidad. */
  function clearPrivate() {
    generation++;
    selection++;
    list.replaceChildren();
    detail.replaceChildren();
    events.replaceChildren();
    get("verifier-list")?.replaceChildren();
    get("verifier-events")?.replaceChildren();
    get("manual-review-id").value = "";
    get("manual-review-reason").value = "";
    get("verifier-email").value = "";
    get("verifier-reason").value = "";
    list.setAttribute("aria-busy", "false");
    get("moderation-next").disabled = true;
    get("moderation-previous").disabled = true;
  }

  // Todo dato de publicación, motivo o fotografía de auditoría se representa como texto, nunca HTML.
  function text(tag, value) {
    const node = document.createElement(tag);
    node.textContent = String(value ?? "");
    return node;
  }

  const stateLabels = {
    EN_REVISION: "En revisión",
    SIN_REVISION: "Sin alerta de revisión",
    CONFIRMADA: "Confirmada por la comunidad",
    REPORTADA: "Reportada",
    VERIFICADA: "Verificada oficialmente",
  };
  const display = (value) =>
    value === null || value === undefined
      ? "Sin dato"
      : typeof value === "boolean"
        ? value
          ? "Sí"
          : "No"
        : (stateLabels[value] ?? String(value));
  const categoryName = (value) =>
    ({
      ASSISTANCE: "Punto de asistencia",
      ROAD: "Vía afectada",
      MISSING_PERSON: "Persona desaparecida",
      HELP_REQUEST: "Solicitud de ayuda",
    })[value] ??
    value ??
    "Publicación ciudadana";
  function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? String(value ?? "Sin fecha")
      : date.toLocaleString("es-CO");
  }
  function badges(p) {
    const group = text("div", "");
    group.className = "operator-badges";
    for (const [label, value, kind] of [
      ["Revisión", p.reviewStatus, "review"],
      ["Confianza", p.confidenceLevel, "trust"],
      ["Estado operativo", p.operationalStatus, "state"],
    ]) {
      const badge = text("span", `${label}: ${display(value)}`);
      badge.className = `operator-badge operator-badge--${kind === "trust" && !["CONFIRMADA", "VERIFICADA"].includes(value) ? "state" : kind}`;
      group.append(badge);
    }
    return group;
  }

  async function read(url) {
    const response = await fetch(url, {
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (response.redirected || !response.ok) {
      const error = new Error("Consulta privada no disponible");
      error.status = response.status;
      error.reference = response.headers.get("X-Correlation-ID");
      throw error;
    }
    return response.json();
  }

  /** Envía una decisión con CSRF renovado; los datos privados solo viven en el DOM de esta vista. */
  async function decide(id, body, isCurrent) {
    const csrf = await read("/csrf");
    if (!isCurrent()) return false;
    const response = await fetch(`/api/operator/publications/${id}/actions`, {
      method: "POST",
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(body),
    });
    if (!isCurrent()) return false;
    if (response.redirected || !response.ok) {
      const error = new Error("Decisión privada no disponible");
      error.status = response.status;
      error.reference = response.headers.get("X-Correlation-ID");
      throw error;
    }
    await response.json();
    return true;
  }

  function failed(error) {
    clearPrivate();
    message.textContent =
      error.status === 401 || error.status === 403 || error.status === 200
        ? "Sesión o permiso de operador no disponible. Inicia sesión con una cuenta autorizada."
        : "No se pudo consultar moderación. Comprueba la conexión e intenta de nuevo.";
    if (/^[a-f0-9-]{36}$/i.test(error.reference ?? ""))
      message.textContent += ` Referencia: ${error.reference}.`;
  }

  /** Página acotada del servidor; al refrescar no deja visible una copia de una sesión anterior. */
  async function loadQueue(nextOffset = offset) {
    clearPrivate();
    if (!navigator.onLine) {
      message.textContent = "Sin conexión. La cola privada no está disponible.";
      return false;
    }
    const version = generation;
    message.textContent = "Consultando cola privada…";
    list.setAttribute("aria-busy", "true");
    try {
      const page = await read(`/api/operator/publications?limit=20&offset=${nextOffset}`);
      if (version !== generation) return false;
      offset = page.offset;
      for (const item of page.items) {
        const p = item.publication;
        const card = text("article", "");
        card.className = "report-card";
        const meta = text("div", "");
        meta.className = "operator-card-meta";
        meta.append(text("span", `#${p.id}`), text("span", categoryName(p.category)));
        card.append(
          meta,
          text("h3", p.title),
          badges(p),
          text(
            "p",
            `${item.visible ? "Visible" : "Oculta"} · ${p.closed ? "Cerrada" : "Abierta"} · ${item.activeReports} denuncias activas / ${item.totalReports} totales`,
          ),
        );
        const button = text("button", "Abrir caso →");
        button.type = "button";
        button.className = "secondary-button";
        button.value = String(p.id);
        button.setAttribute("aria-label", `Consultar contexto de ${p.title}`);
        button.setAttribute("aria-pressed", "false");
        button.addEventListener("click", () => loadDetail(p.id));
        card.append(button);
        list.append(card);
      }
      get("moderation-previous").disabled = offset === 0;
      get("moderation-next").disabled = !page.hasMore;
      if (!page.items.length) {
        const empty = text("p", "Bandeja al día. No hay casos en revisión en esta página.");
        empty.className = "operator-empty";
        list.append(empty);
      }
      list.setAttribute("aria-busy", "false");
      message.textContent = page.items.length
        ? `${page.items.length} casos en esta página.`
        : "No hay casos en revisión en esta página.";
      return true;
    } catch (error) {
      if (version === generation) failed(error);
      return false;
    }
  }

  async function loadDetail(id) {
    const version = generation;
    const selected = ++selection;
    detail.replaceChildren();
    message.textContent = "Cargando contexto privado…";
    try {
      const data = await read(`/api/operator/publications/${id}`);
      if (version !== generation || selected !== selection) return false;
      const p = data.context.publication;
      const summary = text("section", "");
      summary.className = "operator-summary";
      summary.append(
        text("p", `#${p.id} · ${categoryName(p.category)}`),
        text("h3", p.title),
        badges(p),
      );
      const description = text("p", p.description);
      description.className = "operator-description";
      const facts = text("dl", "");
      facts.className = "operator-facts";
      for (const [label, value] of [
        ...(p.missingPerson
          ? [
              ["Nombre o referencia", p.missingPerson.referenceName],
              ["Rango de edad", p.missingPerson.ageRange || "No indicado"],
              ["Última vez vista", formatDate(p.missingPerson.lastSeenAt)],
              ["Último sector conocido", p.missingPerson.lastSeenPlace],
            ]
          : []),
        ...(p.category === "HELP_REQUEST" && p.needType
          ? [
              ["Tipo de necesidad", p.needType],
              ["Cantidad aproximada", p.approximateQuantity || "No indicada"],
              ["Urgencia declarada (no oficial)", p.declaredUrgency],
              ["Alias o comunidad", p.requesterReference],
            ]
          : []),
        ["Sector o lugar público", p.location],
        ["Coordenadas", `${p.latitude}, ${p.longitude}`],
        ["Visibilidad", data.context.visible ? "Visible" : "Oculta"],
        ["Estado del caso", p.closed ? "Cerrado" : "Abierto"],
        ["Fecha de creación", formatDate(p.createdAt)],
        ["Última actualización", formatDate(p.updatedAt)],
      ]) {
        const fact = text("div", "");
        fact.append(text("dt", label), text("dd", value));
        facts.append(fact);
      }
      summary.append(description, facts);
      detail.append(summary);
      appendActions(detail, data.context);
      // La selección se representa con atributo accesible y borde, nunca únicamente con color.
      for (const card of list.children) {
        const control = card.children[card.children.length - 1];
        if (control?.tagName?.toLowerCase() === "button")
          control.setAttribute("aria-pressed", String(control.value === String(p.id)));
      }
      const isCurrent = () => version === generation && selected === selection;
      appendHistorySection(
        detail,
        "Denuncias privadas, más recientes primero",
        "reports",
        data.reports,
        data.moreReports,
        id,
        isCurrent,
      );
      appendHistorySection(
        detail,
        "Historial del caso, más reciente primero",
        "changes",
        data.history,
        data.moreHistory,
        id,
        isCurrent,
      );
      appendHistorySection(
        detail,
        "Decisiones de moderación, más recientes primero",
        "decisions",
        data.decisions ?? [],
        data.moreDecisions,
        id,
        isCurrent,
      );
      appendOfficialVerificationTools(detail, p, isCurrent);
      detail.focus();
      message.textContent = "Contexto privado cargado.";
      return true;
    } catch (error) {
      if (version === generation && selected === selection) failed(error);
      return false;
    }
  }

  /** Permite al operador registrar una corrección o retirar respaldo, con historial por versión. */
  async function appendOfficialVerificationTools(container, publication, isCurrent) {
    const section = text("section", "");
    section.className = "operator-history official-verification-admin";
    section.append(text("h3", "Verificación oficial y auditoría"));
    const status = text("p", "Consultando historial…");
    status.setAttribute("role", "status");
    const entries = text("div", "");
    section.append(status, entries);
    container.append(section);
    const form = document.createElement("form");
    form.className = "moderation-decision";
    const noteLabel = text("label", "Nota de la corrección (hasta 500 caracteres)");
    const note = document.createElement("textarea");
    note.maxLength = 500;
    note.rows = 2;
    noteLabel.append(note);
    const referenceLabel = text("label", "Referencia documental (opcional)");
    const reference = document.createElement("input");
    reference.maxLength = 200;
    referenceLabel.append(reference);
    const reasonLabel = text("label", "Motivo administrativo obligatorio");
    const reason = document.createElement("textarea");
    reason.maxLength = 500;
    reason.rows = 2;
    reason.required = true;
    reasonLabel.append(reason);
    const register = text("button", "Registrar verificación oficial");
    register.type = "submit";
    form.append(noteLabel, referenceLabel, reasonLabel, register);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const noteText = note.value.trim();
      const referenceText = reference.value.trim();
      if (!noteText && !referenceText) {
        status.textContent = "Indica una nota o una referencia para registrar el respaldo.";
        return;
      }
      try {
        const reasonText = reason.value.trim();
        if (!reasonText) {
          status.textContent = "El motivo administrativo es obligatorio.";
          return;
        }
        await saveOfficial({
          note: noteText,
          reference: referenceText || null,
          reason: reasonText,
        });
      } catch (error) {
        if (isCurrent())
          status.textContent = error.message || "No se pudo registrar la verificación.";
      }
    });
    section.append(form);

    async function saveOfficial(payload, verificationId = null) {
      if (!isCurrent()) return;
      const response = await fetch("/csrf", { credentials: "same-origin", cache: "no-store" }).then(
        (result) => {
          if (!result.ok) throw new Error("No se pudo validar la sesión.");
          return result.json();
        },
      );
      const url = verificationId
        ? `/api/operator/publications/${publication.id}/official-verifications/${verificationId}/revoke`
        : `/api/operator/publications/${publication.id}/official-verifications`;
      const result = await fetch(url, {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": response.token },
        body: JSON.stringify(payload),
      });
      if (!result.ok) throw new Error("No se pudo guardar la decisión oficial.");
      if (!isCurrent()) return;
      message.textContent = verificationId
        ? "Verificación retirada; el historial original se conserva."
        : "Verificación oficial registrada con actor, fecha y versión.";
      if (!verificationId) {
        note.value = "";
        reference.value = "";
        reason.value = "";
      }
      await loadDetail(publication.id);
    }

    try {
      const items = await read(
        `/api/operator/publications/${publication.id}/official-verifications`,
      );
      if (!isCurrent()) return;
      entries.replaceChildren();
      for (const item of items) {
        const row = text(
          "article",
          `${item.accountName} · versión ${item.publicationVersion} · ${formatDate(item.verifiedAt)} · ${item.note}${item.reference ? ` · ${item.reference}` : ""} · ${item.revoked ? `retirada por ${item.revokedBy} el ${formatDate(item.revokedAt)}: ${item.revokeReason}` : item.publicationVersion === publication.confirmationVersion ? "vigente" : "histórica por cambio de versión"}`,
        );
        if (!item.revoked && item.publicationVersion === publication.confirmationVersion) {
          const revokeForm = document.createElement("form");
          revokeForm.className = "moderation-decision";
          const reason = document.createElement("textarea");
          reason.maxLength = 500;
          reason.rows = 2;
          reason.required = true;
          reason.setAttribute("aria-label", "Motivo para retirar esta verificación");
          const button = text("button", "Retirar verificación");
          button.type = "submit";
          revokeForm.append(reason, button);
          revokeForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            const detail = reason.value.trim();
            if (!detail) return;
            try {
              await saveOfficial({ reason: detail }, item.id);
            } catch (error) {
              if (isCurrent()) status.textContent = error.message;
            }
          });
          row.append(revokeForm);
        }
        entries.append(row);
      }
      if (!items.length) entries.append(text("p", "Sin verificaciones anteriores."));
      status.textContent = "El historial incluye versiones anteriores y retiros.";
    } catch (error) {
      if (isCurrent())
        status.textContent = error.message || "No se pudo consultar el historial oficial.";
    }
  }

  /** Añade un registro privado con texto plano para el flujo seleccionado. */
  /** Línea de tiempo textual: diferencias conocidas legibles y snapshots originales plegados. */
  function appendHistoryItem(stream, item, container) {
    const entry = text("article", "");
    entry.className = "operator-event";
    container.append(entry);
    if (stream === "reports") {
      entry.append(
        text("p", `${formatDate(item.reportedAt)} · ${item.active ? "Activa" : "Histórica"}`),
        text("p", item.reason),
      );
      return;
    }
    const actionNames = {
      REVIEW: "Envío a revisión",
      KEEP: "Revisión finalizada",
      CORRECT: "Corrección",
      HIDE: "Ocultamiento",
      RESTORE: "Restitución",
      CLOSE: "Cierre",
      REOPEN: "Reapertura",
      EDIT: "Edición",
      CONTACT: "Autorización de contacto adulto",
      CONTACT_WITHDRAW: "Retiro de contacto",
      STATUS: "Cambio de estado",
    };
    entry.append(
      text(
        "p",
        `${formatDate(stream === "changes" ? item.changedAt : item.actedAt)} · ${actionNames[item.action] ?? item.action} · ${stream === "changes" ? "actor interno" : "operador"} ${item.actorId}`,
      ),
      text("p", item.reason ?? "Sin motivo adicional"),
    );
    try {
      const before = JSON.parse(item.beforeSnapshot),
        after = JSON.parse(item.afterSnapshot);
      const labels = {
        title: "Nombre o tramo",
        description: "Descripción",
        location: "Sector",
        latitude: "Latitud",
        longitude: "Longitud",
        status: "Estado operativo",
        operationalStatus: "Estado operativo",
        visible: "Visibilidad",
        closed: "Caso cerrado",
        reviewStatus: "Revisión de contenido",
        confidenceLevel: "Confianza",
        activeReports: "Denuncias activas",
        assistanceSubtype: "Subtipo",
        impactType: "Afectación",
        confirmationVersion: "Versión de confirmación",
        needType: "Tipo de necesidad",
        approximateQuantity: "Cantidad aproximada",
        declaredUrgency: "Urgencia declarada",
        requesterReference: "Alias o comunidad",
        contactAuthorized: "Contacto autorizado",
        referenceName: "Nombre o referencia",
        ageRange: "Rango de edad",
        physicalDescription: "Descripción física",
        lastSeenPlace: "Último sector conocido",
        lastSeenAt: "Última vez vista",
      };
      if (before && after) {
        Object.assign(before, before.missingPerson);
        Object.assign(after, after.missingPerson);
      }
      let changes = 0;
      for (const [key, label] of Object.entries(labels)) {
        if (
          !before ||
          !after ||
          (!Object.hasOwn(before, key) && !Object.hasOwn(after, key)) ||
          JSON.stringify(before[key]) === JSON.stringify(after[key])
        )
          continue;
        const change = text("div", "");
        change.className = "operator-change";
        const previous = text("span", `Antes: ${display(before[key])}`);
        previous.className = "operator-before";
        change.append(
          text("strong", label),
          previous,
          text("span", `Después: ${display(after[key])}`),
        );
        entry.append(change);
        changes++;
      }
      if (!changes)
        entry.append(
          text("p", "Sin cambios en los campos resumidos. Consulta el detalle técnico."),
        );
    } catch {
      entry.append(
        text(
          "p",
          "Resumen no disponible para este registro histórico. Consulta el detalle técnico.",
        ),
      );
    }
    const technical = text("details", "");
    technical.append(
      text("summary", "Detalle técnico · registro original"),
      text("pre", `Antes: ${item.beforeSnapshot}\nDespués: ${item.afterSnapshot}`),
    );
    entry.append(technical);
  }

  /** Carga páginas anteriores sin almacenar datos y descarta respuestas de otro contexto. */
  function appendHistorySection(container, title, stream, items, hasMore, id, isCurrent) {
    const section = document.createElement("section");
    const entries = document.createElement("div");
    section.className = "operator-history";
    entries.className = "operator-timeline";
    section.append(text("h3", title), entries);
    if (!items.length) entries.append(text("p", "Sin registros en esta sección."));
    for (const item of items) appendHistoryItem(stream, item, entries);
    if (hasMore && items.length) {
      const loadButton = document.createElement("button");
      loadButton.type = "button";
      loadButton.className = "secondary-button";
      loadButton.textContent = "Cargar 50 entradas anteriores";
      loadButton.addEventListener("click", async () => {
        if (loadButton.disabled || !isCurrent()) return;
        loadButton.disabled = true;
        message.textContent = "Consultando entradas anteriores…";
        try {
          const beforeId = items.at(-1).id;
          const page = await read(
            `/api/operator/publications/${id}/history/${stream}?beforeId=${beforeId}&limit=50`,
          );
          if (!isCurrent()) return;
          for (const item of page.items) {
            appendHistoryItem(stream, item, entries);
            items.push(item);
          }
          loadButton.disabled = !page.hasMore;
          loadButton.textContent = page.hasMore
            ? "Cargar 50 entradas anteriores"
            : "No hay entradas anteriores";
          message.textContent = `${page.items.length} entradas anteriores cargadas.`;
        } catch (error) {
          if (isCurrent()) failed(error);
        } finally {
          if (
            isCurrent() &&
            loadButton.disabled &&
            loadButton.textContent === "Cargar 50 entradas anteriores"
          )
            loadButton.disabled = false;
        }
      });
      section.append(loadButton);
    }
    container.append(section);
  }

  /** Presenta campos etiquetados y acciones explícitas según estado y categoría del caso. */
  function appendActions(container, context) {
    const p = context.publication;
    const section = text("section", "");
    section.className = "moderation-decision";
    section.append(text("h3", "Decisión del operador"));
    const reasonLabel = text("label", "Motivo obligatorio (máximo 500 caracteres)");
    const reason = document.createElement("textarea");
    reason.maxLength = 500;
    reason.required = true;
    reasonLabel.append(reason);
    section.append(reasonLabel);
    // Confirmación accesible en la propia página: se limpia con el contexto y no bloquea el navegador.
    const confirmation = text("div", "");
    confirmation.className = "operator-confirmation";
    confirmation.hidden = true;
    confirmation.setAttribute("role", "group");
    confirmation.setAttribute("aria-label", "Confirmar decisión");
    const confirmationText = text("p", "");
    const confirmButton = text("button", "Confirmar decisión");
    const cancelButton = text("button", "Cancelar");
    confirmButton.type = cancelButton.type = "button";
    confirmButton.className = "operator-action--danger";
    cancelButton.className = "secondary-button";
    let confirmDecision = null,
      returnFocus = reason;
    confirmButton.addEventListener("click", async () => {
      const apply = confirmDecision;
      confirmDecision = null;
      confirmation.hidden = true;
      if (apply) await apply();
    });
    const cancelConfirmation = () => {
      confirmDecision = null;
      confirmation.hidden = true;
      message.textContent = "Decisión cancelada. No se enviaron cambios.";
      returnFocus.focus();
    };
    cancelButton.addEventListener("click", cancelConfirmation);
    confirmation.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        cancelConfirmation();
      }
    });
    confirmation.append(confirmationText, confirmButton, cancelButton);
    section.append(confirmation);
    let pending = false;
    const act = (action, operationalStatus, correction) => async (confirmed) => {
      if (pending) return;
      const version = generation;
      const selected = selection;
      const isCurrent = () => version === generation && selected === selection;
      if (!reason.value.trim()) {
        message.textContent = "Escribe el motivo antes de guardar la decisión.";
        reason.focus();
        return;
      }
      if ((action === "HIDE" || action === "CLOSE") && confirmed !== true) {
        confirmationText.textContent = `${action === "HIDE" ? "Ocultar" : "Cerrar"} publicación #${p.id}. Se conservarán el historial y el motivo. Confirma para aplicar la decisión.`;
        returnFocus = confirmed?.currentTarget ?? reason;
        confirmDecision = () =>
          isCurrent() ? act(action, operationalStatus, correction)(true) : undefined;
        confirmation.hidden = false;
        confirmButton.focus();
        return;
      }
      confirmDecision = null;
      confirmation.hidden = true;
      if (!isCurrent()) return;
      pending = true;
      message.textContent = "Guardando decisión…";
      try {
        const applied = await decide(
          p.id,
          {
            action,
            reason: reason.value,
            operationalStatus,
            correction: typeof correction === "function" ? correction() : correction,
          },
          isCurrent,
        );
        if (!applied || !isCurrent()) return;
        const refreshed = generation + 1;
        if (!(await loadQueue()) || generation !== refreshed) return;
        const nextSelection = selection + 1;
        if ((await loadDetail(p.id)) && generation === refreshed && selection === nextSelection)
          message.textContent = "Decisión registrada con auditoría.";
      } catch (error) {
        if (!isCurrent()) return;
        if (error.status === 400 || error.status === 409) {
          const nextSelection = selection + 1;
          if ((await loadDetail(p.id)) && generation === version && selection === nextSelection)
            message.textContent =
              "La decisión no se aplicó. Actualiza el caso y revisa sus reglas.";
        } else failed(error);
      } finally {
        pending = false;
      }
    };
    const button = (label, action, status, correction) => {
      const control = text("button", label);
      control.type = "button";
      control.className = ["HIDE", "CLOSE"].includes(action)
        ? "operator-action--danger"
        : ["KEEP", "RESTORE"].includes(action)
          ? "operator-action--positive"
          : "secondary-button";
      control.addEventListener("click", act(action, status, correction));
      section.append(control);
    };
    if (p.reviewStatus === "EN_REVISION")
      button(
        context.activeReports === 0
          ? "Finalizar revisión y mantener publicación"
          : "Mantener publicación y resolver denuncias activas",
        "KEEP",
      );
    if (!p.closed) {
      const correction = document.createElement("details");
      correction.append(text("summary", "Preparar corrección sustancial"));
      const fields = {};
      for (const [key, label, value] of [
        ["title", "Nombre o tramo", p.title],
        ["description", "Descripción", p.description],
        ["location", "Sector o lugar público", p.location],
      ]) {
        const wrapper = text("label", label);
        const input = document.createElement(key === "description" ? "textarea" : "input");
        input.value = value ?? "";
        input.maxLength = key === "title" ? 120 : key === "description" ? 1000 : 160;
        wrapper.append(input);
        correction.append(wrapper);
        fields[key] = input;
      }
      for (const key of ["latitude", "longitude"]) {
        const wrapper = text("label", key === "latitude" ? "Latitud" : "Longitud");
        const input = document.createElement("input");
        input.type = "number";
        input.step = "any";
        input.value = p[key];
        wrapper.append(input);
        correction.append(wrapper);
        fields[key] = input;
      }
      if (p.category === "ASSISTANCE") {
        const wrapper = text("label", "Subtipo de asistencia");
        const input = document.createElement("select");
        for (const [value, label] of [
          ["SHELTER", "Refugio"],
          ["COLLECTION", "Centro de acopio"],
          ["MEDICAL_CENTER", "Centro médico"],
        ]) {
          const option = text("option", label);
          option.value = value;
          input.append(option);
        }
        input.value = p.assistanceSubtype;
        wrapper.append(input);
        correction.append(wrapper);
        fields.assistanceSubtype = input;
      } else if (p.category === "HELP_REQUEST") {
        for (const [key, label, values] of [
          [
            "needType",
            "Tipo de necesidad",
            ["AGUA", "ALIMENTOS", "REFUGIO", "SALUD", "TRANSPORTE", "OTRO"],
          ],
          ["approximateQuantity", "Cantidad aproximada (opcional)", null],
          ["declaredUrgency", "Urgencia declarada (no oficial)", ["BAJA", "MEDIA", "ALTA"]],
          ["requesterReference", "Alias o comunidad solicitante", null],
        ]) {
          const wrapper = text("label", label),
            input = document.createElement(values ? "select" : "input");
          if (values)
            for (const value of values) {
              const option = text("option", value);
              option.value = value;
              input.append(option);
            }
          input.value = p[key] ?? "";
          if (!values) input.maxLength = 120;
          wrapper.append(input);
          correction.append(wrapper);
          fields[key] = input;
        }
      } else if (p.category === "MISSING_PERSON") {
        const ageLabel = text("label", "Rango de edad (opcional)"),
          age = document.createElement("select");
        for (const value of ["", "0–5", "6–12", "13–17", "18–29", "30–59", "60+"]) {
          const option = text("option", value || "No indicado");
          option.value = value;
          age.append(option);
        }
        age.value = p.missingPerson?.ageRange || "";
        ageLabel.append(age);
        correction.append(ageLabel);
        fields.missingAge = age;
        const seenLabel = text("label", "Fecha y hora aproximadas de última vez vista"),
          seen = document.createElement("input");
        seen.type = "datetime-local";
        seen.step = "0.001";
        window.RedAuxilioMissingPerson?.setSeenTime(seen, p.missingPerson?.lastSeenAt);
        seenLabel.append(seen);
        correction.append(seenLabel);
        fields.missingSeen = seen;
        fields.description.maxLength = 500;
      } else {
        const wrapper = text("label", "Tipo de afectación");
        const input = document.createElement("select");
        for (const value of ["DERRUMBE", "INUNDACIÓN", "DAÑO ESTRUCTURAL", "OTRO"]) {
          const option = text("option", value);
          option.value = value;
          input.append(option);
        }
        input.value = p.impactType;
        wrapper.append(input);
        correction.append(wrapper);
        fields.impactType = input;
      }
      const submitCorrection = text("button", "Aplicar corrección y anular respaldos vigentes");
      submitCorrection.type = "button";
      submitCorrection.addEventListener(
        "click",
        act("CORRECT", undefined, () => ({
          category: p.category,
          assistanceSubtype: fields.assistanceSubtype?.value ?? null,
          title: fields.title.value,
          description: fields.description.value,
          location: fields.location.value,
          operationalStatus: p.operationalStatus,
          impactType: fields.impactType?.value ?? null,
          latitude: Number(fields.latitude.value),
          longitude: Number(fields.longitude.value),
          ...(p.category === "MISSING_PERSON"
            ? {
                missingPerson: {
                  referenceName: fields.title.value,
                  physicalDescription: fields.description.value,
                  lastSeenPlace: fields.location.value,
                  ageRange: fields.missingAge.value || null,
                  lastSeenAt: window.RedAuxilioMissingPerson.readSeenTime(fields.missingSeen),
                },
              }
            : {}),
          ...(p.category === "HELP_REQUEST"
            ? {
                needType: fields.needType.value,
                approximateQuantity: fields.approximateQuantity.value.trim() || null,
                declaredUrgency: fields.declaredUrgency.value,
                requesterReference: fields.requesterReference.value,
              }
            : {}),
        })),
      );
      correction.append(submitCorrection);
      section.append(correction);
    }
    if (p.category === "MISSING_PERSON") {
      if (!p.closed)
        button(
          p.operationalStatus === "DESAPARECIDA"
            ? "Marcar como encontrada"
            : "Rectificar como desaparecida",
          "STATUS",
          p.operationalStatus === "DESAPARECIDA" ? "ENCONTRADA" : "DESAPARECIDA",
        );
      // Retira la autorización vigente: motivo enumerado para no archivar el valor del contacto.
      const label = text("label", "Motivo para retirar contacto adulto"),
        choice = document.createElement("select");
      for (const [value, name] of [
        ["", "Selecciona motivo"],
        ["PROTECCION_DATOS", "Protección de datos"],
        ["SOLICITUD_AUTOR", "Solicitud del autor"],
        ["CONTACTO_INCORRECTO", "Contacto incorrecto"],
      ]) {
        const option = text("option", name);
        option.value = value;
        choice.append(option);
      }
      label.append(choice);
      const retire = text("button", "Retirar contacto autorizado");
      retire.type = "button";
      retire.addEventListener("click", async () => {
        if (pending) return;
        if (!choice.value) {
          message.textContent = "Selecciona el motivo de retiro del contacto.";
          return;
        }
        const version = generation,
          selected = selection;
        const isCurrent = () => version === generation && selected === selection;
        pending = true;
        try {
          const token = await read("/csrf");
          if (!isCurrent()) return;
          const response = await fetch(
            `/api/my/publications/${p.id}/authorized-contact?reason=${choice.value}`,
            {
              method: "DELETE",
              credentials: "same-origin",
              cache: "no-store",
              headers: { [token.headerName]: token.token },
            },
          );
          if (!isCurrent()) return;
          if (!response.ok) {
            const error = new Error("Retiro no disponible");
            error.status = response.status;
            throw error;
          }
          message.textContent = "Retiro del contacto confirmado por el servidor.";
        } catch (error) {
          if (isCurrent()) failed(error);
        } finally {
          pending = false;
        }
      });
      section.append(label, retire);
    }
    if (context.visible) button("Ocultar publicación", "HIDE");
    else button("Restituir visibilidad", "RESTORE");
    if (!p.closed)
      button(
        "Cerrar según estado terminal",
        "CLOSE",
        p.category === "ASSISTANCE"
          ? "CERRADO"
          : p.category === "MISSING_PERSON"
            ? "CASO CERRADO"
            : p.category === "HELP_REQUEST"
              ? "SATISFECHA"
              : "HABILITADA",
      );
    if (p.closed) {
      for (const status of p.category === "ASSISTANCE"
        ? ["DISPONIBLE", "CAPACIDAD LIMITADA"]
        : p.category === "MISSING_PERSON"
          ? ["DESAPARECIDA"]
          : p.category === "HELP_REQUEST"
            ? ["ABIERTA"]
            : ["BLOQUEADA", "PARCIALMENTE HABILITADA"])
        button(`Reabrir como ${status}`, "REOPEN", status);
    }
    container.append(section);
  }

  async function loadPermissions() {
    const version = generation;
    events.replaceChildren();
    try {
      const items = await read("/api/operator/permissions/history?limit=20&offset=0");
      if (version !== generation) return;
      for (const item of items)
        events.append(
          text(
            "p",
            `${item.changedAt} · ${item.actor} · cuenta interna ${item.accountId} · ${item.action} · ${item.beforeGranted} → ${item.afterGranted} · ${item.reason}`,
          ),
        );
      message.textContent = items.length
        ? "Últimos 20 eventos de permisos como máximo; el resto se conserva."
        : "Sin eventos de permisos.";
    } catch (error) {
      if (version === generation) failed(error);
    }
  }

  /** Mantiene privada la lista de entidades verificadoras y la auditoría de sus permisos. */
  async function loadVerifierAdmin() {
    const version = generation;
    const list = get("verifier-list");
    const history = get("verifier-events");
    if (!list || !history) return;
    list.replaceChildren();
    history.replaceChildren();
    try {
      const [accounts, changes] = await Promise.all([
        read("/api/operator/verifier-permissions"),
        read("/api/operator/verifier-permissions/history?limit=20&offset=0"),
      ]);
      if (version !== generation) return;
      for (const account of accounts) {
        const row = text(
          "p",
          `${account.displayName} · ${account.email} · autorizado ${formatDate(account.grantedAt)} `,
        );
        const revoke = text("button", "Retirar permiso");
        revoke.type = "button";
        revoke.className = "secondary-button";
        revoke.addEventListener("click", () => {
          get("verifier-email").value = account.email;
          get("verifier-action").value = "REVOKE";
          get("verifier-reason").focus();
        });
        row.append(revoke);
        list.append(row);
      }
      for (const item of changes)
        history.append(
          text(
            "p",
            `${formatDate(item.changedAt)} · ${item.actorEmail} · ${item.action} ${item.email} · ${item.beforeGranted} → ${item.afterGranted} · ${item.reason}`,
          ),
        );
      if (!accounts.length) list.append(text("p", "No hay cuentas autorizadas actualmente."));
      if (!changes.length) history.append(text("p", "Sin cambios de permisos registrados."));
    } catch (error) {
      if (version === generation) failed(error);
    }
  }

  /** Envía GRANT/REVOKE con CSRF y motivo; el servidor conserva estado y autoría. */
  get("verifier-permission-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = get("verifier-email").value.trim();
    const action = get("verifier-action").value;
    const reason = get("verifier-reason").value.trim();
    if (!email || !reason || reason.length > 500) {
      message.textContent = "Indica el correo de una cuenta existente y un motivo válido.";
      return;
    }
    if (!navigator.onLine) {
      clearPrivate();
      message.textContent = "Sin conexión. No se modificaron permisos.";
      return;
    }
    const submit = get("verifier-permission-submit");
    const version = generation;
    submit.disabled = true;
    try {
      const csrf = await fetch("/csrf", { credentials: "same-origin", cache: "no-store" }).then(
        (response) => {
          if (!response.ok) throw new Error("No se pudo validar la sesión del operador.");
          return response.json();
        },
      );
      const response = await fetch("/api/operator/verifier-permissions", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf.token },
        body: JSON.stringify({ email, action, reason }),
      });
      if (!response.ok)
        throw new Error("No se pudo guardar el permiso. Comprueba cuenta y motivo.");
      if (version !== generation) return;
      get("verifier-reason").value = "";
      message.textContent =
        action === "GRANT"
          ? "Autorización concedida y auditada."
          : "Autorización retirada y auditada.";
      await loadVerifierAdmin();
    } catch (error) {
      if (version === generation) failed(error);
    } finally {
      submit.disabled = false;
    }
  });

  /** RF-16: valida ID/motivo, renueva CSRF y solo anuncia éxito tras persistir; no guarda borrador. */
  get("manual-review-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const submit = get("manual-review-submit");
    if (submit.disabled) return;
    const id = get("manual-review-id").value.trim();
    const reason = get("manual-review-reason").value.trim();
    if (
      !/^\d+$/.test(id) ||
      !Number.isSafeInteger(Number(id)) ||
      Number(id) < 1000000 ||
      !reason ||
      reason.length > 500
    ) {
      message.textContent =
        "Indica un ID ciudadano válido y un motivo de entre 1 y 500 caracteres.";
      return;
    }
    if (!navigator.onLine) {
      clearPrivate();
      message.textContent = "Sin conexión. No se envió la publicación a revisión.";
      return;
    }
    const version = generation;
    const isCurrent = () => version === generation;
    submit.disabled = true;
    message.textContent = "Enviando publicación a revisión…";
    try {
      if (!(await decide(id, { action: "REVIEW", reason }, isCurrent)) || !isCurrent()) return;
      const refreshed = generation + 1;
      if (!(await loadQueue(0)) || generation !== refreshed) return;
      // Abre el ID confirmado aunque la cola tenga más de una página, sin cambiar su orden.
      const selected = selection + 1;
      if ((await loadDetail(id)) && generation === refreshed && selection === selected)
        message.textContent =
          "Publicación enviada a revisión con auditoría. Contexto privado cargado.";
    } catch (error) {
      if (!isCurrent()) return;
      if (error.status === 400 || error.status === 404 || error.status === 409)
        message.textContent =
          "No se aplicó la revisión. Comprueba el ID y el motivo; el caso puede estar ya en revisión.";
      else failed(error);
    } finally {
      submit.disabled = false;
    }
  });

  get("moderation-refresh").addEventListener("click", () => loadQueue());
  get("moderation-next").addEventListener("click", () => loadQueue(offset + 20));
  get("moderation-previous").addEventListener("click", () => loadQueue(Math.max(0, offset - 20)));
  get("permission-history").addEventListener("click", loadPermissions);
  get("verifier-refresh")?.addEventListener("click", loadVerifierAdmin);
  get("verification-permission-admin")?.addEventListener("toggle", (event) => {
    if (event.currentTarget.open) loadVerifierAdmin();
  });
  get("operator-logout").addEventListener("submit", clearPrivate);
  window.addEventListener("offline", () => {
    clearPrivate();
    message.textContent = "Sin conexión. La cola privada no está disponible.";
  });
  window.addEventListener("online", () => {
    const pending = loadQueue();
    if (get("verification-permission-admin")?.open) loadVerifierAdmin();
    return pending;
  });
  window.addEventListener("pagehide", clearPrivate);
  window.addEventListener("pageshow", (event) => {
    if (event.persisted) loadQueue();
  });
  window.addEventListener("redauxilio:identity-change", () => {
    clearPrivate();
    message.textContent = "Sesión modificada. Actualiza para comprobar el permiso de operador.";
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) clearPrivate();
    else {
      loadQueue();
      if (get("verification-permission-admin")?.open) loadVerifierAdmin();
    }
  });
  void loadQueue();
})();
