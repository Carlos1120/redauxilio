# RedAuxilio — integración y revisión RA-16

Actualización: 11 de noviembre de 2026 (Colombia). Trabajo aislado en `C:/Users/cmps-/RedAuxilio-recovery/ra16-revision`, rama local `codex/ra16-revision-integracion`; destino autorizado: rama existente `codex/ra16-account-blocking`, PR #31. Se integra mediante merge normal el HEAD revisado `1db301441fe7bc26575b3552a8fd12034abd11d1` con `develop` `a014b44e8a64030ac6042a47f1cf5fe5ef4a8d8f`. No modificar otros worktrees, servidores ni respaldos. Sin force push, merge en GitHub, aceptación humana o cambios en Trello.

## Integración conservada

El único conflicto textual fue este traspaso. Las evidencias históricas de ambas ramas permanecen en `EVIDENCIAS.md`: RA-16 del 4 de noviembre y RA-14 del 11 de noviembre. Sus resultados anteriores no constituyen pruebas del árbol combinado. Los registros RA-10 y las pruebas PostgreSQL anteriores siguen siendo históricos; consultar GitHub antes de actuar sobre otro PR.

RA-14 conserva preconsulta privada, justificación obligatoria ante coincidencias, bloqueo por categoría, control definitivo transaccional y limpieza de advertencias después del alta. `RA14.md` y `RA14_REGRESIONES.md` mantienen contrato y regresiones. RA-15 conserva la última consulta pública válida cuando falla otra zona sin red, con aviso de procedencia anterior. No se añaden recursos privados al service worker ni se cambia su política de caché.

## Corrección RA-16

Después de esperar el token CSRF, el panel comprueba nuevamente la generación de pantalla y la conexión antes de iniciar el POST. Ocultar la pantalla, cambiar identidad o desconectarse invalida la operación pendiente. Esto no cancela solicitudes ya enviadas ni revierte decisiones confirmadas. La restricción persistente continúa separada del bloqueo temporal de autenticación y no elimina permisos previamente concedidos.

Las pruebas dirigidas cubren invalidación asíncrona, rutas RA-14, alta JSON/multipart, confirmaciones, denuncias, avistamientos, verificación y permiso operativo persistente. Incluyen sesión abierta, motivos, auditoría, rollback, paginación y bloqueo SQL entre conexiones independientes. PostgreSQL se limita a una base local exclusiva con nombre validado; la limpieza elimina únicamente cuentas ficticias propias. Logs de esta ejecución fuera de Git en `ra16-qa`; ninguna credencial se incorpora al repositorio.

Validación local completada: Maven verify 204 Java, Node 147/147, Spotless, PMD, Prettier y diff aprobados; ampliación HTTP de historial verificada aparte (13 pruebas dirigidas). PostgreSQL 18.3: 13 pruebas más preconsulta RA-14 y paginación HTTP dirigidas, sin fallos. Cobertura crítica superior al 95 %. Siguiente: publicar normalmente solo si el remoto sigue en el HEAD esperado y comprobar CI exacto. Mantener PR Draft y revisión visual/aceptación humana pendientes. No presentar comprobaciones automatizadas como revisión de Carlos ni Santiago.
