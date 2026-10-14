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
      if (!response.ok) throw new Error("Perfil no disponible.");
      const profile = await response.json();
      accountName.textContent = profile.displayName;
      accountEmail.textContent = profile.email;
      accountMessage.textContent = "Tu cuenta ciudadana está activa.";
    } catch {
      accountName.textContent = "";
      accountEmail.textContent = "";
      accountMessage.textContent = "No fue posible cargar el perfil. Intenta de nuevo.";
    }
  }

  /** Confirma por una ruta protegida si la sesión aún es válida después de usar la copia offline. */
  async function confirmSessionAfterReconnect() {
    try {
      const response = await fetch("/account", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "text/html" },
      });
      const destination = new URL(response.url);
      window.RedAuxilioIdentityStatus.setAuthenticated(
        response.ok && destination.searchParams.get("access") === "account",
      );
    } catch {
      window.RedAuxilioIdentityStatus.setAuthenticated(false);
    }
  }

  window.addEventListener("online", () => {
    updateStatus();
    void confirmSessionAfterReconnect();
  });
  window.addEventListener("offline", updateStatus);
  window.RedAuxilioIdentityStatus = {
    refresh: updateStatus,
    setAuthenticated(authenticated) {
      trigger.dataset.authenticated = String(authenticated);
      updateAccountLabel();
      void loadAccountProfile();
    },
  };
  updateStatus();
  updateAccountLabel();
  if (trigger.dataset.authenticated === "true") void loadAccountProfile();
})();
