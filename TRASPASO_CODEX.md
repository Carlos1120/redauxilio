# RedAuxilio: estado para continuar

Instantánea del 6 de octubre de 2026 (Colombia). Normas en AGENTS.md y ESTANDARES.md; contrato en RA02.md; evidencia automática y humana separada en EVIDENCIAS.md.

## Estado actual

- Repositorio de Santiago: `C:\Users\Santiago\redauxilio`; remoto https://github.com/Carlos1120/redauxilio.git.
- PR #8 de RA-02 fue aprobado por Carlos Mario Peña y se integró por squash en `develop` como `bb97f9bdced4e031bd00d8916c0f5caa80dfd1fc`. CI post-merge pasó en ejecución 37538504822. La tarjeta de seguimiento está en Terminado; se conserva la RA-02 base.
- PR #5, #6 y #7 integrados en develop. No fusionar otra vez las ramas antiguas ni reutilizar un traspaso previo como estado actual. La integración anterior no aprueba este rediseño.
- El rediseño solicitado por Santiago usa Onest, rojo `#d7262e`, mapa como fondo y paneles flotantes de lista/detalle. Conserva API, filtros avanzados y borrador local; añade búsqueda local y filtros rápidos. Leaflet 1.9.4 y Onest se sirven localmente con sus licencias; caché PWA v6. No hay geolocalización, rutas, publicación real ni datos oficiales.
- Santiago observó el diseño en IntelliJ y autorizó commit y push; la revisión final corresponde a Carlos. No atribuir aprobación del PR ni pruebas del agente a Santiago.

## Evidencias y límites

- Rediseño: comprobado mediante Codex en servidor propio 8081, Windows/Corretto 21.0.10 y navegador integrado. Tres tamaños 360×800, 768×1024 y 1366×768 sin desbordamiento horizontal; selectores completos en tableta y detalle modal móvil. Búsqueda/filtros actualizan lista/mapa/detalle. Borrador guardado, recuperado y descartado. Caída del servidor muestra caché fechada; reinicio recupera datos del servidor. Esto no simula desconexión total de red.
- Antes de incorporar PR #7: cinco pruebas Node y 22 Java, Prettier, Spotless, PMD y git diff --check aprobados. OpenJDK 25 falla en Spotless; verify pasa con el JDK 21 instalado. El registro posterior a la actualización debe consultarse en el PR y EVIDENCIAS.md.
- PR #7 aporta 23 ejecuciones Java y nueve JS, corrección del aviso cartográfico por tesela y pruebas de carga aisladas. RNF-01: 1.000 datos/20 usuarios, p95 interno 19,802/4,299/0,252 ms (consulta/filtros/detalle), primeros resultados 366,9 ms en el entorno registrado. El rediseño no modifica el servidor; conservar esa evidencia sin afirmar medición nueva.
- Carlos comunicó validación humana de responsive, instalación PWA en Chrome y desconexión/reconexión real sobre `e95b7da`, antes del rediseño. RNF-10 anterior: Lighthouse 100/100 móvil/escritorio. Esos resultados no acreditan automáticamente la nueva interfaz ni la nueva caché; confirmar los casos afectados. No atribuirlos a Santiago.
- Leaflet retiene los fallos de cada tesela hasta tileload/tileunload; el rediseño conserva esta corrección y usa el mismo estado para mostrar el aviso. Cartografía requiere Internet y no se descarga masivamente ni se guarda por service worker. Consultas offline dependen de la URL exacta previamente guardada.

## Trabajo actual: RA-01

- Rama `feat/RA-01-acceso-ciudadano` parte del `develop` actualizado después de integrar PR #8 (`bb97f9b`). El PR #9 está abierto hacia `develop` y Carlos1120 fue solicitado como revisor. Esta actualización añade pruebas de seguridad RA-01 y corrige expiración de sesión; ver resultados y límites en el apartado final de `EVIDENCIAS.md`.
- La implementación inicial cubre formulario de registro, acceso y salida, persistencia local H2 con perfil PostgreSQL, BCrypt, bloqueo temporal y expiración absoluta de sesión. Alcance y supuestos: RA01.md.
- Respuesta al `REQUEST_CHANGES` de Carlos sobre `422ab2a`: expiración de sesión en rutas públicas corregida; copia del shell sin token CSRF y renovación antes del POST; HTTPS obligatorio en `prod`; correlation-id en cabecera, MDC y logs de errores. Detalle en el apartado del 7 de octubre de `EVIDENCIAS.md`.
- Verificación de la corrección: `mvnw.cmd -B verify` BUILD SUCCESS (37 Java: 12 identidad, 2 HTTPS, 22 publicaciones, 1 rendimiento; Spotless y PMD); `npm.cmd test` 16/16; Prettier y `git diff --check` aprobados. H2 de tests en memoria; base de IntelliJ intacta.
- Las pruebas simulan offline → reconexión con el service worker y script real; no son instalación PWA manual ni prueba en Chrome/Edge de escritorio. HTTPS se comprobó con MockMvc, incluido `X-Forwarded-Proto`, no contra un proxy productivo real.
- La corrección está publicada en PR #9 como `09d6811`; GitHub Actions `verify` pasó en la ejecución 37679497399. Carlos1120 fue solicitado de nuevo como revisor. Pendientes: instalación manual PWA y validación final después de integrar en `develop`. Santiago/Carlos dejaron Trello En progreso; no tocar la tarjeta ni declarar RA-01 terminada.

Seguir la guía visual existente para nuevos formularios. Santiago autorizó el commit y push del avance actual; esto no autoriza PR, merge ni integración. No subir cuentas ni contraseñas reales. No marcar RA-01 lista hasta ejecutar los casos específicos, completar revisión de Carlos y las comprobaciones sobre develop.

Servidor de IntelliJ 8080 preservado; la instancia propia 8081 usa H2 en memoria. No crear cuentas con datos reales. Tarjeta RA-01: https://trello.com/c/25iI3xgM. Tarjeta RA-02: https://trello.com/c/LYDxDTZT.

Lectura mínima: este estado y AGENTS.md; secciones finales de RA02.md/EVIDENCIAS.md; ESTANDARES.md al aplicar reglas.
