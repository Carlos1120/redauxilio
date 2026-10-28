# RedAuxilio — traspaso vigente

Actualización: 28 de octubre de 2026, Colombia. Comprobar estado GitHub/Trello antes de usar este resumen. Normas: `AGENTS.md`, `ESTANDARES.md`; evidencias detalladas: `EVIDENCIAS.md`. El checkout actual es el worktree de Santiago para la corrección táctil; no modificar otra rama desde él.

## Estado comprobado

`origin/develop` quedó en `488157d` después de integrar el PR #14, núcleo RA-05. Al consultar GitHub, no aparecieron revisiones sobre ese PR: la integración no acredita aprobación cruzada ni DoD. RA-05 aún debe conectar y validar la invalidación real con RA-04, además de aceptación manual. RA-03 ya está integrado por PR #11.

PR #12: `fix/RA-02-controles-tactiles`, autor Santiago. Tras el merge anterior `89e5dd6`, la CI 40785333366 aprobó 46 Java y 26 JS, formato y análisis; se observó la vista de escritorio de ese HEAD. El nuevo avance RA-05 de `develop` causó un conflicto solo en `TRASPASO_CODEX.md`, resuelto conservando los datos históricos en Git/EVIDENCIAS. El CSS de 44 px para marca y atribución, con separación de leyenda/controles, permanece intacto. Quedan pruebas del nuevo HEAD, revisión visual móvil y aprobación real de Carlos antes de squash e integración. No aprobar el PR propio.

PR #13: RA-04, autor Carlos. Codex corrigió en la rama la incompatibilidad con RA-05 y publicó `15f7313`: listado propio en una consulta, proyección de confirmaciones y llamada de invalidación tras edición real/transición dentro de la misma transacción. Sobre el árbol combinado pasaron 68 Java, 54 JS, Spotless y PMD; CI del HEAD y validación humana aún deben comprobarse. La revisión previa `CHANGES_REQUESTED` no equivale a aprobación. No modificar esa rama desde este worktree.

## Próximo paso

Verificar formato, pruebas y CI del PR #12 tras publicar el merge; solicitar revisión cruzada a Carlos. Revisar la CI y el nuevo HEAD del PR #13 y obtener aprobación humana de Santiago. No marcar historias Terminado solo por CI ni declarar realizadas instalaciones PWA, pruebas de móvil o revisiones humanas que no consten. Mantener los dos PR hacia `develop`, integrar por squash únicamente tras aprobación y verificar la versión integrada.
