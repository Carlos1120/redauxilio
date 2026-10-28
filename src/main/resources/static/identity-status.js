"use strict";
/**
 * Mantiene por separado el estado de conexión y la sesión; al reconectar confirma /account.
 */
(() => {
  const trigger = document.getElementById("identity-open");
  const connectionIndicator = document.getElementById("connection-indicator");
  const liveStatus = document.getElementById("connection");
  const accountName = document.getElementById("account-name");
  const accountEmail = document.getElementById("account-email");
  const accountMessage = document.getElementById("account-load-message");
  if (!trigger || !connectionIndicator || !liveStatus) return;

  let sessionVersion = 0;

  function updateStatus() {
    const online = navigator.onLine;
    connectionIndicator.dataset.online = String(online);
    liveStatus.textContent = online
      ? "Conexión online; el indicador de conexión está verde."
      : "Sin conexión; el indicador de conexión está rojo. Consulta la caché.";
  }

  function updateAccountLabel() {
    trigger.setAttribute(
      "aria-label",
      trigger.dataset.authenticated === "true"
        ? "Cuenta ciudadana. Sesión iniciada. Abrir cuenta para cerrar sesión."
        : "Cuenta ciudadana. Sin sesión iniciada. Abrir acceso ciudadano.",
    );
  }

  /** Carga los datos visibles del perfil únicamente desde la sesión actual. */
  async function loadAccountProfile() {
    const version = sessionVersion;
    if (trigger.dataset.authenticated !== "true") {
      accountName.textContent = "";
      accountEmail.textContent = "";
      accountMessage.textContent = "Tu cuenta ciudadana está activa.";
      return;
    }
    accountMessage.textContent = "Cargando perfil…";
    try {
      const response = await fetch("/api/account", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
      if (version !== sessionVersion) return;
      if (response.status === 401 || response.status === 403 || response.redirected) {
        window.RedAuxilioIdentityStatus.setAuthenticated(false);
        return;
      }
      if (!response.ok) throw new Error("Perfil no disponible.");
      const profile = await response.json();
      if (version !== sessionVersion) return;
      accountName.textContent = profile.displayName;
      accountEmail.textContent = profile.email;
      accountMessage.textContent = "Tu cuenta ciudadana está activa.";
    } catch {
      if (version !== sessionVersion) return;
      accountName.textContent = "";
      accountEmail.textContent = "";
      accountMessage.textContent = "No fue posible cargar el perfil. Intenta de nuevo.";
    }
  }

  /** Confirma por una ruta protegida si la sesión aún es válida después de usar la copia offline. */
  async function confirmSessionAfterReconnect() {
    const version = sessionVersion;
    try {
      const response = await fetch("/account", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "text/html" },
      });
      if (version !== sessionVersion) return;
      const destination = new URL(response.url);
      window.RedAuxilioIdentityStatus.setAuthenticated(
        response.ok && destination.searchParams.get("access") === "account",
      );
    } catch {
      if (version === sessionVersion) window.RedAuxilioIdentityStatus.setAuthenticated(false);
    }
  }

  window.addEventListener("online", () => {
    updateStatus();
    // Oculta datos anteriores mientras se confirma la identidad, incluso al cambiar de cuenta.
    window.RedAuxilioIdentityStatus.setAuthenticated(false);
    void confirmSessionAfterReconnect();
  });
  window.addEventListener("offline", updateStatus);
  window.RedAuxilioIdentityStatus = {
    refresh: updateStatus,
    setAuthenticated(authenticated) {
      sessionVersion++;
      trigger.dataset.authenticated = String(authenticated);
      updateAccountLabel();
      accountName.textContent = "";
      accountEmail.textContent = "";
      window.dispatchEvent(
        new CustomEvent("redauxilio:identity-change", {
          detail: { authenticated: Boolean(authenticated) },
        }),
      );
      void loadAccountProfile();
    },
  };
  updateStatus();
  updateAccountLabel();
  if (trigger.dataset.authenticated === "true") void loadAccountProfile();
})();
