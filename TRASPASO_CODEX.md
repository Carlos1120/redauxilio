# RedAuxilio — traspaso Laguna

Actualizado: 18 de noviembre de 2026 (Colombia). Carpeta: `C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio`. Rama de trabajo: `codex/render-live-work`, propuesta en el PR borrador #44 hacia `develop`. La versión de respaldo quedó aislada en `codex/backup-redauxilio-2026-11-18` y PR #43; no modificarla. No hay autorización pendiente de Carlos. Comprobar estado remoto antes de publicar o integrar.

## Estado vigente

La aplicación Spring Boot sirve Thymeleaf, JavaScript y CSS desde un solo servicio. El backend de Render está activo con PostgreSQL de Render; el frontend separado en Vercel sigue siendo una posibilidad futura, no parte de esta rama. La copia local se muestra en `http://localhost:8091/` con H2 temporal. El servicio local se inicia con `PORT=8091` y `DB_URL=jdbc:h2:mem:redauxilio_preview;MODE=PostgreSQL`.

Inicio, Explorar, Mis reportes, Cuenta, moderación, verificación y Encontrar personas conservan sus rutas. Los permisos de auditor y verificador están separados. La cabecera y la barra móvil son compartidas. El catálogo de demostración contiene nueve reportes en ubicaciones urbanas de Villavicencio con las imágenes entregadas por Santiago. Encontrar personas conserva dos casos preparados, Jesús Peña y Santiago Ortiz; compara huellas de archivos en memoria, sin reconocimiento facial real.

## Trabajo actual: Publicar

La vista Publicar toma como referencia la selección de categorías de Figma `46:1579` y los pasos de escritorio `38:896`, `38:1016`, `38:1121`; los móviles corresponden a `38:839`, `38:961`, `38:1079`. La elección inicial tiene cuatro categorías. En el paso 1 se repiten como tarjetas compactas y se resalta la elegida. Se conservan los identificadores y controladores necesarios para la API de publicación, junto con los pasos de ubicación y revisión. Los cuatro iconos del paso 1 se descargaron de Figma a `laguna-assets/publish-*.svg`.

La falta de respuesta al pulsar categorías se debió a que la ruta `/publish-steps.js` no estaba permitida por `IdentitySecurityConfiguration`: se entregaba HTML en lugar del script. La ruta quedó autorizada y se añadió al shell PWA, caché v44. En localhost se comprobó que al elegir Vía afectada se selecciona `ROAD`, se exige título y descripción, el mapa marca coordenadas y el paso final resume los datos. La ruta del script devuelve HTTP 200 con `text/javascript`. `IdentitySecurityTests`: 17 casos; pruebas JS dirigidas: 9 casos; cero fallos. No se hizo una publicación real ni un despliegue de esta rama.

## Pendiente inmediato

Completar cotejo visual a 390 y 1366 px, especialmente campos de ubicación y revisión, y verificar una publicación de extremo a extremo con sesión. El primer CI del PR #44 falló en Prettier por `map.js`; se aplicó formato y `npm run format:check` pasó localmente. Revisar CI del nuevo HEAD. No fusionar ni desplegar el PR antes de terminar esa revisión. Mantener las credenciales solo en Render; nunca copiarlas a Git ni a evidencias.
