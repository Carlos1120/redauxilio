"use strict";
/** Actualiza el token CSRF desde el servidor antes de enviar formularios conservados offline. */
for (const form of document.querySelectorAll('form[method="post"]')) {
  form.addEventListener("submit", async (event) => {
    if (form.dataset.csrfFresh === "true") {
      delete form.dataset.csrfFresh;
      return;
    }
    event.preventDefault();
    if (form.dataset.csrfRefreshInProgress === "true") return;
    form.dataset.csrfRefreshInProgress = "true";
    try {
      const response = await fetch("/csrf", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (!response.ok) throw new Error("No fue posible actualizar el formulario.");
      const token = await response.json();
      let field = form.querySelector(`input[name="${token.parameterName}"]`);
      if (!field) {
        field = document.createElement("input");
        field.type = "hidden";
        field.name = token.parameterName;
        form.append(field);
      }
      field.value = token.token;
      form.dataset.csrfFresh = "true";
      delete form.dataset.csrfRefreshInProgress;
      if (event.submitter) form.requestSubmit(event.submitter);
      else form.requestSubmit();
    } catch {
      delete form.dataset.csrfRefreshInProgress;
      const message = document.getElementById("identity-csrf-message");
      message.textContent = navigator.onLine
        ? "No se pudo verificar el formulario. Intenta de nuevo."
        : "Conéctate a Internet para iniciar sesión, registrarte o cerrar sesión.";
      message.hidden = false;
    }
  });
}
