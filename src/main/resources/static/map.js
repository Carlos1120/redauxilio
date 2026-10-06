"use strict";
// Agrupación por celdas de pantalla: al acercar el mapa los grupos se separan.
function groupMapPoints(items, project, cellSize = 64) {
  const cells = new Map();
  for (const item of items) {
    if (!Number.isFinite(item.latitude) || !Number.isFinite(item.longitude)) continue;
    const point = project(item);
    const key = Math.floor(point.x / cellSize) + ":" + Math.floor(point.y / cellSize);
    if (!cells.has(key)) cells.set(key, []);
    cells.get(key).push(item);
  }
  return Array.from(cells.values());
}
if (typeof module !== "undefined") module.exports = { groupMapPoints };
if (typeof window !== "undefined") {
  window.RedAuxilioMap = {
    create(onMove, onDetail, labels) {
      const status = document.getElementById("map-status");
      if (!window.L) {
        status.textContent = "El mapa no pudo cargarse. Puedes consultar los reportes en la lista.";
        return null;
      }
      const map = L.map("report-map", { scrollWheelZoom: false, worldCopyJump: true }).setView(
        [4.145, -73.625],
        13,
      );
      const tileLayer = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).addTo(map);
      tileLayer.on("tileerror", () => {
        status.textContent =
          "El fondo cartográfico no está disponible. Consulta la lista y las ubicaciones de los reportes.";
      });
      tileLayer.on("load", () => {
        status.textContent = "Ubicaciones ficticias. El fondo cartográfico requiere conexión.";
      });
      const layer = L.layerGroup().addTo(map);
      let items = [];
      const symbols = { ASSISTANCE: "+", ROAD: "↔", MISSING_PERSON: "?", HELP_REQUEST: "!" };
      function redraw() {
        layer.clearLayers();
        const groups = groupMapPoints(items, (item) =>
          map.project([item.latitude, item.longitude]),
        );
        for (const group of groups) {
          const first = group[0];
          const grouped = group.length > 1;
          const center = group.reduce(
            (p, item) => [
              p[0] + item.latitude / group.length,
              p[1] + item.longitude / group.length,
            ],
            [0, 0],
          );
          const popup = document.createElement("div");
          popup.className = "map-summary";
          const heading = document.createElement("strong");
          heading.textContent = grouped ? group.length + " reportes cercanos" : first.title;
          popup.append(heading);
          if (grouped) {
            const zoom = document.createElement("button");
            zoom.type = "button";
            zoom.textContent = "Acercar al grupo";
            zoom.addEventListener("click", () =>
              map.fitBounds(
                group.map((item) => [item.latitude, item.longitude]),
                { maxZoom: 18, padding: [40, 40], animate: false },
              ),
            );
            popup.append(zoom);
          }
          for (const item of group) {
            const text = document.createElement("p");
            text.textContent =
              labels[item.category] + " · " + item.operationalStatus + " · " + item.confidenceLevel;
            const detail = document.createElement("button");
            detail.type = "button";
            detail.textContent = grouped ? item.title : "Ver detalle";
            detail.addEventListener("click", () => onDetail(item, detail));
            popup.append(text, detail);
          }
          L.marker(center, {
            title: grouped
              ? group.length + " reportes cercanos"
              : labels[first.category] + ": " + first.title,
            alt: grouped ? "Grupo de reportes" : labels[first.category],
            icon: L.divIcon({
              className: "report-marker",
              html: grouped ? String(group.length) : symbols[first.category],
              iconSize: [44, 44],
              iconAnchor: [22, 22],
            }),
          })
            .bindPopup(popup, { maxWidth: 320, maxHeight: 260 })
            .addTo(layer);
        }
      }
      map.on("zoomend", redraw);
      map.on("moveend", onMove);
      return {
        render(results) {
          items = results;
          redraw();
        },
        bounds() {
          const b = map.getBounds();
          // No filtro por área si el usuario está viendo el mundo completo.
          if (b.getEast() - b.getWest() >= 360) return {};
          const wrap = (longitude) => ((((longitude + 180) % 360) + 360) % 360) - 180;
          return {
            south: Math.max(-90, b.getSouth()),
            west: wrap(b.getWest()),
            north: Math.min(90, b.getNorth()),
            east: wrap(b.getEast()),
          };
        },
        reset() {
          map.setView([4.145, -73.625], 13, { animate: false });
        },
      };
    },
  };
}
