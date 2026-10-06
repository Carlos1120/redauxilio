# Reglas para agentes

- Leer TRASPASO_CODEX.md al retomar; ESTANDARES.md es la norma. Distinguir cambios pendientes de PR de reglas integradas.
- Antes de modificar, comprobar carpeta, estado Git, rama y remotos; preservar cambios locales. Continuar historias en su rama y crear trabajo nuevo desde develop actualizado.
- Leer solo los documentos y fragmentos necesarios. No repetir pruebas sobre el mismo SHA sin una razón nueva.
- Documentar código nuevo o modificado en español: propósito, entradas/salidas, reglas y límites. Usar identificadores en inglés y actualizar documentación si cambia el comportamiento.
- Agrupar archivos relacionados en commits coherentes según ESTANDARES.md. Usar la identidad Git configurada por Carlos, sin atribuciones del agente en commits; distinguir ejecuciones del agente en la documentación de evidencias.
- PR hacia develop, revisión real del compañero y comprobaciones sobre la versión final; integración por squash. No inventar aprobaciones, pruebas ni estados del tablero.
- No descartar cambios, hacer force push, subir secretos ni ampliar el alcance. No delegar por defecto ni trabajar simultáneamente sobre la misma rama/carpeta.
- Explicar avances en español breve; distinguir resultados del agente, validación humana y pendientes. Actualizar el traspaso al cerrar el bloque.

## Mapa de trabajo

- Aceptación/API: RA02.md; ejecución Windows: VALIDACION_WINDOWS.md; resultados: EVIDENCIAS.md.
- Servidor: src/main/java/co/redauxilio/publications/PublicationController.java y PublicationService.java.
- UI: src/main/resources/templates/index.html y src/main/resources/static/{app.js,map.js,app.css}.
- PWA: src/main/resources/static/sw.js y manifest.webmanifest.
- Pruebas: src/test/java/co/redauxilio/publications/PublicationTests.java y src/test/js/map.test.cjs.
- Calidad/versiones: pom.xml, package.json; CI: .github/workflows/verify.yml; PR: .github/PULL_REQUEST_TEMPLATE.md.
- Consultar comandos en VALIDACION_WINDOWS.md; no cargar todos los archivos del mapa por rutina.
