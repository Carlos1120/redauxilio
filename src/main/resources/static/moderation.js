/* Cola RA-08 de solo lectura: no persiste datos privados ni ejecuta decisiones administrativas. */
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
      return;
    }
    const version = generation;
    message.textContent = "Consultando cola privada…";
    list.setAttribute("aria-busy", "true");
    try {
      const page = await read(`/api/operator/publications?limit=20&offset=${nextOffset}`);
      if (version !== generation) return;
      offset = page.offset;
      for (const item of page.items) {
        const p = item.publication;
        const card = text("article", "");
        card.className = "report-card";
        card.append(
          text("h3", p.title),
          text("p", `${p.reviewStatus} · ${p.confidenceLevel} · ${p.operationalStatus}`),
          text(
            "p",
            `${item.visible ? "Visible" : "Oculta"} · ${p.closed ? "Cerrada" : "Abierta"} · ${item.activeReports} denuncias activas / ${item.totalReports} totales`,
          ),
        );
        const button = text("button", `Consultar contexto de ${p.title}`);
        button.type = "button";
        button.addEventListener("click", () => loadDetail(p.id));
        card.append(button);
        list.append(card);
      }
      get("moderation-previous").disabled = offset === 0;
      get("moderation-next").disabled = !page.hasMore;
      list.setAttribute("aria-busy", "false");
      message.textContent = page.items.length
        ? `${page.items.length} casos en esta página. Solo lectura.`
        : "No hay casos en revisión en esta página.";
    } catch (error) {
      if (version === generation) failed(error);
    }
  }

  async function loadDetail(id) {
    const version = generation;
    const selected = ++selection;
    detail.replaceChildren();
    try {
      const data = await read(`/api/operator/publications/${id}`);
      if (version !== generation || selected !== selection) return;
      const p = data.context.publication;
      detail.append(
        text("h3", p.title),
        text("p", p.description),
        text("p", `${p.location} · ${p.latitude}, ${p.longitude}`),
        text("p", `Creada: ${p.createdAt}. Actualizada: ${p.updatedAt}.`),
        text("h3", "Denuncias privadas"),
      );
      for (const report of data.reports)
        detail.append(
          text(
            "p",
            `${report.reportedAt} · ${report.active ? "Activa" : "Histórica"} · ${report.reason}`,
          ),
        );
      if (data.moreReports)
        detail.append(
          text(
            "p",
            "Se muestran las 50 denuncias más recientes; el resto permanece en la base de datos.",
          ),
        );
      detail.append(text("h3", "Historial del caso, más reciente primero"));
      for (const change of data.history) {
        detail.append(
          text(
            "p",
            `${change.changedAt} · ${change.action} · actor interno ${change.actorId} · ${change.reason ?? "Sin motivo adicional"}`,
          ),
          text("pre", `Antes: ${change.beforeSnapshot}\nDespués: ${change.afterSnapshot}`),
        );
      }
      if (data.moreHistory)
        detail.append(
          text(
            "p",
            "Se muestran los 50 cambios más recientes; el resto permanece en la base de datos.",
          ),
        );
      detail.append(text("h3", "Decisiones de moderación, más reciente primero"));
      for (const decision of data.decisions ?? [])
        detail.append(
          text(
            "p",
            `${decision.actedAt} · ${decision.action} · operador ${decision.actorId} · ${decision.reason}`,
          ),
          text("pre", `Antes: ${decision.beforeSnapshot}\nDespués: ${decision.afterSnapshot}`),
        );
      if (data.moreDecisions)
        detail.append(
          text("p", "Se muestran las 50 decisiones más recientes; el resto se conserva."),
        );
      appendActions(detail, data.context);
      detail.focus();
      message.textContent = "Contexto privado cargado.";
    } catch (error) {
      if (version === generation && selected === selection) failed(error);
    }
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
    const act = (action, operationalStatus, correction) => async () => {
      const version = generation;
      const selected = selection;
      const isCurrent = () => version === generation && selected === selection;
      if (!reason.value.trim()) {
        message.textContent = "Escribe el motivo antes de guardar la decisión.";
        reason.focus();
        return;
      }
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
        await loadQueue();
        await loadDetail(p.id);
        message.textContent = "Decisión registrada con auditoría.";
      } catch (error) {
        if (error.status === 400 || error.status === 409) {
          await loadDetail(p.id);
          message.textContent = "La decisión no se aplicó. Actualiza el caso y revisa sus reglas.";
        } else failed(error);
      }
    };
    const button = (label, action, status, correction) => {
      const control = text("button", label);
      control.type = "button";
      control.addEventListener("click", act(action, status, correction));
      section.append(control);
    };
    if (p.reviewStatus === "EN_REVISION")
      button("Mantener publicación y resolver denuncias activas", "KEEP");
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
        })),
      );
      correction.append(submitCorrection);
      section.append(correction);
    }
    if (context.visible) button("Ocultar publicación", "HIDE");
    else button("Restituir visibilidad", "RESTORE");
    if (!p.closed)
      button(
        "Cerrar según estado terminal",
        "CLOSE",
        p.category === "ASSISTANCE" ? "CERRADO" : "HABILITADA",
      );
    if (p.closed) {
      for (const status of p.category === "ASSISTANCE"
        ? ["DISPONIBLE", "CAPACIDAD LIMITADA"]
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

  get("moderation-refresh").addEventListener("click", () => loadQueue());
  get("moderation-next").addEventListener("click", () => loadQueue(offset + 20));
  get("moderation-previous").addEventListener("click", () => loadQueue(Math.max(0, offset - 20)));
  get("permission-history").addEventListener("click", loadPermissions);
  get("operator-logout").addEventListener("submit", clearPrivate);
  window.addEventListener("offline", () => {
    clearPrivate();
    message.textContent = "Sin conexión. La cola privada no está disponible.";
  });
  window.addEventListener("online", () => loadQueue());
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
    else loadQueue();
  });
  void loadQueue();
})();
