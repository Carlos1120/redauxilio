# RedAuxilio — continuidad Laguna

Actualización: 12 de noviembre de 2026 (Colombia). Worktree aislado: `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelidad`, rama `codex/laguna-fidelidad`. PR #37 abierto en Draft hacia `feat/laguna-shell-home-explore` (PR #36); base previa al bloque `3d808092b5448489382de9458a9e0147374cf003`. El commit del bloque es el HEAD de esta rama; comprobar su CI exacto en GitHub. No modificar otros worktrees, servidores ni bases; no hacer merge ni atribuir aceptación humana.

## Módulo activo y referencias

Terminar Inicio antes de avanzar a Explorar. Las únicas referencias oficiales de Inicio son Figma `4:248` (390×850) y `6:311` (1366×880), archivo `jdOBCS8zKaUs3pvxvUGF1y`. Los frames 09C de 360 y 768 px son auxiliares y no deben reproducirse. La adaptación responsive conserva tarjeta blanca de reporte, ilustración, cuatro categorías ilustradas, tarjeta de apoyo, logo y navegación del original. Solar y Azul no se han modificado en Figma.

## Trabajo y evidencia vigente

Los SVG originales, iconos, imágenes y fuentes locales están en `src/main/resources/static/laguna-assets/`; se enlazan desde `index.html` y `laguna.css`. El service worker precarga solo estos recursos públicos y la configuración de seguridad permite su GET anónimo. La navegación conserva IDs, enlaces y acciones de la aplicación; las categorías usan los filtros públicos ya existentes. No hay cambios de reglas de negocio ni datos productivos.

Instancia local de QA: `http://127.0.0.1:61384/`, puerto dedicado y base H2 exclusiva en `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelity-qa/data/fidelity`. El JAR de QA se reconstruyó desde el árbol local del bloque Inicio; identificar su versión por el worktree y no confundirlo con una aceptación humana. Capturas oficiales, capturas reales de Chrome, superposiciones y diferencias están fuera de Git en `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelity-qa/figma/`. La comparación directa midió diferencia media por canal 8,19/255 a 390 px y 5,36/255 a 1366 px. Las cuatro anchuras no mostraron overflow horizontal; el lienzo blanco final quedó capturado. Pruebas JS dirigidas de Laguna: 10/10 PASS; Node completo: 157/157. Java: 205/205 sin fallos; el primer empaquetado falló porque el servidor bloqueó el JAR en Windows. Con el servidor detenido, `mvn -DskipTests verify` aprobó empaquetado, Spotless, PMD y cobertura. Prettier y `git diff --check` aprobaron. El estado de CI se debe verificar sobre el HEAD publicado.

## Siguiente acción

Foco visible, recursos y componentes quedaron comprobados a 360, 390, 768 y 1366 px; la navegación en Chrome activó filtros, Publicar y Explorar sin errores. Verificar CI y revisar Inicio con Carlos antes de pasar a Explorar. Comprobar CI del HEAD publicado. Solicitar revisión visual humana de Inicio antes de pasar a Explorar. Las diferencias residuales de fuentes, barra de desplazamiento y curva blanca móvil se registran en `docs/DISEÑO_LAGUNA_IMPLEMENTACION.md`; no declarar fidelidad absoluta sin evidencia.
