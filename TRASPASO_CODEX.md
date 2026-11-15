# RedAuxilio — traspaso Laguna

Actualizado: 15 de noviembre de 2026 (Colombia). Rama `codex/laguna-fidelidad`, basada en `feat/laguna-shell-home-explore`; el PR #37 sigue abierto como Draft y su base llega a `develop` mediante el PR #36. Verificar estado remoto antes de integrar. No hacer merge ni atribuir aprobación humana inexistente. Los PR #34 y #35 tienen trabajo solapado y no deben mezclarse sin revisar.

## Dirección y alcance

Inicio toma los frames oficiales de Figma `4:248` (390×850) y `6:311` (1366×880); los 09C son auxiliares. Explorar usa `8:559` y `8:648`, pendiente de fidelidad completa. La identidad Laguna sustituye progresivamente la estética anterior en pantallas migradas; `ESTANDARES_INTERFAZ.md` conserva las normas funcionales y de accesibilidad. Mantener Spring Boot, Thymeleaf, Leaflet, sesión, privacidad y PWA.

## Implementado y comprobado

Inicio reproduce sus componentes originales y se revisó en navegador a 390 y 1366 px. Se corrigieron el ancho móvil, la curva de fondo y la cabecera intermedia. Inicio, Explorar, Publicar y Mis reportes ahora se presentan como secciones exclusivas por hash, conservando controladores e ID. «Señalar en mapa» abre Explorar y vuelve al formulario con coordenadas; se comprobó el recorrido real.

La última ampliación incorpora un selector de acceso para Ciudadano, Auditor y Verificador autorizado. El registro público sigue creando solo cuentas ciudadanas. El backend distingue permiso de operador y autorización para verificar publicaciones: seleccionar una opción no concede ninguno. Tras el login, el cliente consulta el permiso vigente; el acceso de auditor abre `/operator` solo si la ruta protegida lo permite. La opción de verificador consulta su permiso y comunica el resultado. Se generalizaron los textos de cuenta. El encabezado tiene ondas y puntos suaves, y los botones responden al puntero; `prefers-reduced-motion` desactiva el movimiento.

Pruebas del bloque actual: 161 JavaScript, Prettier y `mvn verify` con Java 21 aprobados; Maven ejecutó 205 pruebas Java, sin fallos, errores ni omitidas. El navegador local se comprobó en 360, 390, 768 y 1366 px, sin desbordamiento horizontal del diálogo. El servidor temporal usa `http://localhost:61384/`; IntelliJ mantiene otro checkout en 8080.

## Siguiente paso

Publicar el bloque en el PR #37 y comprobar CI del HEAD exacto. La revisión visual humana de Inicio y la fidelidad de Explorar y los demás módulos siguen pendientes. No inventar validación humana ni integrar por cuenta del agente.
