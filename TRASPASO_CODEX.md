# RedAuxilio — integración RA-13 sobre RA-12

Actualización: 3 de noviembre de 2026, Colombia. PR #26 integrado por squash en develop d9cbc05012cd4bc71a893f0a463d8369beecd606. PR #27 permanece abierto; se actualiza desde 7fc964e88e3708e3dff587be43e80a77a65f82a8 mediante merge normal de esa base. Trabajo aislado en C:\Users\cmps-\RedAuxilio-recovery\ra13-integracion-ra12, rama auxiliar codex/ra13-integracion-ra12, con publicación autorizada exclusivamente a feat/RA-13-verificacion-oficial. No modificar worktrees originales, ramas de otras historias, servidores H2 ni respaldos de recuperación.

## Conciliación

El único conflicto fue este traspaso. schema.sql, app.js, index.html, CSS y pruebas del service worker se combinaron automáticamente. Se conservan RA-12: tres fotografías JPEG/PNG/WebP, decodificación completa, límites de 5 MiB/20 MP, validación MIME/extensión, privacidad, persistencia, multipart atómico, borradores y limpieza de fotos en cambios de sesión. Se conserva RA-13: permisos persistentes, concesión/revocación, verificación oficial por versión, clave id explícita, auditoría, privacidad y consultas. El shell v24 incluye el código público de verificación; imágenes y rutas privadas permanecen excluidas de caché. Contratos RA12.md y RA13.md.

## Evidencia y límites

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Resultados anteriores independientes: RA-12 nueve pruebas PostgreSQL; RA-13 cuatro y una comprobación adicional de edición. Sus CI y versiones se conservan en GitHub y la auditoría local ra12-ra13-review-20261103. No equivalen a pruebas del nuevo árbol combinado. Las evidencias históricas de Santiago y la resolución actual se registran en EVIDENCIAS.md para evitar estados contradictorios en este resumen.

La nueva regresión EvidenceOfficialIntegrationTests cubre una brecha entre historias: multipart 201 con fotografía, verificación oficial de esa publicación, consulta pública, permiso revocado, edición que invalida el respaldo y conservación de foto e historia. Solo prepara datos ficticios y limpia sus propias fixtures. Pruebas dirigidas PostgreSQL usan clúster/base nuevos; nunca bases existentes. El primer intento reveló contaminación entre pruebas por un evento VERIFY de la fixture nueva; se corrigió su limpieza, sin cambio productivo. Logs en ra13-integracion-qa-20261103, fuera de Git.

Resultados finales Maven/Node/formato/PostgreSQL en EVIDENCIAS.md y CI del HEAD publicado en PR #27. Siguiente: comprobar remoto sin avances inesperados, publicar un commit de merge coherente, verificar ausencia de conflictos y CI exacto. No aprobar formalmente ni integrar el PR; Carlos debe autorizar el paso final. QA visual/PWA y aceptación humana continúan pendientes, sin cambios administrativos ni Trello y sin atribuir aprobaciones inexistentes.
