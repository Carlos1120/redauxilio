# RedAuxilio — traspaso Laguna

Actualizado: 15 de noviembre de 2026 (Colombia). Rama activa `codex/laguna-operator`, creada desde `codex/laguna-access-motion` (HEAD publicado en PR #38). El PR #38 está abierto hacia `feat/laguna-shell-home-explore`; el PR #37 ya se integró en esa rama y #36 en `develop`. Verificar de nuevo esos estados antes de integrar. No hacer merge ni atribuir revisión humana inexistente.

## Dirección y alcance

Inicio sigue los frames oficiales de Figma `4:248` móvil y `6:311` escritorio; los 09C son auxiliares. Explorar usa `8:559` y `8:648`, con fidelidad completa pendiente. La identidad Laguna se aplica por pantalla sin cambiar Spring Boot, Thymeleaf, Leaflet, sesión, privacidad, PWA ni reglas de moderación. Consultar `ESTANDARES_INTERFAZ.md` y el contrato `RA08.md` antes de alterar el panel privado.

## Implementado y comprobado

Inicio, Explorar, Publicar y Mis reportes se presentan como secciones exclusivas por hash. El selector de acceso distingue Ciudadano, Auditor y Verificador autorizado, pero solo la consulta del permiso persistente concede acceso efectivo. El registro público sigue creando ciudadanos. La cabecera pública muestra ondas vectoriales y los botones responden al puntero; movimiento reducido desactiva animación. Los marcadores del mapa y la leyenda usan cuatro pictogramas Laguna. El service worker usa caché v31.

Este bloque traslada `/operator` a Laguna sin cambiar endpoints ni IDs: cabecera turquesa, ondas, fuentes locales, presentación de tarea, accesos a secciones, revisión manual, cola, contexto y paneles plegables. `moderation-laguna.css` se carga después de `app.css`, `laguna.css` y `moderation.css`; no se precarga como recurso privado en el service worker. El operador carece de frame propio de Figma, por lo que esta adaptación necesita revisión visual del compañero.

La demo local aislada funciona en `http://127.0.0.1:61385/` con una base H2 de archivo en una carpeta temporal fuera de Git. Una cuenta ficticia tiene permisos de operador y verificador, concedidos y comprobados mediante las rutas protegidas; no registrar su contraseña en el repositorio. La app de IntelliJ en `8080` usa otro checkout/base. En navegador local se comprobó `/operator` a 390, 768 y 1366 px sin desbordamiento horizontal y se abrió la sección de autorizaciones. Pruebas dirigidas de moderación: 26/26; JavaScript completo: 162/162; Prettier pasó. Maven `verify` con Java 21 aprobó 205 pruebas Java, sin fallos.

## Siguiente paso

Publicar PR apilado hacia `codex/laguna-access-motion` y comprobar CI del SHA final. Revisar con Carlos la dirección visual y después integrar en orden. La fidelidad de Explorar y otros módulos permanece pendiente. No atribuir validación humana ni cerrar la transición global por este bloque.
