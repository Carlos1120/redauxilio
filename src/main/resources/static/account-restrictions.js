"use strict";
/** Maneja en memoria el bloqueo privado de cuentas; nunca conserva respuestas sensibles offline. */
(() => {
  const get = (id) => document.getElementById(id);
  const form = get("account-restriction-form");
  if (!form) return;

  const email = get("account-restriction-email");
  const action = get("account-restriction-action");
  const reason = get("account-restriction-reason");
  const message = get("account-restriction-message");
  const result = get("account-restriction-result");
  let generation = 0;

  function clearPrivate() {
    generation += 1;
    result.replaceChildren();
  }

  function clearSensitiveInputs() {
    email.value = "";
    action.value = "";
    reason.value = "";
  }

  function element(tag, content, className) {
    const node = document.createElement(tag);
    node.textContent = content;
    if (className) node.className = className;
    return node;
  }

  function formatDate(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Fecha no disponible" : date.toLocaleString("es-CO");
  }

  function render(account) {
    const card = element("section", "", "account-restriction-card");
    card.setAttribute("aria-labelledby", "account-restriction-result-title");
    const title = element("h3", "Estado de la cuenta");
    title.id = "account-restriction-result-title";
    card.append(title);
    card.append(element("p", `${account.displayName} · ${account.email}`));
    card.append(
      element(
        "p",
        account.blocked
          ? "Bloqueada: conserva solo la lectura pública."
          : "Activa: puede colaborar.",
      ),
    );
    if (account.blockedAt)
      card.append(element("p", `Bloqueada el ${formatDate(account.blockedAt)}.`));
    if (account.currentReason)
      card.append(element("p", `Motivo vigente: ${account.currentReason}`));

    const historyTitle = element("h4", "Historial de decisiones");
    card.append(historyTitle);
    if (!account.history?.length) {
      card.append(element("p", "Todavía no hay decisiones registradas."));
    } else {
      const list = document.createElement("ol");
      let displayed = [...account.history];
      const appendEntries = (entries) => {
        for (const entry of entries) {
          const item = document.createElement("li");
          const heading = entry.action === "BLOCK" ? "Cuenta bloqueada" : "Cuenta reactivada";
          item.append(element("strong", heading));
          item.append(
            element(
              "p",
              `${formatDate(entry.changedAt)} · Operador: ${entry.actorDisplayName} (cuenta #${entry.actorId})`,
            ),
          );
          item.append(element("p", `Motivo: ${entry.reason}`));
          list.append(item);
        }
      };
      appendEntries(displayed);
      card.append(list);
      if (account.historyHasOlder) {
        const more = element("button", "Cargar decisiones anteriores", "secondary-button");
        more.type = "button";
        more.addEventListener("click", async () => {
          if (more.disabled || !displayed.length) return;
          const current = generation;
          more.disabled = true;
          message.textContent = "Consultando decisiones anteriores…";
          try {
            const target = email.value.trim();
            const cursor = displayed[displayed.length - 1].id;
            const response = await fetch(
              `/api/operator/accounts/restriction/history?email=${encodeURIComponent(target)}&beforeId=${cursor}`,
              {
                credentials: "same-origin",
                cache: "no-store",
                headers: { Accept: "application/json" },
              },
            );
            if (current !== generation) return;
            if (response.redirected || !response.ok) {
              const error = new Error("No se pudo consultar la auditoría anterior.");
              error.status = response.status;
              throw error;
            }
            const page = await response.json();
            if (current !== generation) return;
            displayed = [...displayed, ...page.events];
            appendEntries(page.events);
            more.hidden = !page.hasOlder;
            message.textContent = "Se cargaron decisiones anteriores.";
          } catch (error) {
            if (current === generation) reportFailure(error);
          } finally {
            if (current === generation) more.disabled = false;
          }
        });
        card.append(more);
      }
    }
    result.replaceChildren(card);
  }

  async function readCsrf() {
    const response = await fetch("/csrf", {
      credentials: "same-origin",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (response.redirected || !response.ok) throw new Error("Confirma tu sesión de operador.");
    return response.json();
  }

  async function loadStatus(expectedGeneration) {
    const target = email.value.trim();
    if (!target) {
      message.textContent = "Escribe el correo exacto de una cuenta para consultar su estado.";
      return;
    }
    if (!navigator.onLine) {
      clearPrivate();
      message.textContent = "Sin conexión. El estado y el historial privados no están disponibles.";
      return;
    }
    const response = await fetch(
      `/api/operator/accounts/restriction?email=${encodeURIComponent(target)}`,
      { credentials: "same-origin", cache: "no-store", headers: { Accept: "application/json" } },
    );
    if (expectedGeneration !== generation) return;
    if (response.redirected || !response.ok) {
      const error = new Error("No se pudo consultar esa cuenta.");
      error.status = response.status;
      throw error;
    }
    const account = await response.json();
    if (expectedGeneration !== generation) return;
    render(account);
    message.textContent = "Estado e historial cargados desde el servidor.";
  }

  function reportFailure(error) {
    if (error.status === 401 || error.status === 403)
      message.textContent = "Tu sesión o permiso de operador ya no está vigente.";
    else if (error.status === 404)
      message.textContent = "No se encontró una cuenta con ese correo exacto.";
    else if (error.status === 409)
      message.textContent =
        "La acción no se aplicó: comprueba el estado actual y evita bloquearte.";
    else if (error.status === 400)
      message.textContent = "Revisa el correo, la acción y el motivo (máximo 500 caracteres).";
    else message.textContent = error.message || "No se pudo completar la consulta.";
  }

  get("account-restriction-inspect").addEventListener("click", async () => {
    clearPrivate();
    const current = generation;
    message.textContent = "Consultando estado e historial…";
    try {
      await loadStatus(current);
    } catch (error) {
      if (current === generation) reportFailure(error);
    }
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submit = get("account-restriction-submit");
    if (submit.disabled) return;
    const input = {
      email: email.value.trim(),
      action: action.value,
      reason: reason.value.trim(),
    };
    if (!input.email || !input.action || !input.reason || input.reason.length > 500) {
      message.textContent = "Completa correo, acción y motivo (máximo 500 caracteres).";
      return;
    }
    if (!navigator.onLine) {
      clearPrivate();
      message.textContent = "Sin conexión. No se envió ninguna decisión.";
      return;
    }
    clearPrivate();
    const current = generation;
    submit.disabled = true;
    message.textContent = "Guardando decisión y auditoría…";
    try {
      const csrf = await readCsrf();
      // No iniciar una escritura si la espera de CSRF pertenece a una pantalla invalidada.
      // Esto no cancela decisiones que ya fueron enviadas al servidor.
      if (current !== generation || !navigator.onLine) return;
      const response = await fetch("/api/operator/accounts/restriction", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf.token },
        body: JSON.stringify(input),
      });
      if (current !== generation) return;
      if (response.redirected || !response.ok) {
        const error = new Error("No se pudo guardar la decisión.");
        error.status = response.status;
        throw error;
      }
      const account = await response.json();
      if (current !== generation) return;
      action.value = "";
      reason.value = "";
      render(account);
      message.textContent =
        input.action === "BLOCK"
          ? "Cuenta bloqueada y decisión auditada."
          : "Cuenta reactivada y decisión auditada.";
    } catch (error) {
      if (current === generation) reportFailure(error);
    } finally {
      submit.disabled = false;
    }
  });

  window.addEventListener("offline", () => {
    clearPrivate();
    clearSensitiveInputs();
    message.textContent = "Sin conexión. Se borró el contenido privado de esta pantalla.";
  });
  window.addEventListener("pagehide", () => {
    clearPrivate();
    clearSensitiveInputs();
  });
  window.addEventListener("redauxilio:identity-change", () => {
    clearPrivate();
    clearSensitiveInputs();
  });
  get("operator-logout").addEventListener("submit", () => {
    clearPrivate();
    clearSensitiveInputs();
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      clearPrivate();
      clearSensitiveInputs();
    }
  });
})();
