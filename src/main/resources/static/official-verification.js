"use strict";
/* Presenta respaldo oficial público y muestra el formulario solo a una cuenta con permiso vigente. */
(() => {
  let generation = 0;
  const make = (tag, content, className = "") => {
    const element = document.createElement(tag);
    element.textContent = content;
    element.className = className;
    return element;
  };

  async function render(item, container) {
    const request = ++generation;
    container.replaceChildren(make("h3", "Verificación oficial"));
    const status = make("p", "Consultando respaldos oficiales…", "official-verification-status");
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    const entries = make("div", "", "official-verification-list");
    container.append(status, entries);
    try {
      const [response, permission] = await Promise.all([
        fetch(`/api/publications/${item.id}/official-verifications`, {
          cache: "no-store",
          headers: { Accept: "application/json" },
        }),
        fetch("/api/my/verification-permission", {
          credentials: "same-origin",
          cache: "no-store",
          headers: { Accept: "application/json" },
        }),
      ]);
      const verifications = response.ok ? await response.json() : [];
      const allowed = permission.ok && (await permission.json()).canVerify === true;
      if (request !== generation) return;
      entries.replaceChildren();
      for (const entry of verifications) {
        const article = make("article", "", "official-verification-entry");
        article.append(
          make("strong", entry.accountName),
          make("time", new Date(entry.verifiedAt).toLocaleString("es-CO")),
        );
        if (entry.note) article.append(make("p", entry.note));
        if (entry.reference) article.append(make("p", `Referencia: ${entry.reference}`));
        entries.append(article);
      }
      status.textContent = verifications.length
        ? `${verifications.length} respaldo(s) oficial(es) vigente(s).`
        : "Esta publicación no tiene verificaciones oficiales vigentes.";
      if (allowed) addForm(item, container, status, request);
    } catch {
      if (request === generation)
        status.textContent = "No se pudieron consultar los respaldos oficiales.";
    }
  }

  function addForm(item, container, status, request) {
    const form = document.createElement("form");
    form.className = "official-verification-form";
    const noteLabel = make("label", "Nota institucional");
    const note = document.createElement("textarea");
    note.maxLength = 500;
    note.rows = 2;
    note.autocomplete = "off";
    noteLabel.append(note);
    const referenceLabel = make("label", "Referencia (opcional)");
    const reference = document.createElement("input");
    reference.maxLength = 200;
    reference.autocomplete = "off";
    referenceLabel.append(reference);
    const submit = make("button", "Registrar verificación oficial");
    submit.type = "submit";
    const help = make(
      "small",
      "El respaldo quedará asociado a tu cuenta y a esta versión del reporte.",
    );
    form.append(noteLabel, referenceLabel, submit, help);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (request !== generation || submit.disabled) return;
      const requestNote = note.value.trim();
      const requestReference = reference.value.trim();
      if (!requestNote && !requestReference) {
        status.textContent = "Escribe una nota o agrega una referencia.";
        return;
      }
      submit.disabled = true;
      status.textContent = "Guardando verificación oficial…";
      try {
        const token = await fetch("/csrf", {
          credentials: "same-origin",
          cache: "no-store",
        }).then((response) => {
          if (!response.ok) throw new Error("No se pudo validar la sesión.");
          return response.json();
        });
        const response = await fetch(`/api/my/publications/${item.id}/official-verifications`, {
          method: "POST",
          credentials: "same-origin",
          cache: "no-store",
          headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": token.token },
          body: JSON.stringify({ note: requestNote, reference: requestReference || null }),
        });
        if (!response.ok) throw new Error("No se pudo registrar la verificación oficial.");
        if (request !== generation) return;
        note.value = "";
        reference.value = "";
        status.textContent = "Verificación oficial registrada con fecha y cuenta.";
        await render(item, container);
        await window.RedAuxilioPublicationUI?.loadPublications();
        window.RedAuxilioPublicationUI?.refreshConfirmation();
      } catch (error) {
        if (request === generation) status.textContent = error.message;
      } finally {
        if (request === generation) submit.disabled = false;
      }
    });
    container.append(form);
  }

  window.RedAuxilioOfficialVerification = { render };
})();
