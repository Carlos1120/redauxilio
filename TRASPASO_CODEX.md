# RedAuxilio — traspaso Laguna

Actualizado: 16 de noviembre de 2026 (Colombia). Rama activa `codex/laguna-verifier`, creada desde `codex/laguna-operator` (`795b544`). El PR #39 de operador sigue abierto y apilado sobre PR #38. Verificar otra vez las bases y el estado remoto antes de integrar. No atribuir aprobación humana ni hacer merge por este bloque.

## Dirección y alcance

Inicio sigue los frames oficiales de Figma `4:248` móvil y `6:311` escritorio; los 09C son auxiliares. Explorar usa `8:559` y `8:648`, con fidelidad completa pendiente. La identidad Laguna se aplica sin reemplazar Spring Boot, Thymeleaf, Leaflet, sesión, privacidad, PWA ni moderación. Consultar `ESTANDARES_INTERFAZ.md`; `RA08.md` y `RA13.md` rigen los roles y la verificación.

## Estado comprobado

Inicio, Explorar, Publicar y Mis reportes son secciones exclusivas. Auditorías abre `/operator` solo con sesión y permiso SQL vigente; la ruta privada vuelve a comprobarlo por petición. Su cabecera coincide con Inicio en medidas y controles, y el cierre de sesión está en Cuenta. PR #39, HEAD `795b544`: 205 pruebas Java, 162 JS, formato y CI exitosos; requiere revisión visual humana. El service worker v32 no guarda respuestas privadas.

En esta rama, el acceso Verificador autorizado abre `#verifier-workspace`: lista de publicaciones públicas, búsqueda y formulario existente de verificación oficial. El permiso se consulta al entrar y la API lo exige de nuevo al guardar. El selector de acceso no concede ni retira roles; una cuenta con dos permisos conserva ambas capacidades. La cabecera y barra móvil ahora muestran Verificaciones en modo verificador y ocultan Auditorías; en modo auditor presentan Auditorías. Cada destino exige permiso vigente. El vínculo de regreso también está en Mi cuenta. Los avisos usan teal para estados normales y ámbar para errores; cerrar sesión produce un solo aviso. El shell PWA pasa a v34; las escrituras y respuestas privadas siguen fuera de caché.

La demo aislada usa `http://127.0.0.1:61385/` y una H2 externa a Git. Su cuenta ficticia posee ambos permisos; la contraseña no se versiona. IntelliJ en `8080` usa otro checkout y otra base. En navegador se comprobó el acceso de verificador, la lista y el formulario a 1280 y 393 px, además del aviso único tras salir. La nueva navegación tiene pruebas JS dirigidas; falta revisión visual del SHA final.

## Siguiente paso

Completar Maven y revisión responsive de la navegación, publicar el ajuste en PR #40 apilado sobre #39 y revisar CI. Someter la vista del verificador a revisión visual del compañero; no cerrar la transición Laguna ni fusionar sin el proceso de integración.
