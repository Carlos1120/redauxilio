# RedAuxilio — traspaso Laguna

Actualizado: 18 de noviembre de 2026 (Colombia). Carpeta verificada: `C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio`. Rama activa: `codex/render-live-work`, nacida de `origin/develop` (`4075cc7`). La copia anterior está aislada en `codex/backup-redauxilio-2026-11-18` (`a0a3b9f`); el PR #43 sigue abierto hacia `develop` y no está integrado. `origin` apunta a `Carlos1120/redauxilio`.

## Estado vigente

La transición Laguna conserva Inicio, Explorar, Mis reportes, publicación, cuenta, moderación y verificación, con permisos separados. Inicio sigue los frames oficiales de Figma `4:248` móvil y `6:311` escritorio. La cabecera usa cuadrícula fluida y navegación contextual: el verificador no ve Auditorías y el auditor no ve Verificaciones. Por debajo de 1200 px permanece la barra inferior de cinco destinos.

Como ampliación acotada de RA-09 existe **Encontrar personas**. La pantalla admite de una a tres fotos y presenta coincidencias preparadas. Las imágenes se procesan en memoria y no se guardan. El servidor compara huellas SHA-256; no incorpora reconocimiento facial ni IA. El catálogo contiene los casos autorizados de Jesús Peña (94 %) y Santiago Ortiz (91 %), con tres referencias y una comparación separada por persona. La API exige sesión, limita formatos y tamaños, y responde `no-store`.

La carga de marca funciona en escritorio y móvil y respeta `prefers-reduced-motion`. El diálogo de acceso no cubre Inicio. `app-loader.js` forma parte del shell PWA y la caché vigente es `redauxilio-demo-v42`.

El catálogo público suma nueve reportes de demostración persistidos en SQL y repartidos dentro del perímetro urbano de Villavicencio: tres alojamientos, tres afectaciones viales y tres puntos de ayuda o acopio. Cada reporte incorpora una de las imágenes entregadas por Santiago el 17 de noviembre. `DemoPublicationSeeder` carga `demo-publications/reports.json`, valida los campos con las reglas del dominio, sanitiza la evidencia con el servicio RA-12 y actualiza por título sin duplicar registros. La cuenta técnica no tiene una contraseña conocida y el sembrado puede desactivarse con `DEMO_REPORTS_ENABLED=false`.

## Comprobaciones

Suite Node completa: **178 pruebas**, cero fallos. `mvn verify` con Java 21 ejecutó **210 pruebas**, cero fallos; el empaquetado, Spotless y PMD finalizaron y generaron `redauxilio-0.1.0-SNAPSHOT.jar`. La regresión de reportes demuestra nueve altas, nueve evidencias y repetición idempotente; una comprobación HTTP obtuvo las nueve imágenes y confirmó sus coordenadas urbanas. Las pruebas de Encontrar personas verifican ambos casos, referencias separadas, descarga protegida y ausencia de persistencia de cargas. `git diff --check`: aprobado.

La vista aislada debe reiniciarse con H2 temporal para entregar la caché v42 y revisar visualmente los reportes nuevos.

## Siguiente paso

La corrección actual alinea el ancho de Explorar/Publicar con Figma Laguna y conecta `#draft-section` al formulario; elimina la cabecera duplicada que interfería con la vista. Para Publicar, Santiago precisó como fuente la página Figma `38:170`, variante Laguna (`38:839`, `38:896`, `38:961`, `38:1016`, `38:1079`, `38:1121`): tres pasos ¿Qué pasa?, Lugar y Revisar, en móvil 390 y escritorio 1366. La implementación actual todavía no reproduce ese flujo. Comprobación puntual: sintaxis de `app.js` y `map.js` y `git diff --check`. Queda pendiente comparar visualmente en navegador a 390 y 1366 px, completar la estructura por pasos y revisar CI antes de integración. No hay despliegue de esta rama en Render.

Revisar el mapa, filtros, tarjetas e imágenes de los nueve reportes. Para despliegue, mantener el monolito Spring Boot/Thymeleaf en un único servicio Render y conectarlo a PostgreSQL administrado; separar el frontend hacia Vercel solo requeriría una migración posterior de arquitectura, CORS, sesión, CSRF y PWA. El Dockerfile se corrigió tras comprobar que `eclipse-temurin:21-jre` fallaba con `exec format error` en Docker Desktop y que el wrapper Maven intentaba descargar una distribución innecesaria. El build final `docker build --quiet --platform linux/amd64 -t redauxilio:demo .` terminó correctamente con Maven/Temurin 21 como build y runtime. El PR #42 también corrige estructura HTML duplicada/mal cerrada en `index.html`, elimina el `search-query` duplicado y actualiza pruebas JS que fijaban la caché a v42 y un atributo de navegación inexistente. `npm run format:check`, las seis pruebas JS dirigidas y `git diff --check` pasan localmente. CI del commit `bc41974` terminó exitosamente (run #212). La imagen local es `redauxilio:demo`. Después crear el Web Service de Render desde GitHub, configurar PostgreSQL y variables según README, desplegar y comprobar salud/rutas y publicación compartida en tiempo real. Aún no se creó ningún recurso ni se cargaron secretos en Render.
