# RedAuxilio — traspaso vigente

Actualización: 28 de octubre de 2026, Colombia. Comprobar remotos, PR y Trello al retomar; este resumen no sustituye la evidencia actual. Normas: `AGENTS.md` y `ESTANDARES.md`; resultados detallados en `EVIDENCIAS.md`.

## Base integrada y pendientes de proceso

`origin/develop` estaba en `7c98844` al comenzar este bloque. PR #14 integró el núcleo RA-05 como `488157d`, PR #13 añadió RA-04 e invalidación de confirmaciones como `ac18121`, y PR #12 de controles táctiles como `7c98844`. Se verificaron CI posteriores a los tres merges con éxito. GitHub no mostraba revisiones aprobatorias de #12, #13 ni #14; la integración no acredita la revisión cruzada exigida. RA-04 sigue En revisión y RA-05 En progreso en Trello; no moverlas a Terminado sin constatar criterios, validación humana y trazabilidad. Los traspasos previos en develop estaban desactualizados tras merges concurrentes.

## Trabajo actual

Checkout principal `C:\Users\Santiago\redauxilio`, rama `fix/RA-05-cierre-vial-heredado` creada desde `origin/develop` limpio. El problema: la API pública trataba una vía antigua `HABILITADA` con `closed=false` como cerrada, pero el servicio de confirmaciones permitía respaldarla porque consultaba solo la bandera. Codex reprodujo el fallo mediante prueba HTTP/SQL: detalle `closed=true`, confirmación 200 en vez de 409. La lectura bloqueada de confirmación ahora considera categoría y estado, igual que la proyección pública; no cambia datos heredados ni la arquitectura. La prueba específica pasa después de `mvnw clean`. La ejecución incremental previa reutilizó una clase antigua; se confirmó con `javap` y no se presenta como prueba de la corrección. La verificación final pasó con Corretto 21: 69 Java, Spotless, PMD, 54 JavaScript, Prettier y diff --check. Faltan CI y revisión de Carlos sobre el HEAD final.

El worktree de PR #12 en `C:\Users\Santiago\.codex\worktrees\ra02-touch-targets\redauxilio` conserva un commit local `d2e2d39` sin publicar y diverge de la rama remota, que ya fue integrada. No descartarlo ni hacer force push. El checkout principal no modifica el worktree de Carlos.

## Siguiente acción

Publicar la corrección en PR nuevo hacia `develop` y pedir revisión real de Carlos. Registrar por separado CI y validación humana; no aprobar el PR propio ni afirmar que el resto de RA-05 cumple DoD. Después, refinar RA-12 si hay capacidad y diseño suficientes, sin atribuir estimaciones a Carlos.
