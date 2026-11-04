# RedAuxilio — traspaso RA-10

Actualización: 4 de noviembre de 2026 (Colombia). Clon principal `C:\Users\Santiago\redauxilio`, rama `feat/RA-08-acciones-moderacion`, HEAD local `d6a2348659eb3c3133ae58eb3ee2ac1dfe72387b`; conservar modificaciones locales en `TRASPASO_CODEX.md`, `src/main/resources/static/app.css` y carpetas no rastreadas `output/`, `tmp/` (no inspeccionadas ni tocadas). Este incremento está aislado en `C:\Users\Santiago\.codex\worktrees\ra10-avistamientos`, rama `codex/ra10-avistamientos`, base `develop` `0b581020afe2de0c7cc02494163460ff7d3847d3`.

## Progreso RA-10 — Avistamientos

Requisitos de referencia: observación ligada a una persona desaparecida con fecha, lugar, descripción, foto opcional e historial. No debe marcar el caso como encontrado. Contrato de implementación y políticas candidatas en `RA10.md`; evidencia acumulada en `EVIDENCIAS.md`.

Añadidos API/servicio, tablas SQL, proyección pública sin identidad del autor, registro con sesión/CSRF, tres fotografías máximas validadas por el componente de RA-12, ubicación aproximada a tres decimales y filtrado de texto con regla RA-09. No cambia estado, cierre o fecha de actualización del caso. Historial y fotos usan no-store; el service worker excluye ambas rutas y precarga el nuevo script. El caché PWA avanza a v25 después de RA-13.

Decisión no explícita en el requisito original, pendiente de revisión de Carlos: impedir nuevos avistamientos si el caso ya está encontrado o cerrado. No asumirla como política aceptada hasta revisión.

Pruebas actuales: `MissingPersonSightingTests` 4/4 en H2 y 4/4 en PostgreSQL 18.3 sobre un clúster/base temporales de esta sesión; comprobó multipart HTTP, lectura/foto, seguridad y rollback. La clase usa H2 por defecto o RA10_TEST_DB_URL/USERNAME/PASSWORD y fuerza schema init; nunca dirigirla a una base con datos. `mvnw.cmd -B verify` después de ese cambio: 181 Java, Spotless y PMD aprobados. `npm ci`; `npm test` 129/129; Prettier y `git diff --check` aprobados. Navegador visual sigue pendiente.

La rama remota/PR #30 sigue en `212494c9001d2e3140e49fd33ea614d338f6cdd9`; CI `verify` SUCCESS para ese SHA y GitHub reporta MERGEABLE. Carlos está solicitado formalmente; sin revisión aún. Hay cambios locales en el test configurable para PostgreSQL y estos documentos; requieren un commit/push normal, tras el cual CI debe comprobarse sobre el nuevo HEAD. La regla propuesta para casos encontrados/cerrados aún necesita revisión de Carlos. No integrar ni mover Trello.

## Estado integrado

PR #26 (RA-12) integrado por squash en develop `d9cbc050`. PR #27 (RA-13) fue aprobado formalmente por Carlos para `847dfe7` y GitHub lo muestra integrado por squash; su merge resultó `0b58102`. CI SUCCESS y verificación de 177 Java, 125 JS y 14/14 PostgreSQL se documentaron para ese HEAD previo a integración. El estado de esta nueva rama se ha actualizado al último develop; sus resultados propios de RA-10 se reportan separadamente y usan H2.

Sin aprobación ni aceptación humana de RA-10. No marcarla terminada ni atribuir pruebas humanas. Mantener intactos worktrees, servidores y cambios locales ajenos. Revisión formal de Carlos requerida para PR nuevo; integración por squash después de revisión y CI final.
