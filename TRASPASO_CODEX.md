# RedAuxilio — traspaso de corrección PostgreSQL

Actualización: 3 de noviembre de 2026, Colombia. PR #28 ya integrado; develop verificado 3fa21ac2abdcbc38306f92fa5608b3f67f385458, CI posterior al merge 41657715567 SUCCESS. Este bloque trabaja exclusivamente en la nueva rama fix/RA-09-postgresql-generated-key, desde esa base, en C:\Users\cmps-\RedAuxilio-recovery\ra09-postgresql-generated-key. Git sano; worktrees, servidores, bases H2 y respaldos originales preservados. No volver al repositorio corrupto. Contrato funcional RA09.md y registros históricos en EVIDENCIAS.md; consultar únicamente lo pertinente.

## Cambio y reproducción

La auditoría adicional demostró un P1 común a las cuatro categorías: PostgreSQL devuelve todas las columnas al solicitar claves generadas sin indicar columna, y getKey falla con múltiples claves. CitizenPublicationRepository.insert ahora solicita exclusivamente id. No se modifican esquema, API, validaciones, cuotas, autoría, CSRF, auditoría ni transacciones. Es el único uso de claves generadas en producción; el defecto precedía a RA-09. No se amplía el alcance funcional ni se implementan historias posteriores.

PublicationGeneratedKeyTests reproduce cuatro altas HTTP 500 antes del arreglo y verifica seis casos después, tanto en PostgreSQL 18.3 como en H2: cuatro categorías con HTTP 201, ID persistido, autoría, consulta pública y propia; validaciones sin filas parciales; rollback posterior al INSERT. No usa fixtures SQL de publicaciones para acreditar altas HTTP. Configuración opcional RA_KEY_TEST_DB_URL, RA_KEY_TEST_DB_USERNAME y RA_KEY_TEST_DB_PASSWORD; sin variables utiliza H2 en memoria. Solo ejecutar contra una base de pruebas autorizada. PostgreSQL local propio, puerto 56261, base ra09_key_fix; cuentas sintéticas independientes, sin borrar datos ajenos ni modificar servicios Windows.

## Calidad y entrega

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Dirigidas: seis PostgreSQL y seis H2 aprobadas. Verificación completa única: 163 Java y 121 JS PASS, Spotless/PMD/Prettier correctos. Cobertura de líneas del repositorio 100%, servicio de creación 94,06%. Logs locales en ra09-pg-audit-20261103, fuera de Git; CI H2 es independiente del resultado PostgreSQL local. Publicar un solo commit coherente y PR Draft hacia develop; comprobar SHA y CI exactos antes de recomendar integración. No fusionar ni atribuir revisión formal.

RA-09 conserva sus salvaguardas de contacto separado/no-store, retiro, consentimiento adulto, estados y vigencia 72 h. Sus resultados H2/E2E anteriores permanecen documentados; no repetir geometría/PWA sin motivo. Instalación efectiva PWA, aceptación de Carlos y revisión cruzada siguen pendientes. No declarar RA-09 terminada, DoR/DoD satisfecha ni actualizar Trello. Tras esta corrección, el siguiente paso es revisión real del PR y aceptación pendiente del incremento.
