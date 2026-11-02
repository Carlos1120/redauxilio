# RedAuxilio — traspaso RA-06

Actualización: 2 de noviembre de 2026 (Colombia). Base verificada origin/develop `93db33bb8c7582f2160f23c966cb6bc46a68bb11`, CI 41521961942 SUCCESS y sin PR abiertos al inicio. Rama propia codex/RA-06-vigencia-visible, worktree aislado `C:\Users\cmps-\.codex\worktrees\ra06-vigencia\redauxilio`. La tarjeta RA-06 autoriza implementación y está En progreso; el encargo confirma WIP RA-05/RA-06. No se alteran ramas/servidores de Santiago ni instancias anteriores, y no se autoriza merge o cierre de tarjeta.

## Contrato implementado

Leer RA06.md y la última evidencia RA-06; no reconstruir las historias integradas. La vigencia se calcula con Clock al consultar: 24 horas para asistencia, vía y ayuda; 72 para desaparecidos. Exactamente al vencer sigue VIGENTE, después DESACTUALIZADA. Es una dimensión independiente de confianza, revisión, cierre y visibilidad. No hay scheduler, escrituras temporales ni auditoría de vencimiento inventada.

La actividad deriva de updated_at real y MAX de confirmaciones aceptadas de la versión actual, excluyendo al autor. La proyección pública compartida obtiene conteo y fecha sin consultas por publicación. Una confirmación aceptada renueva aunque no alcance tres; rechazos/versiones antiguas no renuevan. La edición idéntica ya no actualiza fecha, invalida ni audita. Se conservan constructores y filtro histórico de confianza DESACTUALIZADA; el nuevo freshnessStatus es independiente y se combina mediante AND. La API añade lastValidActivityAt/freshnessExpiresAt sin exponer identidades o auditoría interna.

Tarjetas/detalle muestran fecha y advertencia; revisión tiene prioridad sobre caducidad y confianza permanece visible. El shell v21 precarga freshness.js, un recurso público permitido sin autenticar. El cliente recalcula expiración de respuestas guardadas antes de compartirlas con mapa/lista/detalle, filtra y nunca rejuvenece una respuesta caducada al atrasar su reloj. No amplía acceso privado ni promete mapa offline completo.

## Validación y siguiente paso

Ocho pruebas Java nuevas controlan límites, actividad, confirmaciones/versiones, filtros, no-op, cerrados/ocultos y consulta única. Regresiones dirigidas con mantenimiento, confianza, moderación y acceso aprobadas. JS cubre caché expirada, dimensiones/prioridad, fechas y filtro, manteniendo los ejemplos ficticios. Resultados finales y entorno en EVIDENCIAS.md; consultar CI del HEAD publicado.

Pendientes reales: aceptación humana de filtros/fechas, no-op frente a edición real, confirmación renovadora, responsive y desconexión/reconexión. El reloj offline depende del dispositivo; no se certifica PostgreSQL ni se implementan RA-09/11/13. Mantener PR Draft independiente hacia develop y solicitar revisión cruzada posterior. La siguiente acción es comprobar CI exacto y validar la instancia ficticia, sin marcar RA-06 terminada.

QA P2 PR #24: se limita el reloj local a respuestas marcadas X-RedAuxilio-Saved-At; red y detalle conservan autoridad del servidor. Regresiones de reloj adelantado, filtro y caché aprobadas (102 JS totales); sin cambios Java/SW. Revalidación humana focalizada pendiente sobre el nuevo HEAD.
