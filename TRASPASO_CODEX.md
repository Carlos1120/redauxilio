# RedAuxilio — traspaso Laguna

Actualizado: 18 de noviembre de 2026 (Colombia). Carpeta verificada: `C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio`. La rama activa para corregir Docker es `codex/fix-render-docker-build`, HEAD `c8ec832`, PR #42 hacia `develop`; `origin` apunta a `Carlos1120/redauxilio`. El PR #41 ya está integrado en `develop` (`80baf5c`). No integrar el PR #42 hasta completar revisión/CI según el flujo del repositorio.

## Estado vigente

La transición Laguna conserva Inicio, Explorar, Mis reportes, publicación, cuenta, moderación y verificación, con permisos separados. Inicio sigue los frames oficiales de Figma `4:248` móvil y `6:311` escritorio. La cabecera usa cuadrícula fluida y navegación contextual: el verificador no ve Auditorías y el auditor no ve Verificaciones. Por debajo de 1200 px permanece la barra inferior de cinco destinos.

Como ampliación acotada de RA-09 existe **Encontrar personas**. La pantalla admite de una a tres fotos y presenta coincidencias preparadas. Las imágenes se procesan en memoria y no se guardan. El servidor compara huellas SHA-256; no incorpora reconocimiento facial ni IA. El catálogo contiene los casos autorizados de Jesús Peña (94 %) y Santiago Ortiz (91 %), con tres referencias y una comparación separada por persona. La API exige sesión, limita formatos y tamaños, y responde `no-store`.

La carga de marca funciona en escritorio y móvil y respeta `prefers-reduced-motion`. El diálogo de acceso no cubre Inicio. `app-loader.js` forma parte del shell PWA y la caché vigente es `redauxilio-demo-v42`.

El catálogo público suma nueve reportes de demostración persistidos en SQL y repartidos dentro del perímetro urbano de Villavicencio: tres alojamientos, tres afectaciones viales y tres puntos de ayuda o acopio. Cada reporte incorpora una de las imágenes entregadas por Santiago el 17 de noviembre. `DemoPublicationSeeder` carga `demo-publications/reports.json`, valida los campos con las reglas del dominio, sanitiza la evidencia con el servicio RA-12 y actualiza por título sin duplicar registros. La cuenta técnica no tiene una contraseña conocida y el sembrado puede desactivarse con `DEMO_REPORTS_ENABLED=false`.

## Comprobaciones

Suite Node completa: **178 pruebas**, cero fallos. `mvn verify` con Java 21 ejecutó **210 pruebas**, cero fallos; el empaquetado, Spotless y PMD finalizaron y generaron `redauxilio-0.1.0-SNAPSHOT.jar`. La regresión de reportes demuestra nueve altas, nueve evidencias y repetición idempotente; una comprobación HTTP obtuvo las nueve imágenes y confirmó sus coordenadas urbanas. Las pruebas de Encontrar personas verifican ambos casos, referencias separadas, descarga protegida y ausencia de persistencia de cargas. `git diff --check`: aprobado.

La vista aislada debe reiniciarse con H2 temporal para entregar la caché v42 y revisar visualmente los reportes nuevos.

## Siguiente paso

Revisar el mapa, filtros, tarjetas e imágenes de los nueve reportes. Para despliegue, mantener el monolito Spring Boot/Thymeleaf en un único servicio Render y conectarlo a PostgreSQL administrado; separar el frontend hacia Vercel solo requeriría una migración posterior de arquitectura, CORS, sesión, CSRF y PWA. El Dockerfile se corrigió tras comprobar que `eclipse-temurin:21-jre` fallaba con `exec format error` en Docker Desktop y que el wrapper Maven intentaba descargar una distribución innecesaria. El build `docker build --progress plain --platform linux/amd64 -t redauxilio:demo .` finalizó correctamente con Maven/Temurin 21 como build y runtime; `git diff --check` también pasó. La imagen local es `redauxilio:demo`. PR #42 contiene este ajuste; falta CI/revisión e integración. Después crear el Web Service de Render desde GitHub, configurar PostgreSQL y variables según README, desplegar y comprobar salud/rutas y publicación compartida en tiempo real. Aún no se creó ningún recurso ni se cargaron secretos en Render.
