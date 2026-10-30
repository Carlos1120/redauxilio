# RedAuxilio — traspaso vigente RA-08

Actualización: 29 de octubre de 2026 (Colombia). Repositorio `C:\Users\Santiago\redauxilio`, rama `feat/RA-08-acciones-moderacion`, basada en `origin/develop` `73034e57c92df05f69ca9d4897153c3c67513ac3`. PR #20 está abierto en borrador hacia `develop`; su HEAD publicado anterior es `3c8193f8892eeb94dfffcd3761b79b0ce7f2db1c`. El incremento de paginación del historial está implementado y verificado localmente, aún sin commit ni push en esta actualización. PR #19 de alineación del formulario ya está listo para revisión de Carlos; `verify` pasó en su HEAD `e269e87e310ec91ea753ad020d4d4e556c2dc969`. Ninguno está integrado. No se cambió Trello.

## Alcance actual

RA-08 tiene en PR #20 las acciones administrativas motivadas de mantener, corregir, ocultar, restituir, cerrar y reabrir. Además, el contexto inicial limitado a 50 registros ahora permite cargar denuncias, cambios y decisiones anteriores con `GET /api/operator/publications/{id}/history/{stream}`. El cursor usa el ID de la última entrada mostrada y orden descendente, sin repetir filas cuando entran eventos nuevos. El backend limita cada página a 50, valida operador persistente y contexto del caso, responde `no-store` y rechaza streams inválidos. La interfaz agrega los datos como texto y descarta respuestas tardías después de cambiar caso o sesión. El contrato actualizado está en `RA08.md` y el detalle de verificación en `EVIDENCIAS.md`.

El trabajo continúa dentro de la rama y el PR RA-08 existentes para mantener un cambio revisable; PR #20 sigue en borrador mientras falta inspección visual manual. El árbol local partió limpio y los archivos modificados pertenecen únicamente al incremento descrito y sus pruebas/documentación. La identidad configurada en este checkout es Santiago Ortiz (`151885267+Arch0102@users.noreply.github.com`); no se suplanta la cuenta de Carlos.

## Verificaciones y límites

En Corretto 21: `mvnw verify`, 96/96 pruebas Java, Spotless y PMD aprobados. `npm ci` no reportó vulnerabilidades; `npm test` pasó 87/87, junto con Prettier y `git diff --check`. JaCoCo para `ModerationQueueService`: 123/124 líneas y 27/32 ramas cubiertas. Una prueba inicial de Spotless falló porque PowerShell tomó Java 8; se repitió correctamente con Corretto 21. Las integraciones usan H2 y no certifican PostgreSQL.

Pendientes antes de solicitar revisión formal del nuevo HEAD: publicar el commit en PR #20, comprobar CI sobre ese SHA e inspeccionar la vista a 360×800, 768×1024 y 1366×768. Carlos aún debe revisar y aprobar; no se declara RA-08 terminada ni se cambia la tarjeta a Terminado. Después de aprobar e integrar, verificar `develop` y conservar la regla de squash. Para continuar: revisar el diff, confirmar identidad, cerrar formato/documentación y publicar el incremento sin modificar la rama de la interfaz.
