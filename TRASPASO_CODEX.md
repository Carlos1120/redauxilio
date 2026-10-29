# RedAuxilio — traspaso correctivo RA-08

Actualización: 29 de octubre de 2026 (Colombia). Trabajo aislado en `C:\Users\cmps-\.codex\worktrees\ra08-corregir-rondas\redauxilio`, rama `fix/RA-08-resolver-rondas`, creada desde PR #20 HEAD `3c8193f8892eeb94dfffcd3761b79b0ce7f2db1c`. El PR de Santiago permanece en `feat/RA-08-acciones-moderacion`, basado en develop `73034e57c92df05f69ca9d4897153c3c67513ac3`. El PR correctivo debe ser Draft apilado hacia esa rama; no modificarla ni fusionar automáticamente. Los demás worktrees/servidores y sus datos se preservan.

## Contrato y discrepancia demostrada

Consultar RA08.md y la última sección RA-08 de EVIDENCIAS.md; no reconstruir contexto antiguo. Carlos aprobó corrección sustancial, nuevas denuncias tras resolución y reapertura explícita en Trello (acción 6ae3bdabb77ef7d3b54f8106). Aprobación de políticas no es revisión formal de GitHub. La prioridad fue comprobar resolución de denuncias/revisión en CORRECT/HIDE/RESTORE/CLOSE frente a KEEP; las cinco deben cerrar la ronda de moderación conservando historia.

La regresión nueva falló en las cuatro acciones: respuesta EN_REVISION tras una decisión exitosa. KEEP fue el control positivo. La resolución SQL estaba limitada a KEEP. Ahora las cinco decisiones válidas comparten el cierre de ronda después de ejecutar su operación y antes de auditar: active_slot NULL y review_status SIN_REVISION. Respuesta y snapshot final incluyen cero activas. La resolución mantiene el bloqueo por publicación y la transacción existente; rechazos o fallos de auditoría revierten operación, resolución e invalidaciones. No se borran denuncias ni se añade otra política.

Corrección real y cierre mantienen invalidación de confirmaciones/versión RA-04/05; ocultar/restituir no cambian respaldos ni cierre. Una cuenta puede denunciar otra vez tras la resolución, y solo activas nuevas cuentan para el umbral RA-07. REOPEN sigue independiente y no resuelve una ronda recién creada. No hay cambios de UI, permisos ni aprovisionamiento.

## Verificación y pendientes

Específicas Java: ModerationQueueTests 23 aprobadas, con 12 ejecuciones nuevas para las cinco decisiones, rollback, concurrencia y cierre/reapertura vial. JS de moderación/reporte específico aprobado. Batería completa final: 107 Java, 86 JS, Spotless/PMD, npm ci y Prettier aprobados; diff --check se comprueba al cerrar. JS específicas: 26. Consultar CI del HEAD publicado; no reutilizar resultados de otro SHA como validación del nuevo.

Conservar PR correctivo Draft y el PR #20 abierto, sin force push ni merge. Pendientes reales: revisión de Carlos/Santiago sobre el cambio y validación humana del segundo bloque; contexto previo también mantiene PostgreSQL no certificado y responsive manual pendiente. RF-16 de envío manual a revisión no se implementa dentro de esta corrección. No declarar DoD ni ampliar alcance. Entrega autorizada en un único commit coherente con push normal solo de la rama correctiva y PR Draft apilado. Siguiente acción: comprobar CI exacto y revisión humana antes de cualquier integración.
