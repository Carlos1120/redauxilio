# RedAuxilio — traspaso Laguna

Actualizado: 16 de noviembre de 2026 (Colombia). Rama activa `codex/laguna-operator`, PR #39 abierto hacia `codex/laguna-access-motion` (PR #38). Verificar de nuevo los PR y sus bases antes de integrar. No hacer merge ni atribuir revisión humana inexistente.

## Dirección y alcance

Inicio sigue los frames oficiales de Figma `4:248` móvil y `6:311` escritorio; los 09C son auxiliares. Explorar usa `8:559` y `8:648`, con fidelidad completa pendiente. La identidad Laguna se aplica por pantalla sin cambiar Spring Boot, Thymeleaf, Leaflet, sesión, privacidad, PWA ni reglas de moderación. Consultar `ESTANDARES_INTERFAZ.md` y el contrato `RA08.md` antes de alterar el panel privado.

## Implementado y comprobado

Inicio, Explorar, Publicar y Mis reportes se presentan como secciones exclusivas por hash. El selector de acceso distingue Ciudadano, Auditor y Verificador autorizado, pero solo la consulta del permiso persistente concede acceso efectivo. El registro público sigue creando ciudadanos. La cabecera pública muestra ondas vectoriales y los botones responden al puntero; movimiento reducido desactiva animación. Los marcadores del mapa y la leyenda usan cuatro pictogramas Laguna. El service worker usa caché v31.

Este bloque traslada `/operator` a Laguna sin cambiar endpoints ni IDs: cabecera turquesa, ondas, fuentes locales, presentación de tarea, accesos a secciones, revisión manual, cola, contexto y paneles plegables. La cabecera privada iguala las medidas de Inicio, incorpora En línea y Cuenta y ofrece Cerrar sesión dentro del menú de cuenta. El enlace Auditorías público se revela solo tras consultar el permiso de operador vigente para la sesión; se oculta al salir, perder red o revocar. La ruta privada mantiene su validación por petición. `moderation-laguna.css` se carga después de `app.css`, `laguna.css` y `moderation.css`; no se precarga como recurso privado en el service worker. El operador carece de frame propio de Figma, por lo que esta adaptación necesita revisión visual del compañero.

La demo aislada funciona en `http://127.0.0.1:61385/` con H2 fuera de Git. Una cuenta ficticia tiene permisos de operador y verificador; no registrar su contraseña. IntelliJ en `8080` usa otro checkout/base. En navegador se comprobó `/operator` a 390, 768 y 1366 px sin desbordamiento horizontal.

Para el nuevo acceso se comprobó la navegación de Inicio a Auditorías en 390, 1200 y 1366 px: enlace visible con permiso vigente, sin desbordamiento a 390/1200. En el ajuste de cabecera se midieron Inicio y Auditorías a 1280 px: logo, altura y controles coinciden; se corrigieron también altura y márgenes móviles. La regresión Java cubre permiso ausente, concesión tras nuevo login y revocación en sesión antigua. La nueva ruta responde sin caché; el script público se precarga en PWA v32, pero ninguna respuesta privada entra en la caché. `npm test` aprobó 162 pruebas y `format:check` pasó. CI del siguiente HEAD aún debe comprobarse.

## Siguiente paso

Actualizar el PR #39 con esta cabecera y comprobar CI del SHA final. Revisar con Carlos la dirección visual y después integrar en orden. La fidelidad de Explorar y otros módulos permanece pendiente. No atribuir validación humana ni cerrar la transición global por este bloque.
