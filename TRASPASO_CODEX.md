# RedAuxilio — seguimiento RA-10 y RA-16

Actualización: 6 de noviembre de 2026 (Colombia). Clon principal `C:\Users\Santiago\redauxilio`: rama `feat/RA-08-acciones-moderacion`, HEAD `d6a2348659eb3c3133ae58eb3ee2ac1dfe72387b`; preservar cambios locales `TRASPASO_CODEX.md`, `src/main/resources/static/app.css`, `output/` y `tmp/`. El trabajo RA-16 está aislado en `C:\Users\Santiago\.codex\worktrees\ra16-account-blocking\redauxilio`, rama `codex/ra16-account-blocking`, ahora sincronizada con develop `a43f3358fad971170e4906aa9cd626962b9acce1` (integración de RA-10).

## RA-10 — avistamientos

PR [#30](https://github.com/Carlos1120/redauxilio/pull/30) recibió correcciones y está abierto; consultar SHA/CI actuales directamente en GitHub antes de actuar. Validación final reportada por Carlos: 186 pruebas Java, 133 JavaScript; pruebas PostgreSQL 18.3 desechable; aprobación humana y QA visual siguen pendientes. No aprobar ni integrar en nombre de Santiago.

## RA-16 — bloqueo y reactivación

Tarjeta [RA-16](https://trello.com/c/148zjrzQ) en En progreso; estimación/fecha pendientes. Commit funcional `1a1975e3a901cfb3bbbbad85f552ecb83b2e8ec9`, PR Draft [#31](https://github.com/Carlos1120/redauxilio/pull/31). Merge normal de develop `a43f335` en `b3d668e274ea5a7ffe6403549f4d7510c74ebafa`; compatibilidad local verificada. El merge y el commit docs `8577558` están publicados. CI `verify` SUCCESS en `85775580493cbc8c8650837362566c9a69ce47fb` (run `42241749597`); si se publica otra actualización de documentos, comprobar CI del nuevo HEAD.

La restricción persistente se separa del bloqueo temporal RA-01. Bloquear rechaza mutaciones API incluso con sesión vigente y mantiene lectura pública GET. No elimina permisos de operador/verificador; reactivar restaura su uso. Acciones requieren motivo y generan auditoría transaccional inmutable; historial privado paginado. El panel busca por correo exacto, pagina auditoría en grupos de 50 y borra datos al salir, ocultar página o desconectarse; las respuestas privadas usan no-store.

Combinación actual verificada con Corretto 21: `mvnw.cmd -B verify`, 191 Java sin fallos; Spotless/PMD aprobados. `npm test`, 137/137; formato y `git diff --check` aprobados. Solo H2; PostgreSQL no ejecutado. La QA visual manual en 360×800, 768×1024 y 1366×768 sigue pendiente; el helper gráfico falló al iniciar. No presentar esto como aprobación humana.

Siguiente: confirmar que la rama RA-16 remota coincide con el merge actualizado, ejecutar verificaciones pertinentes, consultar CI exacto y actualizar PR/traspaso. Solicitar revisión formal a Carlos solo tras completar QA visual; no integrar sin revisión APPROVED real. Preservar los demás worktrees y cambios locales.
