# RedAuxilio — traspaso de trabajo

Actualizado: 19 de noviembre de 2026 (Colombia). Carpeta: C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio. Rama codex/render-live-work, PR borrador #44 hacia develop. La versión de respaldo está aislada en codex/backup-redauxilio-2026-11-18 y PR #43; no modificarla. No se requiere autorización de Carlos para revisar. Verificar el estado remoto antes de integrar.

## Estado de producto

La aplicación Spring Boot entrega Thymeleaf, JavaScript y CSS desde un único servicio. El backend de Render está activo con PostgreSQL de Render; Vercel sigue como posibilidad futura. La copia local está en http://localhost:8091/ con H2 persistente en ./data/redauxilio_preview.mv.db. Se inicia con PORT=8091 y DB_URL=jdbc:h2:file:./data/redauxilio_preview;MODE=PostgreSQL. La base local es distinta de PostgreSQL en Render: sus cuentas y reportes no se comparten automáticamente.

Inicio, Explorar, Publicar, Mis reportes, Cuenta, auditoría, verificación y Encontrar personas conservan sus rutas. Los permisos de auditor y verificador están separados. La cabecera y la barra móvil son compartidas. Hay nueve reportes fotográficos de Villavicencio más cuatro reportes base; Encontrar personas conserva los casos de Jesús Peña y Santiago Ortiz. La comparación de fotos es local y predeterminada, sin reconocimiento facial real.

## Trabajo actual: Explorar y acceso

Explorar usa los frames Laguna 8:559 (móvil 390 px) y 8:648 (portátil 1366 px). El SVG original del mapa es la vista inicial; Leaflet permanece accesible con «Abrir mapa interactivo». La búsqueda, los filtros, la lista y el detalle consultan la API. El mapa oculto producía límites geográficos nulos y dejaba la lista vacía; ahora filtra por zona solo después de una interacción con el mapa real. Las tarjetas compactas se ordenan por fecha de creación. Los recursos están en static/laguna-assets/explore-map-*.svg; el shell PWA pasa a v47.

La instancia local anterior devolvía HTTP 500 a /api/publications; se sustituyó por una base H2 nueva y persistente. La API ahora responde HTTP 200 con trece resultados. Desde el navegador se probó una cuenta ciudadana local: el inicio de sesión abrió «Mi cuenta» con el perfil esperado. También se comprobaron filtro Refugios (siete resultados), detalle, alternancia Mapa/Lista y mapa real con los reportes. El acceso en Render no se verificó en este bloque.

## Verificación y siguiente paso

npm test: 182/182; mvnw test -q: pasó; npm run format:check: pasó. A 390 px, el mapa comienza en y=282 y la primera tarjeta en y=625, sin desbordamiento horizontal. Falta revisar visualmente escritorio con captura completa y probar inicio de sesión con una cuenta de Render. Publicar aún requiere una operación autenticada de extremo a extremo. Revisar CI del HEAD final y actualizar PR #44. No fusionar ni desplegar sin revisión; mantener credenciales fuera del repositorio.
