"use strict";
/** Actualiza el token CSRF desde el servidor antes de enviar formularios conservados offline. */
/** Envía login/logout con CSRF fresco y emite el cambio de sesión al aceptar la redirección esperada. */
async function submitIdentityFormWithoutReload(form) {
  const path = new URL(form.action, window.location.origin).pathname;
  const body = new URLSearchParams(new FormData(form));
  const response = await fetch(form.action, {
    method: "POST",
    credentials: "same-origin",
    cache: "no-store",
    headers: { Accept: "text/html" },
    body,
  });
  const destination = new URL(response.url);
  const access = destination.searchParams.get("access");
  const message = document.getElementById("identity-csrf-message");

  if (path === "/login" && access === "account" && response.ok) {
    form.reset();
    window.dispatchEvent(
      new CustomEvent("redauxilio:identity-change", {
        detail: { authenticated: true, message: "Sesión iniciada." },
      }),
    );
  } else if (path === "/logout" && destination.searchParams.has("signedout") && response.ok) {
    window.dispatchEvent(
      new CustomEvent("redauxilio:identity-change", {
        detail: { authenticated: false, message: "Cerraste la sesión." },
      }),
    );
  } else if (path === "/login" && destination.searchParams.has("error")) {
    const password = form.querySelector('input[type="password"]');
    if (password) password.value = "";
    message.textContent = "No fue posible iniciar sesión. Revisa tus datos o intenta de nuevo.";
    message.hidden = false;
  } else {
    throw new Error("No se pudo completar la acción de acceso.");
  }
}

for (const form of document.querySelectorAll('form[method="post"]')) {
  form.addEventListener("submit", async (event) => {
    if (form.dataset.csrfFresh === "true") {
      delete form.dataset.csrfFresh;
      if (form.action?.endsWith("/login") || form.action?.endsWith("/logout")) {
        event.preventDefault();
        await submitIdentityFormWithoutReload(form).catch(() => {
          const message = document.getElementById("identity-csrf-message");
          message.textContent =
            "No se pudo completar la acción. Revisa tu conexión e intenta de nuevo.";
          message.hidden = false;
        });
      }
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
      if (event.submitter) await form.requestSubmit(event.submitter);
      else await form.requestSubmit();
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
