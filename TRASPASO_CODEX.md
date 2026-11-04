# RedAuxilio — traspaso RA-10

Actualización: 3 de noviembre de 2026 (Colombia). Clon principal `C:\Users\Santiago\redauxilio`, rama `feat/RA-08-acciones-moderacion`, HEAD local `d6a2348659eb3c3133ae58eb3ee2ac1dfe72387b`; conservar modificaciones locales allí en `TRASPASO_CODEX.md` y `src/main/resources/static/app.css`. Este incremento está aislado en `C:\Users\Santiago\.codex\worktrees\ra10-avistamientos`, rama `codex/ra10-avistamientos`, ahora alineada con `origin/develop` `0b581020afe2de0c7cc02494163460ff7d3847d3` antes del commit RA-10.

## Progreso RA-10 — Avistamientos

Requisitos de referencia: observación ligada a una persona desaparecida con fecha, lugar, descripción, foto opcional e historial. No debe marcar el caso como encontrado. Contrato de implementación y políticas candidatas en `RA10.md`; evidencia acumulada en `EVIDENCIAS.md`.

Añadidos API/servicio, tablas SQL, proyección pública sin identidad del autor, registro con sesión/CSRF, tres fotografías máximas validadas por el componente de RA-12, ubicación aproximada a tres decimales y filtrado de texto con regla RA-09. No cambia estado, cierre o fecha de actualización del caso. Historial y fotos usan no-store; el service worker excluye ambas rutas y precarga el nuevo script. El caché PWA avanza a v25 después de RA-13.

Decisión no explícita en el requisito original, pendiente de revisión de Carlos: impedir nuevos avistamientos si el caso ya está encontrado o cerrado. No asumirla como política aceptada hasta revisión.

Pruebas actuales: `MissingPersonSightingTests` 4/4 en H2, incluida transacción completa al fallar la inserción de imagen. Tras sincronizar develop/RA-13: `mvnw.cmd -B verify` pasó 181 Java, Spotless y PMD; el segundo intento terminó `BUILD SUCCESS` tras DNS transitorio de Maven Central. `npm ci`; `npm test` 129/129; Prettier y `git diff --check` aprobados. Sin PostgreSQL ni navegador real.

Commit funcional publicado: `a9aa6d3366bbbe3df99b99e290bd0a1536d6f8d5`. PR #30 abierto hacia develop `0b581020afe2de0c7cc02494163460ff7d3847d3`, GitHub lo reporta MERGEABLE; revisión formal solicitada a Carlos. CI verify en cola sobre `a9aa6d3` (run 41878569912 observado). Hay un seguimiento documental local aún sin publicar; el último push falló por DNS de `github.com`, sin mover la rama remota. La regla provisional sobre casos encontrados/cerrados requiere respuesta/revisión de Carlos. Siguiente: reintentar push normal cuando vuelva la conexión, comprobar CI sobre el HEAD publicado y esperar revisión formal. No integrar ni mover Trello.

## Estado integrado

PR #26 (RA-12) integrado por squash en develop `d9cbc050`. PR #27 (RA-13) fue aprobado formalmente por Carlos para `847dfe7` y GitHub lo muestra integrado por squash; su merge resultó `0b58102`. CI SUCCESS y verificación de 177 Java, 125 JS y 14/14 PostgreSQL se documentaron para ese HEAD previo a integración. El estado de esta nueva rama se ha actualizado al último develop; sus resultados propios de RA-10 se reportan separadamente y usan H2.

Sin aprobación ni aceptación humana de RA-10. No marcarla terminada ni atribuir pruebas humanas. Mantener intactos worktrees, servidores y cambios locales ajenos. Revisión formal de Carlos requerida para PR nuevo; integración por squash después de revisión y CI final.
