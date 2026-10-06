"use strict";
const $ = (id) => document.getElementById(id);
const labels = {ASSISTANCE:"Punto de asistencia",ROAD:"Vía afectada",MISSING_PERSON:"Persona desaparecida",HELP_REQUEST:"Solicitud de ayuda"};
let lastQuery = 0;
function connection() { $("connection").textContent = navigator.onLine ? "Conexión de red disponible" : "Sin conexión · datos guardados"; }
window.addEventListener("online", connection); window.addEventListener("offline", connection); connection();
function render(items) {
  $("publications").replaceChildren();
  for (const item of items) {
    const card = document.createElement("article"); card.className = "card";
    for (const [tag, value] of [["span",labels[item.category]],["h3",item.title],["p",item.location],["p",`${item.status} · ${item.confidence}`],["p",`Fecha del reporte: ${new Date(item.updatedAt).toLocaleString("es-CO")}`]]) {
      const el = document.createElement(tag); el.textContent = value; if (tag === "span") el.className = "tag"; card.append(el);
    }
    $("publications").append(card);
  }
}
async function load() {
  const query = ++lastQuery;
  $("message").textContent = "Consultando…";
  try {
    const response = await fetch(`/api/publications?category=${encodeURIComponent($("category").value)}`);
    if (!response.ok) throw new Error("Consulta rechazada");
    const items = await response.json(); if(query !== lastQuery) return; render(items);
    const savedAt = response.headers.get("X-RedAuxilio-Saved-At");
    $("message").textContent = savedAt ? `Consulta almacenada el ${new Date(savedAt).toLocaleString("es-CO")}; puede estar desactualizada. ${items.length} reportes.` : `${items.length} reportes ficticios encontrados. Datos obtenidos del servidor.`;
  } catch { if(query === lastQuery) { render([]); $("message").textContent = "No se pudo consultar. La categoría podría no tener una consulta guardada. Intenta de nuevo con conexión."; } }
}
$("category").addEventListener("change", load); $("refresh").addEventListener("click", load);
const database = new Promise((resolve,reject) => {
  const request = indexedDB.open("redauxilio-demo",1);
  request.onupgradeneeded = () => request.result.createObjectStore("drafts");
  request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error);
});
async function draftOperation(mode, operation) {
  const db = await database;
  return new Promise((resolve,reject) => {
    const transaction = db.transaction("drafts",mode);
    const request = operation(transaction.objectStore("drafts")); let result;
    request.onsuccess = () => { result = request.result; };
    transaction.oncomplete = () => resolve(result);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}
$("draft-form").addEventListener("submit",async (event) => {
  event.preventDefault();
  try { await draftOperation("readwrite",store => store.put({text:$("draft-text").value,latitude:$("latitude").value,longitude:$("longitude").value},"current")); $("draft-message").textContent = "Borrador guardado localmente. No se ha enviado al servidor."; }
  catch { $("draft-message").textContent = "No se pudo guardar. Conserva el texto antes de cerrar esta página."; }
});
$("discard").addEventListener("click", async () => {
  try { await draftOperation("readwrite",store => store.delete("current")); $("draft-form").reset(); $("draft-message").textContent = "Borrador local descartado."; }
  catch { $("draft-message").textContent = "No se pudo descartar el borrador."; }
});
draftOperation("readonly",store => store.get("current")).then(draft => { if(draft) { $("draft-text").value=draft.text; $("latitude").value=draft.latitude; $("longitude").value=draft.longitude; $("draft-message").textContent="Borrador local recuperado; no está publicado."; }}).catch(() => {$("draft-message").textContent="Almacenamiento local no disponible.";});
if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {$("connection").textContent += " · caché offline no disponible";});
load();
