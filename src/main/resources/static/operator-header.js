"use strict";
/** Mantiene el indicador de red del encabezado privado y cierra el menú al salir de él. */
(() => {
  const indicator = document.getElementById("connection-indicator");
  const status = document.getElementById("connection");
  const account = document.querySelector(".operator-account-menu");
  if (!indicator || !status || !account) return;

  function updateConnection() {
    const online = navigator.onLine;
    indicator.dataset.online = String(online);
    status.textContent = online
      ? "Conexión online; el indicador de conexión está verde."
      : "Sin conexión; el indicador de conexión está rojo.";
    if (!online) account.open = false;
  }
  window.addEventListener("online", updateConnection);
  window.addEventListener("offline", updateConnection);
  document.addEventListener("click", (event) => {
    if (account.open && !account.contains(event.target)) account.open = false;
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") account.open = false;
  });
  updateConnection();
})();
