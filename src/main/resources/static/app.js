"use strict";
const getElement = (id) => document.getElementById(id);
const categoryLabels = {
  ASSISTANCE: "Punto de asistencia",
  ROAD: "Vía afectada",
  MISSING_PERSON: "Persona desaparecida",
  HELP_REQUEST: "Solicitud de ayuda",
};
let latestPublicationQueryId = 0;
function updateConnectionStatus() {
  getElement("connection").textContent = navigator.onLine
    ? "Conexión de red disponible"
    : "Sin conexión · datos guardados";
}
window.addEventListener("online", updateConnectionStatus);
window.addEventListener("offline", updateConnectionStatus);
updateConnectionStatus();
function renderPublications(items) {
  getElement("publications").replaceChildren();
  for (const item of items) {
    const card = document.createElement("article");
    card.className = "card";
    for (const [tag, value] of [
      ["span", categoryLabels[item.category]],
      ["h3", item.title],
      ["p", item.location],
      ["p", `${item.operationalStatus} · ${item.confidenceLevel}`],
      ["p", `Fecha del reporte: ${new Date(item.updatedAt).toLocaleString("es-CO")}`],
    ]) {
      const el = document.createElement(tag);
      el.textContent = value;
      if (tag === "span") el.className = "tag";
      card.append(el);
    }
    getElement("publications").append(card);
  }
}
async function loadPublications() {
  const query = ++latestPublicationQueryId;
  getElement("message").textContent = "Consultando…";
  try {
    const response = await fetch(
      `/api/publications?category=${encodeURIComponent(getElement("category").value)}`,
    );
    if (!response.ok) throw new Error("Consulta rechazada");
    const items = await response.json();
    if (query !== latestPublicationQueryId) return;
    renderPublications(items);
    const savedAt = response.headers.get("X-RedAuxilio-Saved-At");
    getElement("message").textContent = savedAt
      ? `Consulta almacenada el ${new Date(savedAt).toLocaleString("es-CO")}; puede estar desactualizada. ${items.length} reportes.`
      : `${items.length} reportes ficticios encontrados. Datos obtenidos del servidor.`;
  } catch {
    if (query === latestPublicationQueryId) {
      renderPublications([]);
      getElement("message").textContent =
        "No se pudo consultar. La categoría podría no tener una consulta guardada. Intenta de nuevo con conexión.";
    }
  }
}
getElement("category").addEventListener("change", loadPublications);
getElement("refresh").addEventListener("click", loadPublications);
const database = new Promise((resolve, reject) => {
  const request = indexedDB.open("redauxilio-demo", 1);
  request.onupgradeneeded = () => request.result.createObjectStore("drafts");
  request.onsuccess = () => resolve(request.result);
  request.onerror = () => reject(request.error);
});
async function executeDraftOperation(mode, operation) {
  const db = await database;
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("drafts", mode);
    const request = operation(transaction.objectStore("drafts"));
    let result;
    request.onsuccess = () => {
      result = request.result;
    };
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
getElement("draft-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await executeDraftOperation("readwrite", (store) =>
      store.put(
        {
          text: getElement("draft-text").value,
          latitude: getElement("latitude").value,
          longitude: getElement("longitude").value,
        },
        "current",
      ),
    );
    getElement("draft-message").textContent =
      "Borrador guardado localmente. No se ha enviado al servidor.";
  } catch {
    getElement("draft-message").textContent =
      "No se pudo guardar. Conserva el texto antes de cerrar esta página.";
  }
});
getElement("discard").addEventListener("click", async () => {
  try {
    await executeDraftOperation("readwrite", (store) => store.delete("current"));
    getElement("draft-form").reset();
    getElement("draft-message").textContent = "Borrador local descartado.";
  } catch {
    getElement("draft-message").textContent = "No se pudo descartar el borrador.";
  }
});
executeDraftOperation("readonly", (store) => store.get("current"))
  .then((draft) => {
    if (draft) {
      getElement("draft-text").value = draft.text;
      getElement("latitude").value = draft.latitude;
      getElement("longitude").value = draft.longitude;
      getElement("draft-message").textContent = "Borrador local recuperado; no está publicado.";
    }
  })
  .catch(() => {
    getElement("draft-message").textContent = "Almacenamiento local no disponible.";
  });
if ("serviceWorker" in navigator)
  navigator.serviceWorker.register("/sw.js").catch(() => {
    getElement("connection").textContent += " · caché offline no disponible";
  });
loadPublications();
