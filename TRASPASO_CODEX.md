# RedAuxilio — seguimiento RA-10 y RA-16

Actualización: 6 de noviembre de 2026 (Colombia). Clon principal `C:\Users\Santiago\redauxilio`: rama `feat/RA-08-acciones-moderacion`, HEAD `d6a2348659eb3c3133ae58eb3ee2ac1dfe72387b`; preservar sus cambios locales en `TRASPASO_CODEX.md`, `src/main/resources/static/app.css`, `output/` y `tmp/`. RA-16 está aislada en `C:\Users\Santiago\.codex\worktrees\ra16-account-blocking\redauxilio`, rama `codex/ra16-account-blocking`, basada en develop `0b581020afe2de0c7cc02494163460ff7d3847d3`.

## RA-10 — avistamientos

PR [#30](https://github.com/Carlos1120/redauxilio/pull/30), HEAD `13251e9b75c694d12c59f42599ec2c8c636e1d6a`, CI `verify` SUCCESS según comprobación previa. Carlos sigue solicitado como revisor; no hay revisión formal ni integración comprobada en esta actualización. Una regla provisional sobre aportes a casos encontrados/cerrados sigue pendiente de decisión. No fusionar ni marcar terminado sin revisión.

## RA-16 — bloqueo y reactivación

Tarjeta [RA-16](https://trello.com/c/148zjrzQ) en En progreso; estimación y fecha pendientes. Implementación en commit `1a1975e3a901cfb3bbbbad85f552ecb83b2e8ec9`; PR Draft [#31](https://github.com/Carlos1120/redauxilio/pull/31) hacia `develop`. CI `verify` estaba queued para ese SHA al crear la PR; falta resultado final. La PR no está lista para aprobación formal hasta completar QA visual manual y CI verde del HEAD vigente.

La restricción persistente se separa del bloqueo temporal RA-01. Bloquear rechaza mutaciones de API incluso con sesión existente, conserva lectura pública GET y no borra permisos previos de operador/verificador; reactivar restaura su uso. Operaciones de operador requieren motivo, guardan auditoría transaccional inmutable y ofrecen historial paginado privado. El panel limpia datos al salir, ocultar la página o perder conexión.

Verificación local final en Corretto 21: `mvnw.cmd -B verify`, 181 Java sin fallos/errores/omitidas; Spotless y PMD aprobados. `npm test`, 129/129; `npm run format:check` y `git diff --check` aprobados. Pruebas en H2 aislada; PostgreSQL real no se ejecutó. QA visual manual pendiente: el helper de escritorio falló al inicializar por ruta no encontrada; no afirmar revisión de 360×800, 768×1024 ni 1366×768.

Siguiente: consultar CI de PR #31; realizar QA visual antes de pedir a Carlos una revisión formal. Registrar cualquier arreglo en la misma rama y repetir suites solo si cambia el código. Actualizar SHA y CI en este traspaso al terminar. No integrar sin aprobación formal ni tocar otros worktrees/cambios locales.
