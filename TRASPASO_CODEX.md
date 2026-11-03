# RedAuxilio — traspaso RA-12 y RA-13

Actualización: 3 de noviembre de 2026 (Colombia). Repositorio principal `C:\Users\Santiago\redauxilio`, rama `feat/RA-08-acciones-moderacion`, HEAD `d6a2348659eb3c3133ae58eb3ee2ac1dfe72387b`; sus cambios locales en `TRASPASO_CODEX.md` y `src/main/resources/static/app.css` se conservaron. Trabajo RA-12 en `C:\Users\Santiago\.codex\worktrees\ra12-sync-29`, rama auxiliar `codex/ra12-sync-develop-366a550`, que publica en `feat/RA-12-evidencias-fotograficas`. RA-13 revisada en `C:\Users\Santiago\.codex\worktrees\ra13-sync-29`.

## Estado remoto verificado

`develop` está en `366a55045150e9252c081fd3d7627dccf2310353`. PR #29 está cerrado e integrado por squash con ese SHA; la aprobación formal de Santiago y CI SUCCESS del HEAD `62e575c9a12cdfd3025deadf93cf1123654754db` están comprobados. PR #26 sigue abierto, mergeable y sin aprobación formal de Carlos; su HEAD remoto previo a este bloque era `b23fb86bc6891a4c44a47f26049dbf8a65907c4c`. PR #27 sigue abierto, mergeable y sin revisión formal registrada; HEAD `a69fb8da38ad564f6a2375ea79706d0b6041da39`. No se integró ninguno.

## RA-12 — trabajo y comprobaciones

Se reprodujo el WebP truncado aceptado por mera inspección RIFF. La validación ahora decodifica y re-encodea por completo el WebP con límite de 20 MP y 5 MiB, y exige firma, MIME y extensión concordantes. Se añadieron regresiones para WebP válido/truncado, MIME y extensión falsos, CSRF, permiso de autor, ocultar/restaurar, caso cerrado, carga múltiple atómica y acceso público. Las rutas de imágenes siguen fuera del service worker. Al cerrar sesión o iniciar otra cuenta se eliminan fotos locales y vistas previas, conservando los otros campos offline.

En el HEAD local aún no publicado: Java 21 `mvn verify` pasó 172 pruebas, Spotless y PMD; `npm test` 123/123; `npm run format:check` y `git diff --check` pasaron. CI para el nuevo SHA debe comprobarse tras publicar. El uso de H2 no demuestra PostgreSQL: PostgreSQL local escucha, pero no hay URL/configuración que confirme una base aislada autorizada; no se escribió en ella.

## RA-13 y siguiente paso

Auditoría dirigida de servicio, controlador y pruebas: permisos de operador/verificador separados, CSRF y rutas privadas, revocación e historial, invalidación por versión, privacidad pública y separación de confianza/vigencia. `OfficialVerificationTests`: 3/3 pasan en H2. CI SUCCESS conocido corresponde al HEAD remoto anterior, no a nuevos cambios (no se modificó RA-13). Revisiones cruzadas formales de Carlos siguen pendientes.

Siguiente: comprobar el nuevo SHA y CI de #26, facilitar a Carlos la revisión formal y ejecutar la prueba multipart contra PostgreSQL aislado cuando exista una conexión de pruebas identificada. Después, volver a comprobar #27. Sin merge, cambio de Trello ni afirmación de aceptación humana; QA visual/PWA queda pendiente.
