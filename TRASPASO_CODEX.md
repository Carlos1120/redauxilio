# RedAuxilio — traspaso Laguna

Actualizado: 17 de noviembre de 2026 (Colombia). Rama `codex/laguna-verifier`, creada desde `codex/laguna-operator` (`795b544`). PR #39 sigue abierto y apilado sobre #38. Verificar bases y estado remoto antes de integrar. No atribuir aprobación humana ni hacer merge.

## Dirección y alcance

Inicio sigue los frames de Figma `4:248` móvil y `6:311` escritorio; 09C es auxiliar. Explorar usa `8:559` y `8:648`, aún pendiente. Laguna no reemplaza Spring Boot, Thymeleaf, Leaflet, sesión, privacidad, PWA ni moderación. Consultar `ESTANDARES_INTERFAZ.md`, `RA08.md` y `RA13.md`.

## Estado comprobado

Inicio, Explorar, Publicar y Mis reportes son secciones exclusivas. Auditorías abre `/operator` con sesión y permiso SQL vigente, comprobados por petición. Su cabecera coincide con Inicio y el cierre está en Cuenta. PR #39, HEAD `795b544`: 205 Java, 162 JS, formato y CI exitosos; revisión visual pendiente. El service worker v32 no guarda respuestas privadas.

En esta rama, el acceso Verificador autorizado abre `#verifier-workspace`: lista de publicaciones públicas, búsqueda y formulario existente de verificación oficial. El permiso se consulta al entrar y la API lo exige de nuevo al guardar. El selector de acceso no concede ni retira roles; una cuenta con dos permisos conserva ambas capacidades. La cabecera y barra móvil ahora muestran Verificaciones en modo verificador y ocultan Auditorías; en modo auditor presentan Auditorías. Cada destino exige permiso vigente. El vínculo de regreso también está en Mi cuenta. Los avisos usan teal para estados normales y ámbar para errores; cerrar sesión produce un solo aviso. El shell PWA pasa a v34; las escrituras y respuestas privadas siguen fuera de caché.

El ajuste visual siguiente corrige las categorías de Inicio: escritorio ya no sustituye los cuatro pictogramas por el mismo símbolo. Refugio, vía, personas y ayuda usan símbolos distintos y acentos teal, ámbar, lila y coral tanto en las miniaturas como en los marcadores. El panel de operador conserva Bricolage y reduce únicamente el título principal a un máximo de 44 px; Auditorías y Publicar ganan separación explícita. El shell PWA pasa a v35 y deja de precargar las cuatro miniaturas de escritorio obsoletas.

La demo aislada usa `http://127.0.0.1:61385/` y una H2 externa a Git. Su cuenta ficticia posee ambos permisos; la contraseña no se versiona. IntelliJ en `8080` usa otro checkout y otra base. En navegador se comprobó el acceso de verificador, la lista y el formulario a 1280 y 393 px, además del aviso único tras salir. También se comprobó Inicio con los cuatro pictogramas diferenciados a 1366 px. Maven verify, 165 pruebas JS y pruebas visuales dirigidas pasan; falta revisión visual humana del operador final.

## Siguiente paso

Publicar el ajuste visual en PR #40 apilado sobre #39 y revisar CI del nuevo HEAD. Revisar visualmente el operador con sesión a 390, 768 y 1366 px; no cerrar la transición Laguna ni fusionar sin el proceso de integración.
