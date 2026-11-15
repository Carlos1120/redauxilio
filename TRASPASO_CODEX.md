# RedAuxilio — traspaso Laguna

Actualizado: 15 de noviembre de 2026 (Colombia). El PR #36 se integró en `develop` y el PR #37 se integró en `feat/laguna-shell-home-explore`; esta rama contiene trabajo posterior a #37. La rama activa es `codex/laguna-access-motion`, creada desde el HEAD de esa rama Laguna, y conserva solo el nuevo bloque de acceso y movimiento. La rama previa `codex/laguna-fidelidad` permanece publicada; no usarla directamente para un PR hacia `develop` porque la base diverge. Comprobar el remoto antes de integrar. No hacer merge sin autorización ni atribuir aprobación humana inexistente.

## Dirección y alcance

Inicio toma los frames oficiales de Figma `4:248` (390×850) y `6:311` (1366×880); los 09C son auxiliares. Explorar usa `8:559` y `8:648`, pendiente de fidelidad completa. La identidad Laguna sustituye progresivamente la estética anterior en pantallas migradas; `ESTANDARES_INTERFAZ.md` conserva las normas funcionales y de accesibilidad. Mantener Spring Boot, Thymeleaf, Leaflet, sesión, privacidad y PWA.

## Implementado y comprobado

Inicio conserva componentes originales; su ancho móvil, curva de fondo y cabecera intermedia se corrigieron. Inicio, Explorar, Publicar y Mis reportes se presentan como secciones exclusivas por hash, conservando controladores e ID. «Señalar en mapa» abre Explorar y vuelve al formulario con coordenadas; se comprobó el recorrido real en el navegador.

El bloque actual incorpora un selector de acceso para Ciudadano, Auditor y Verificador autorizado. El registro público sigue creando solo cuentas ciudadanas. El backend distingue permiso de operador y autorización para verificar publicaciones: seleccionar una opción no concede ninguno. Tras el login, el cliente consulta el permiso vigente; Auditor abre `/operator` solo si la ruta protegida lo permite. Verificador consulta su permiso y comunica el resultado. Los textos de cuenta se generalizaron. La cabecera tiene ondas y puntos suaves, y los botones responden al puntero; `prefers-reduced-motion` desactiva el movimiento.

Pruebas del bloque: 161 JavaScript, Prettier y `mvn verify` con Java 21 aprobados; Maven ejecutó 205 pruebas Java, sin fallos, errores ni omitidas. El navegador local se comprobó en 360, 390, 768 y 1366 px, sin desbordamiento horizontal del diálogo. El servidor temporal usa `http://localhost:61384/`; IntelliJ mantiene otro checkout en 8080.

## Siguiente paso

Publicar la nueva rama mediante PR hacia `feat/laguna-shell-home-explore`, comprobar CI del HEAD exacto y continuar la revisión visual. La fidelidad de Explorar y los demás módulos sigue pendiente. No inventar validación humana ni integrar por cuenta del agente.
