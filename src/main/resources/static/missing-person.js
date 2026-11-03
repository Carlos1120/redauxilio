"use strict";
/** Convierte fechas locales sin truncar silenciosamente la precisión de una versión ya persistida. */
window.RedAuxilioMissingPerson = {
  setSeenTime(input, instant) {
    if (!instant) {
      input.value = "";
      input.dataset.originalInstant = "";
      input.dataset.originalLocal = "";
      return;
    }
    const date = new Date(instant);
    input.value = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, -1);
    input.dataset.originalInstant = instant;
    input.dataset.originalLocal = input.value;
  },
  readSeenTime(input) {
    if (input.value && input.value === input.dataset.originalLocal && input.dataset.originalInstant)
      return input.dataset.originalInstant;
    const date = new Date(input.value);
    return input.value && Number.isFinite(date.getTime()) ? date.toISOString() : null;
  },
};
