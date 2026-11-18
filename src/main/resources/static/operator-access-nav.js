"use strict";
/** Consulta permisos vigentes y muestra el destino del modo de acceso elegido. */
(() => {
  const trigger = document.getElementById("identity-open");
  const operatorDesktop = document.getElementById("operator-nav-link");
  const operatorMobile = document.getElementById("operator-mobile-link");
  const verifierDesktop = document.getElementById("verifier-nav-link");
  const verifierMobile = document.getElementById("verifier-mobile-link");
  const bottom = document.querySelector(".laguna-bottom-nav");
  if (
    !trigger ||
    !operatorDesktop ||
    !operatorMobile ||
    !verifierDesktop ||
    !verifierMobile ||
    !bottom
  )
    return;

  let revision = 0;
  function show(operatorAllowed, verifierAllowed) {
    const mode = sessionStorage.getItem("redauxilio:access-mode");
    const showOperator = operatorAllowed && mode !== "verifier";
    const showVerifier = verifierAllowed && mode !== "operator";
    operatorDesktop.hidden = operatorMobile.hidden = !showOperator;
    verifierDesktop.hidden = verifierMobile.hidden = !showVerifier;
    bottom.classList.toggle("has-role-destination", showOperator || showVerifier);
  }
  async function refresh() {
    const current = ++revision;
    show(false, false);
    if (trigger.dataset.authenticated !== "true" || !navigator.onLine) return;
    try {
      const options = {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      };
      const [operator, verifier] = await Promise.all([
        fetch("/api/my/operator-permission", options),
        fetch("/api/my/verification-permission", options),
      ]);
      if (current !== revision) return;
      const operatorAllowed =
        operator.ok && !operator.redirected && (await operator.json()).canModerate === true;
      const verifierAllowed =
        verifier.ok && !verifier.redirected && (await verifier.json()).canVerify === true;
      if (current === revision && trigger.dataset.authenticated === "true") {
        show(operatorAllowed, verifierAllowed);
      }
    } catch {
      // Si la red falla, el acceso privado queda oculto hasta una nueva comprobación.
    }
  }
  window.addEventListener("redauxilio:identity-change", () => void refresh());
  window.addEventListener("offline", () => {
    revision++;
    show(false, false);
  });
  window.addEventListener("online", () => void refresh());
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) void refresh();
  });
  void refresh();
})();
