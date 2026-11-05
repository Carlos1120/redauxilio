# RedAuxilio — correcciones finales RA-10

Actualización: 4 de noviembre de 2026, Colombia. PR #30 abierto, rama remota codex/ra10-avistamientos, base de revisión 13251e9b75c694d12c59f42599ec2c8c636e1d6a. Correcciones aisladas en C:/Users/cmps-/RedAuxilio-recovery/ra10-correcciones, rama auxiliar codex/ra10-correcciones; publicación normal autorizada al mismo PR si el remoto permanece sin avances. No modificar los worktrees de Santiago, RA-09, RA-11 ni la instancia de aceptación RA-12/13. Conservar respaldos y datos existentes.

## Alcance comprobado

Se atiende la revisión cruzada 5779067611: cuota persistente de cinco avistamientos por cuenta en una hora móvil; bloqueo de cuenta que serializa solicitudes entre casos distintos; HTTP 429 sin registros parciales. Los ocultos siguen contando y una operación revertida no consume cupo. La política reduce abuso de almacenamiento sin añadir infraestructura nueva.

Operador autorizado por permiso persistente RA-08 puede ocultar solo un avistamiento mediante ID y motivo obligatorio. El original se conserva y el evento privado incluye actor, fecha, motivo y cambio visible→oculto. Se excluye el anexo del historial público y se deniega su fotografía directa. Auditoría y ocultamiento son atómicos. El formulario administrativo mínimo y su consulta de auditoría reutilizan /operator; no implementan restauración ni otra historia.

Historial paginado con limit 1–20, offset no negativo y orden fecha observada/ID descendente. Cargar más conserva los resultados anteriores. Sin snapshot frente a modificaciones concurrentes: refrescar cuando cambie el historial. Shell v26 distribuye el JS actualizado, manteniendo exclusión de datos privados e imágenes. Se conserva provisionalmente la prohibición de nuevas observaciones en casos encontrados/cerrados, autorizada por Carlos para esta corrección.

## Evidencia y siguientes pasos

Reproducción original: sexta alta 201 en vez de 429 y página de dos devolvía tres. Regresiones corregidas: nueve Java dirigidas en H2 y nueve en PostgreSQL 18.3 nuevo y desechable; 43 JavaScript dirigidas y 133 finales. Pruebas limpian solo fixtures; una barrera valida destino antes de inicializar esquema y ejecutar fallos DDL controlados. Resultados Maven, cobertura y CI del HEAD final en EVIDENCIAS.md y PR #30. Logs y datos de pruebas quedan fuera de Git en ra10-qa.

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Siguiente: comprobar CI exacto, revisar visualmente Cargar más y ocultamiento por ID, y realizar aprobación cruzada real antes de integrar. No registrar APPROVED, no hacer merge ni modificar Trello. Los resultados históricos favorables de RA-12/13 permanecen en EVIDENCIAS.md; no equivalen a aceptación humana de RA-10.
