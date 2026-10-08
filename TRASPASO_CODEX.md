# RedAuxilio: estado para continuar

Instantánea del 6 de octubre de 2026 (Colombia). Normas en AGENTS.md y ESTANDARES.md; contrato en RA02.md; evidencia automática y humana separada en EVIDENCIAS.md.

## Estado actual

- Repositorio de Santiago: `C:\Users\Santiago\redauxilio`; remoto https://github.com/Carlos1120/redauxilio.git.
- PR #8 de RA-02 fue aprobado por Carlos Mario Peña y se integró por squash en `develop` como `bb97f9bdced4e031bd00d8916c0f5caa80dfd1fc`. CI post-merge pasó en ejecución 37538504822. La tarjeta de seguimiento está en Terminado; se conserva la RA-02 base.
- PR #5, #6 y #7 integrados en develop. No fusionar otra vez las ramas antiguas ni reutilizar un traspaso previo como estado actual. La integración anterior no aprueba este rediseño.
- El rediseño solicitado por Santiago usa Onest, rojo `#d7262e`, mapa como fondo y paneles flotantes de lista/detalle. Conserva API, filtros avanzados y borrador local; añade búsqueda local y filtros rápidos. Leaflet 1.9.4 y Onest se sirven localmente con sus licencias; caché PWA v9. No hay geolocalización, rutas, publicación real ni datos oficiales.
- Santiago observó el diseño en IntelliJ y autorizó commit y push; la revisión final corresponde a Carlos. No atribuir aprobación del PR ni pruebas del agente a Santiago.

## Evidencias y límites

- Rediseño: comprobado mediante Codex en servidor propio 8081, Windows/Corretto 21.0.10 y navegador integrado. Tres tamaños 360×800, 768×1024 y 1366×768 sin desbordamiento horizontal; selectores completos en tableta y detalle modal móvil. Búsqueda/filtros actualizan lista/mapa/detalle. Borrador guardado, recuperado y descartado. Caída del servidor muestra caché fechada; reinicio recupera datos del servidor. Esto no simula desconexión total de red.
- Antes de incorporar PR #7: cinco pruebas Node y 22 Java, Prettier, Spotless, PMD y git diff --check aprobados. OpenJDK 25 falla en Spotless; verify pasa con el JDK 21 instalado. El registro posterior a la actualización debe consultarse en el PR y EVIDENCIAS.md.
- PR #7 aporta 23 ejecuciones Java y nueve JS, corrección del aviso cartográfico por tesela y pruebas de carga aisladas. RNF-01: 1.000 datos/20 usuarios, p95 interno 19,802/4,299/0,252 ms (consulta/filtros/detalle), primeros resultados 366,9 ms en el entorno registrado. El rediseño no modifica el servidor; conservar esa evidencia sin afirmar medición nueva.
- Carlos comunicó validación humana de responsive, instalación PWA en Chrome y desconexión/reconexión real sobre `e95b7da`, antes del rediseño. RNF-10 anterior: Lighthouse 100/100 móvil/escritorio. Esos resultados no acreditan automáticamente la nueva interfaz ni la nueva caché; confirmar los casos afectados. No atribuirlos a Santiago.
- Leaflet retiene los fallos de cada tesela hasta tileload/tileunload; el rediseño conserva esta corrección y usa el mismo estado para mostrar el aviso. Cartografía requiere Internet y no se descarga masivamente ni se guarda por service worker. Consultas offline dependen de la URL exacta previamente guardada.

## Trabajo actual: RA-01

- El PR #9 de RA-01 ya aparece integrado en `develop` en GitHub (8 de octubre de 2026). El checkout está en `feat/RA-01-acceso-ciudadano`, HEAD `42ec1f2`; el estado local tiene cambios sin commit de interfaz/perfil posteriores a la integración y no pertenecen al PR. Esta actualización añade pruebas de seguridad RA-01 y corrige expiración de sesión; ver resultados y límites en el apartado final de `EVIDENCIAS.md`.
- La implementación inicial cubre formulario de registro, acceso y salida, persistencia local H2 con perfil PostgreSQL, BCrypt, bloqueo temporal y expiración absoluta de sesión. Alcance y supuestos: RA01.md.
- Respuesta al `REQUEST_CHANGES` de Carlos sobre `422ab2a`: expiración de sesión en rutas públicas corregida; copia del shell sin token CSRF y renovación antes del POST; HTTPS obligatorio en `prod`; correlation-id en cabecera, MDC y logs de errores. Detalle en el apartado del 7 de octubre de `EVIDENCIAS.md`.
- Verificación de la corrección: `mvnw.cmd -B verify` BUILD SUCCESS (37 Java: 12 identidad, 2 HTTPS, 22 publicaciones, 1 rendimiento; Spotless y PMD); `npm.cmd test` 16/16; Prettier y `git diff --check` aprobados. H2 de tests en memoria; base de IntelliJ intacta.
- Las pruebas simulan offline → reconexión con el service worker y script real; no son instalación PWA manual ni prueba en Chrome/Edge de escritorio. HTTPS se comprobó con MockMvc, incluido `X-Forwarded-Proto`, no contra un proxy productivo real.
- Históricamente, GitHub Actions `verify` pasó en `09d6811` (ejecución 37679497399); luego el PR #9 se integró. Pendientes del bloque: instalación manual PWA y comprobaciones post-merge en `develop`. No se verificó Trello en esta actualización.

Seguir la guía visual existente para nuevos formularios. Santiago autorizó el commit y push del avance actual; esto no autoriza PR, merge ni integración. No subir cuentas ni contraseñas reales. No marcar RA-01 lista hasta ejecutar los casos específicos, completar revisión de Carlos y las comprobaciones sobre develop.

Servidor de IntelliJ 8080 preservado; la instancia propia 8081 usa H2 en memoria. No crear cuentas con datos reales. Tarjeta RA-01: https://trello.com/c/25iI3xgM. Tarjeta RA-02: https://trello.com/c/LYDxDTZT.

Lectura mínima: este estado y AGENTS.md; secciones finales de RA02.md/EVIDENCIAS.md; ESTANDARES.md al aplicar reglas.

## Cambio local pendiente — cabecera de acceso

En `feat/RA-01-acceso-ciudadano`, la cabecera separa el botón neutral de cuenta del punto pequeño de conexión (verde online, rojo offline). El botón abre la cuenta y permite cerrar sesión. Login/logout envían POST con token CSRF fresco y actualizan el estado sin navegación completa; al reconectar se confirma la sesión con `/account`. El service worker v9 elimina del shell guardado los tokens CSRF y el estado de autenticación antiguo. Cambios locales no comprometidos ni publicados, según la instrucción expresa de Santiago.

Verificación local: 39 pruebas Java de `mvnw verify`, 22 de Node, Prettier y `git diff --check` aprobados con Corretto 21. La observación inicial de `localhost:8080` en esta nota ya no es actual: la inspección del 8 de octubre encontró `identity-controls` y `identity-status.js` servidos; no reiniciar ni detener la instancia ajena.

## Refinamiento local del panel de consulta — 8 de octubre de 2026

Santiago solicitó probar la propuesta B de Open Design con ajustes de legibilidad de A. Tras su comentario de que el primer ajuste era demasiado sutil, `app.css` ahora usa panel de 376 px, títulos de 18 px, insignias de categoría y bloques visuales paralelos para estado operativo y confianza; `app.js` muestra la última actualización y `sw.js` renueva la caché v10. Se conserva la dirección Onest/rojo/mapa; no se alteraron API ni datos. La guía de interfaz y evidencias anotan el refinamiento.

Vista probada por Codex en 360×800, 768×1024 y 1366×768 mediante la app local en 8081, con H2 en memoria y recursos/plantilla leídos desde el árbol actual. No apareció desbordamiento horizontal; detalle centrado en móvil/tableta y lateral en escritorio; mapa, lista, fichas, controles y atribución se vieron según el encuadre. El detalle largo cierra con Escape y devuelve el foco. En tableta, selectores avanzados de 329 px caben completos. No se verificó error cartográfico ni recorrido PWA offline manual.

`npm.cmd test`: 22/22; `npm.cmd run format:check` y `git diff --check`: aprobados. Revisión del agente no acredita validación manual de Santiago o Carlos. La instancia propia 8081 sigue activa para inspección; IntelliJ/8080 se conservó. HEAD `42ec1f275ef82ac3d2c69951c913e3f090e0d160`, sin commit; cambios locales previos preservados.

## Corrección de registro y perfil — 8 de octubre de 2026

En el mismo bloque local de RA-01 se aclaró el mensaje para la restricción de correo duplicado sin confirmar si una cuenta existe; se agregó `/api/account`, privado y sin caché, que solo devuelve nombre visible y correo de la sesión. El icono de cuenta carga esos datos en el diálogo, mantiene cerrar sesión y limpia los campos al salir. `sw.js` actualiza la caché a v11 para renovar la interfaz. No se usaron datos de registro de la captura ni se creó una cuenta real.

Pruebas locales: `npm.cmd test` 23/23; `mvnw.cmd -B verify` con Corretto 21.0.10 BUILD SUCCESS (39 pruebas, Spotless y PMD); `npm.cmd run format:check` y `git diff --check` aprobados. GET `localhost:8082/` devuelve 200 y los campos de perfil; H2 en memoria aislada. La vista nueva 8082 queda disponible; no se reiniciaron 8080 ni 8081, cuyas instancias conservaron su estado. No se ha hecho prueba manual del flujo de registro/perfil en navegador ni validación humana. El PR base ya está integrado; los cambios actuales son locales y pendientes de organizar para revisión.

## Estado remoto revisado — 8 de octubre de 2026

GitHub muestra el PR #9 como integrado en `develop` el 8 de octubre: https://github.com/Carlos1120/redauxilio/pull/9. Se verificó por red que `origin/develop` era `12d7939` y que la rama fuente del PR estaba en `42ec1f2`. Los cambios locales se guardaron temporalmente en stash y reaplicaron sin conflictos sobre la rama nueva `feat/RA-01-perfil-ciudadano`, basada en `origin/develop`; el stash se conserva hasta completar el commit/push. `gh` no está instalado; el PR se consultó en GitHub. No se actualizó Trello. La integración no demuestra comprobaciones post-merge en `develop` ni completa la validación manual PWA.
