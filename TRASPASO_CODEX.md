# RedAuxilio — traspaso RA-11

Actualización: 2 de noviembre de 2026 (Colombia). Historia activa RA-11, contrato RA11.md; no reconstruir historias previas. Rama existente feat/RA-11-solicitudes-ayuda, base 72c5df8e0dfb9384b3668cbb52f177fa67c9ed20 y develop 7665aa6a8074e6c93f76786ebb4e6ad32333846e, ambos verificados en remoto antes de publicar. Trabajar exclusivamente en C:\Users\cmps-\RedAuxilio-recovery\20261102-194520\recovered-repository. El original sufrió corrupción del índice/referencia: permanece intacto, con respaldos independientes working-tree/git-metadata e inventario SHA-256 en el padre. Se recuperaron 18 archivos modificados y dos pruebas nuevas sin pérdida detectada respecto al respaldo. No volver al repositorio corrupto ni eliminar respaldos.

## Incremento

HELP_REQUEST persiste needType, approximateQuantity opcional, declaredUrgency y requesterReference en columnas anulables, con validación servidor/cliente y mapeo público selectivo. Alta ABIERTA; estados EN ATENCIÓN/SATISFECHA, cierre y reapertura motivada. Autoría, CSRF, auditoría/rollback, invalidación de confirmaciones, denuncias y moderación reutilizan módulos existentes. Vigencia RA-06 de 24 horas no se duplica. Edición idéntica conserva fecha, versión y respaldo; la UI informa «Sin cambios». Otras categorías y seis ejemplos mantienen compatibilidad.

Formulario, detalle, mapa/lista, Mis publicaciones y corrección de operador transportan los campos propios. IndexedDB conserva todos los campos del borrador; shell público v22, sin recursos privados nuevos. No se añaden dependencias, PostgreSQL, fotos ni historias posteriores.

## Validación y límites

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Maven verify: 142 Java aprobadas, Spotless/PMD SUCCESS; Node 109 aprobadas, Prettier y diff check correctos. Cobertura crítica 95,03–100%, umbral 80% superado. Evidencia A1–A7 y límites en la última sección RA-11 de EVIDENCIAS.md. No repetir suite completa sin cambios que la invaliden; consultar CI sobre el HEAD exacto del PR Draft de esta rama.

Instancia aislada http://127.0.0.1:59976/, JAR final local y H2 ra11-instance/qa fuera del repositorio, cookie RA11_RECOVERED. Casos 1000000–1000003 ficticios. Herramientas comprobaron HTTP, creación, borrador/recarga, edición/no-op, cierre/reapertura, historial, logout y vista de formulario en tres resoluciones. Instalación PWA y desconexión/reconexión reales, aceptación de Carlos y revisión cruzada siguen pendientes. No certificar PostgreSQL ni WCAG por estas comprobaciones.

Siguiente acción: comprobar CI del HEAD publicado y ejecutar el guion humano sin mezclar datos/servidores anteriores. Conservar PR Draft, sin merge ni Trello. Capacidad real/reparto/fecha objetivo pendientes: estimación 18–24 h-persona documentada, no es disponibilidad autorizada ni DoR cumplida. No atribuir aprobaciones a Santiago ni declarar RA-11 terminada.
