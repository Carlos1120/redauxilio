# Reglas para agentes

- Al retomar, consultar el resumen vigente de TRASPASO_CODEX.md y el contrato RAxx.md pertinente. ESTANDARES.md es la norma; distinguir PR pendientes de reglas integradas y verificar siempre el estado remoto cambiante.
- Antes de crear o modificar pantallas, leer ESTANDARES_INTERFAZ.md y reutilizar los tokens y componentes de app.css. Conservar la dirección Onest/rojo/mapa del PR #8; no mezclar la paleta azul anterior. Registrar cambios de dirección en la guía y someterlos a revisión del compañero.
- Antes de modificar, comprobar carpeta, estado Git, rama y remotos; preservar cambios locales. Continuar historias en su rama y crear trabajo nuevo desde develop actualizado.
- Ahorrar contexto: localizar primero con búsqueda por archivo/símbolo y abrir solo fragmentos pertinentes. No volcar archivos grandes (EVIDENCIAS.md, ESTANDARES.md, app.css) ni historiales completos por rutina.
- Encargos cortos: rama, objetivo, criterios específicos y entrega. No reiterar estas reglas, los estándares o la arquitectura en cada prompt; ampliar solo ante una decisión técnica nueva.
- Pruebas escalonadas: durante el cambio, ejecutar primero pruebas específicas; antes de publicar un incremento, ejecutar verificaciones completas de Maven/Node/formato y revisar CI sobre el HEAD exacto. No repetir suites del mismo SHA sin razón nueva.
- Mantener TRASPASO_CODEX.md entre 300 y 500 palabras: estado verificado, rama/PR, riesgos, bloqueos y siguiente paso. Guardar historia y resultados extensos en GitHub/EVIDENCIAS.md, pero consultarlos solo cuando haga falta.
- Documentar código nuevo o modificado en español: propósito, entradas/salidas, reglas y límites. Usar identificadores en inglés y actualizar documentación si cambia el comportamiento.
- Agrupar archivos relacionados en commits coherentes según ESTANDARES.md. Usar la identidad Git configurada por Carlos, sin atribuciones del agente en commits; distinguir ejecuciones del agente en la documentación de evidencias.
- PR hacia develop, revisión real del compañero y comprobaciones sobre la versión final; integración por squash. No inventar aprobaciones, pruebas ni estados del tablero.
- No descartar cambios, hacer force push, subir secretos ni ampliar el alcance. No delegar por defecto ni trabajar simultáneamente sobre la misma rama/carpeta.
- Responder en español breve con HEAD, PR, pruebas/CI, bloqueos y siguiente acción; distinguir agente, humano y pendientes. Actualizar el traspaso solo con información vigente al cerrar el bloque.

## Mapa de trabajo

- Historia activa de esta rama: RA07.md y tarjeta RA-07. Para contexto integrado: RA04.md/RA05.md; corrección RA-05 pendiente: PR #15 (verificar antes de integrar). Windows: VALIDACION_WINDOWS.md; evidencias históricas: EVIDENCIAS.md.
- Servidor: src/main/java/co/redauxilio/publications/PublicationController.java y PublicationService.java.
- UI: src/main/resources/templates/index.html y src/main/resources/static/{app.js,map.js,app.css}.
- PWA: src/main/resources/static/sw.js y manifest.webmanifest.
- Pruebas: src/test/java/co/redauxilio/publications/PublicationTests.java y src/test/js/map.test.cjs.
- Calidad/versiones: pom.xml, package.json; CI: .github/workflows/verify.yml; PR: .github/PULL_REQUEST_TEMPLATE.md.
- Consultar comandos en VALIDACION_WINDOWS.md; no cargar todos los archivos del mapa por rutina.
