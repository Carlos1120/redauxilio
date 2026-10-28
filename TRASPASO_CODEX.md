# RedAuxilio — traspaso vigente

Actualización: 28 de octubre de 2026, Colombia. Comprobar siempre GitHub, Trello y el árbol local antes de usar este resumen. Las normas están en `AGENTS.md` y `ESTANDARES.md`; resultados detallados en `EVIDENCIAS.md`.

## Estado verificado

`origin/develop` avanzó a `488157d` mediante el squash del PR #14, núcleo RA-05. Al consultar GitHub, el PR #14 figuraba integrado sin ninguna revisión registrada; esa integración no acredita la aprobación cruzada, la validación humana ni la DoD. La tarjeta RA-05 requiere comprobación actual antes de cambiar estado. RA-03 ya se integró previamente por PR #11. No usar las secciones históricas de los documentos como estado vigente.

PR #13, RA-04, de Carlos: rama `feat/RA-04-actualizacion-cierre`. La corrección de rendimiento `393602d` sustituyó las lecturas globales M×N por una consulta SQL propia; Codex verificó el cambio y la CI de ese HEAD pasó. La revisión `CHANGES_REQUESTED` anterior permanece en GitHub y no hay aprobación humana de Santiago. Al integrar `develop` con RA-05 aparecieron conflictos en documentación, seguridad, repositorio, esquema, PWA, plantilla y prueba. En el checkout principal se resolvieron conservando ambos módulos. La proyección pública comparte conteo y versión de confirmaciones, y la edición real o transición de RA-04 invalida respaldo previo dentro de la misma transacción. Regresión nueva: tres confirmadores, edición, versión antigua rechazada y cierre. Pasaron 22 pruebas Java específicas, 54 JS y `mvnw verify` con 68 Java, Spotless y PMD sobre este árbol. Falta publicar el merge, comprobar CI del HEAD y validación humana final.

PR #12, RA-02 táctiles, de Santiago: rama `fix/RA-02-controles-tactiles` en worktree `C:\Users\Santiago\.codex\worktrees\ra02-touch-targets\redauxilio`. El HEAD publicado `89e5dd6` incorporó RA-03/develop, pasó CI 40785333366 y quedó abierto para revisión. El avance posterior de `develop` con RA-05 volvió a causar conflictos documentales; resolverlos en ese worktree, verificar CI y solicitar revisión real de Carlos. No aprobar el PR propio.

## Siguiente acción y límites

Completar documentación/formato y publicar la resolución de PR #13 sin force push. Resolver después el conflicto del PR #12. Ninguno se integra hasta revisión cruzada real sobre el HEAD final, CI verde y comprobaciones manuales pertinentes. Registrar por separado los resultados del agente y las confirmaciones humanas; no mover RA-04 o RA-05 a Terminado por una CI. Conservar ramas y datos locales; no modificar el worktree de Carlos ni fabricar evidencias.
