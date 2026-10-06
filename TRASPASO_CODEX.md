# RedAuxilio: traspaso a Codex local

Estado verificado el 6 de octubre de 2026. Este resumen es una instantánea: comprobar Git/PR antes de tomar decisiones. No importar la conversación completa.

## Proyecto y fuentes

- Equipo 4: Carlos Mario Peña (responsable de RA-02) y Santiago Ortiz Ochoa (revisor). Docente/interlocutor: Javier Charry. Según Carlos, el docente autorizó dos integrantes.
- Repositorio: https://github.com/Carlos1120/redauxilio
- Carpeta Windows indicada por Carlos: `C:\Users\cmps-\redauxilio`; confirmar ubicación real.
- Trello: https://trello.com/b/BiGr7UFw/redauxilio-gerencia-de-software
- RA-02: https://trello.com/c/LYDxDTZT
- PWA académica para información georreferenciada ante emergencias. Conservar los 25 RF y 15 RNF del alcance original. No inventar requisitos ni dar por implementado el producto completo.
- Arquitectura actual: monolito Java 17/Spring Boot, Maven Wrapper, Thymeleaf, JavaScript/CSS sin framework; IndexedDB y service worker. Herramientas frontend: Node 24/npm 11/Prettier. Versiones exactas en pom.xml/package.json.

## Estado de Git y entregas

- RA-00 (base técnica) integrada en develop y validada manualmente por Carlos: filtro, persistencia/descarte del borrador, consulta offline, tres anchos, validaciones y recorrido por teclado. Consultar EVIDENCIAS.md para límites y versión.
- PR #5: https://github.com/Carlos1120/redauxilio/pull/5. Abierto, hacia develop, rama docs/manual-test-evidence. Actualiza estándares a v1.1: capturas opcionales; conservar registros manuales reales, revisión del compañero y CI. CI #37 aprobado; aceptación/revisión de Santiago e integración pendientes al verificar este resumen. No tratarlo como ya fusionado.
- PR #6: https://github.com/Carlos1120/redauxilio/pull/6. Abierto EN BORRADOR, hacia develop, rama feat/RA-02-consulta-geografica. Último SHA verificado: 2371a92674a42fc26459f78568b6b52b81560d8e. CI #48 aprobado sobre ese SHA.
- develop verificado previamente: 1e7accc90e4cb1ab398e7315b1a9dc141a47b7ec. Actualizar referencias antes de trabajar.
- RA-02 implementa mapa Leaflet 1.9.4/OpenStreetMap, agrupación por celdas de 64 píxeles según zoom, consulta por área visible, filtros combinados, inclusión explícita de cerrados y detalle. Servidor excluye ocultos. Datos: cuatro activos visibles, uno cerrado visible, uno oculto. No hay base de datos persistente, autenticación ni publicación real.
- API: GET /api/publications con category, operationalStatus, confidenceLevel, includeClosed y south/west/north/east opcionales (los cuatro si llega alguno); GET /api/publications/{id}. Invalidación 400 y oculto/inexistente 404. Detalle UI usa el objeto de la consulta; no vuelve a pedir detalle.
- Verificación: 22 pruebas Java y 3 Node aprobadas; formato, Spotless y PMD aprobados. Se añadieron comentarios y GUIA_CODIGO.md. No atribuir estas ejecuciones del agente a los integrantes.
- Pendiente RA-02: pruebas reales de navegador, teclado/PWA, accesibilidad, rendimiento con 1000 publicaciones/20 usuarios (p95 servidor ≤2 s; primeros resultados ≤3 s con red documentada), revisión de Santiago e integración. Estimación propuesta por Carlos: 6–8 horas-persona, pendiente de contraste de Santiago. Trello sigue Por hacer hasta acordar preparación/selección; no está terminado.
- Cartografía y CDN requieren conexión. No guardar mapas externos en service worker ni hacer descarga masiva. Consultas guardadas dependen de la URL exacta; son potencialmente antiguas. Borrador local no es publicación.
- No reemplazar requisitos por arquitectura más compleja. Persistencia y seguridad corresponden a próximos incrementos; Santiago tiene trabajo propio: verificar sus ramas antes de tocar archivos compartidos.

## Reglas permanentes de trabajo

1. Leer ESTANDARES.md como norma del repositorio; distinguir versión en develop del cambio pendiente del PR #5. No fusionar ni declarar aprobaciones inexistentes.
2. Todo código nuevo/modificado debe quedar documentado en español: propósito, entradas/salidas, reglas, decisiones no evidentes y límites; actualizar documentación cuando cambia comportamiento. Identificadores en inglés. Evitar comentarios que repiten instrucciones obvias.
3. Explicar a Carlos qué se hace, por qué y cómo se verifica, en español sencillo y con mensajes breves. Dar pasos por bloques útiles, no una conversación por cada comando.
4. Crear trabajo nuevo desde develop actualizado. Para continuar RA-02, usar su rama existente; no sobrescribir cambios locales. PR hacia develop, revisión real del compañero sobre versión final y CI aprobado; squash al integrar. main solo recibe promociones de develop.
5. Un commit por cambio coherente, agrupando archivos relacionados; no un commit por clase/archivo. Formato tipo(modulo): descripción [RA-XX], conforme al estándar. Usar la identidad Git configurada por Carlos, sin autoría ni coautoría de Codex en commits, según su instrucción del 6 de octubre de 2026; nunca suplantar a Santiago ni inventar firmas. Conservar la identificación veraz de ejecuciones del agente en evidencias.
6. No hacer force push, reset destructivo ni descartar cambios del usuario. No pedir secretos en chat ni ponerlos en Git. Autorización previa de Carlos permite tareas rutinarias necesarias sin confirmar cada paso; respetar las protecciones de la aplicación y pedir aclaración solo si es material.
7. Verificar documentación oficial vigente cuando cambien versiones, APIs o decisiones técnicas. Fuentes internas originales/repo antes de suponer. Distinguir pruebas hechas y pendientes.
8. No abrir múltiples tareas que modifiquen la misma rama/carpeta simultáneamente. No delegar a otros agentes por defecto. Actualizar estado del tablero solo con evidencia real y acceso disponible; no asumir que los conectores de esta conversación se transfieren a Codex local.

## Lectura mínima y ahorro de contexto

- Inicio: este archivo, git status, rama/remotos; consultar títulos/estado/CI de PR #5/#6 si hay acceso.
- Primera sesión: ESTANDARES.md, RA02.md y README.md. GUIA_CODIGO.md al estudiar o modificar código; EVIDENCIAS.md para comprobar resultados. Leer archivos de código y .interface-design/system.md solo según la tarea.
- No cargar toda la conversación, PDFs, ZIP, node_modules, target ni logs completos. Buscar con rg y extraer fragmentos relevantes. Tener archivos en disco no requiere pegarlos todos en el chat; los fragmentos leídos sí usan contexto.
- No repetir instalación/pruebas completas si el mismo SHA ya tiene verificación válida y no existe una razón nueva. Antes de integrar, cumplir checks del estándar sobre la versión final.
- Proponer AGENTS.md breve con reglas permanentes, sin duplicar ESTANDARES.md. Mantener este archivo como estado de traspaso. Actualizar pendientes/SHA al cerrar una tarea; nueva conversación por objetivo, usando los archivos como referencia.

## Primera tarea al abrir Codex

Leer este resumen, comprobar carpeta/rama/cambios y sincronización sin modificar nada. Confirmar Java 17, Node y Git. Preparar RA-02 para probarlo en Windows, contrastar DoR/estimación con Santiago y atender PR #5. No iniciar otra funcionalidad hasta resolver esa continuidad. Revisar solo los archivos necesarios y devolver estado + siguiente bloque de acciones en máximo 10 líneas.

Comandos de comprobación habituales en PowerShell: `git status`, `git fetch origin`, `java -version`, `javac -version`, `npm.cmd ci`, `npm.cmd test`, `npm.cmd run format:check`, `.\mvnw.cmd verify`, `.\mvnw.cmd spring-boot:run`. npm.cmd evita el bloqueo de npm.ps1 observado. Comprobar JAVA_HOME/PATH porque antes apuntaban a Java 23; no fijar una ruta de JDK sin verificarla. git push ya funcionó en PowerShell de Carlos; confirmar que Codex local tiene acceso antes de darlo por transferido.

La skill ui-ux-design-pro se utilizó para la interfaz; sus instrucciones/instalación no se transfieren automáticamente. El diseño aplicado está en .interface-design/system.md. Si se vuelve a necesitar la skill, verificar instalación local y leerla antes de usarla.

## Continuidad local — 6 de octubre de 2026

- Rama RA-02 actualizada por avance directo a 2371a92674a42fc26459f78568b6b52b81560d8e; entorno Windows Java 17, Node 24 y npm 11 confirmado.
- AGENTS.md conserva reglas permanentes. EVIDENCIAS.md registra comprobaciones preliminares de UI, límites de las capturas y arranque/HTTP del SHA actualizado en puerto temporal; servidor previo de 8080 preservado.
- Observación pendiente: abrir un grupo puede mover el área y cerrar su menú antes de seleccionar un reporte. Reproducir antes de diagnosticar o corregir.
- PR #5 abierto y #6 en borrador según páginas públicas; gh devuelve 401. CI actual no confirmado. No repetir consultas sin acceso nuevo o cambios remotos.
- Siguiente bloque: validar UI sobre servidor reiniciado desde el SHA actual, selección desde grupo, tres tamaños completos, teclado/PWA y desconexión; después accesibilidad y carga 1000/20. DoR, estimación y revisión de Santiago siguen pendientes. Sin funcionalidad nueva.
