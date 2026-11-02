"use strict";
/** Vigencia pública: solo puede degradar una respuesta guardada al vencer; no altera confianza. */
window.RedAuxilioFreshness = {
  normalize(item, now = Date.now()) {
    const expiry = Date.parse(item.freshnessExpiresAt);
    return {
      ...item,
      freshnessStatus:
        !Number.isFinite(expiry) && item.freshnessStatus === "VIGENTE"
          ? undefined
          : Number.isFinite(expiry) && now > expiry
            ? "DESACTUALIZADA"
            : item.freshnessStatus,
    };
  },
};
