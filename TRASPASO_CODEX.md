# RedAuxilio: estado para continuar

Instantánea del 6 de octubre de 2026 (Colombia). Normas en AGENTS.md y ESTANDARES.md; evidencia en EVIDENCIAS.md; antecedentes completos conservados en Git.

## Objetivo y contexto

Continuar validación RA-02 en Windows; no iniciar otra funcionalidad. Carlos Mario Peña responsable, Santiago Ortiz Ochoa revisor; equipo 4 de dos autorizado según Carlos, interlocutor Javier Charry. Conservar 25 RF/15 RNF originales.
Repositorio C:\Users\cmps-\redauxilio; remoto https://github.com/Carlos1120/redauxilio.
Tablero https://trello.com/b/BiGr7UFw/redauxilio-gerencia-de-software; tarjeta RA-02 https://trello.com/c/LYDxDTZT.

## Estado conocido

- Rama feat/RA-02-consulta-geografica; código remoto comprobado 2371a92674a42fc26459f78568b6b52b81560d8e. Commit local 0884239 preparó reglas/evidencias; este bloque añade documentación. Comprobar HEAD/status al retomar.
- origin/develop observado 1e7accc90e4cb1ab398e7315b1a9dc141a47b7ec. RA-00 integrada y validada por Carlos; versión/límites en EVIDENCIAS.md.
- PR #5 abierto hacia develop, no integrado al verificar: https://github.com/Carlos1120/redauxilio/pull/5. Propone estándares v1.1 y capturas opcionales; local conserva v1.0. Revisión/aceptación de Santiago pendientes.
- PR #6 en borrador hacia develop: https://github.com/Carlos1120/redauxilio/pull/6. CI #48 aprobado sobre 2371a92 según traspaso previo; checks actuales no confirmados. gh devuelve 401, web no expone resultados. No repetir sin motivo/acceso nuevo.
- Windows confirmado: Java/javac Temurin 17.0.20.1, JAVA_HOME correcto, Node 24.19.0/npm 11.17.0, Git 2.45.1. Arranque desde código actualizado: HTTP 200 y cuatro publicaciones en puerto temporal; proceso detenido. Servidor previo 8080 preservado, versión cargada no acreditada.
- Pruebas previas: 22 Java/3 Node, formato/Spotless/PMD aprobados. UI preliminar: filtros combinados, detalle Enter/Escape y retorno de foco, anchos sin desbordamiento DOM. No acredita revisión visual completa ni servidor UI reiniciado al SHA actual.
- Fallo del grupo reproducido y corregido localmente: autoPan dispara consulta y el render borraba su marcador. Se conservan capas de grupos sin cambios; datos/miembros distintos se reemplazan. Comprobado con instancia nueva, ambos detalles, retorno de foco, zoom y selección en tres tamaños; evidencia en EVIDENCIAS.md. Cinco pruebas Node y 22 Java, formato/Spotless/PMD aprobados. Publicación/revisión de la corrección pendientes.

## Producto y límites

Monolito Java/Spring Boot, Thymeleaf y JS/CSS; IndexedDB/service worker. Leaflet 1.9.4/OSM, área visible, celdas de 64 píxeles, filtros y detalle del objeto consultado. Cuatro activos visibles, un cerrado visible, un oculto excluido por servidor. Contrato y criterios: RA02.md.
Sin persistencia, autenticación ni publicación real; borrador local no publica. CDN/cartografía requieren red, sin descarga masiva ni caché por service worker. Consultas guardadas dependen de URL exacta y pueden estar desactualizadas. No ampliar arquitectura ni tocar trabajo compartido de Santiago sin verificar sus ramas.

## Siguiente bloque

1. Comprobar carpeta/Git; usar VALIDACION_WINDOWS.md para arrancar versión identificable preservando servidores ajenos.
2. Completar revisión visual de todas las secciones en tres tamaños, instalación PWA, desconexión total/reconexión y bloqueo CDN/mapa. Ya comprobados en `484e1da`: recorrido Tab, Shift+Tab y diálogo, guardar/recuperar/descartar borrador; caché de consulta exacta ante caída del servidor, error de filtro sin caché y recuperación al restaurar servidor. No equivale a navegador offline ni auditoría completa; evidencia en EVIDENCIAS.md. CI de `484e1da` aprobado. Flujo de selección de grupo comprobado tras corrección.
3. Completar RNF-10 accesibilidad y RNF-01: 1.000 publicaciones/20 usuarios, p95 servidor ≤2 s, primeros resultados ≤3 s con red documentada.
4. DoR contrastado y boceto enlazado en RA02.md. Carlos confirmó Sprint 1 del 5 al 19 de octubre de 2026 y unas 10 h propias disponibles: 7 h efectivas tras reserva del 30 %, sin sumar capacidad de Santiago. Pendiente confirmación del incremento sintético con confianza Reportada. Reestimar esfuerzo restante; los 6–8 h-persona eran estimación de implementación, pruebas y revisión, no saldo medido. Tras resolver estos datos, registrar selección y mover tarjeta a En progreso si cumple las seis condiciones y el WIP. No esperar respuesta previa de Santiago; revisión cruzada antes de integrar se conserva. No declarar DoR/DoD completos sin evidencia.
5. Atender #5 y revisión final #6; integrar cumpliendo estándares.

Lectura mínima: este estado y AGENTS.md; ESTANDARES.md al aplicar normas, RA02.md para aceptación, VALIDACION_WINDOWS.md para ejecución. GUIA_CODIGO.md y .interface-design/system.md solo al estudiar código/diseño. Skills globales: validar-app-local, preparar-entrega, regresion-ui, validar-rendimiento y verificar-trazabilidad; leen los comandos y criterios de este proyecto. Sustituyen redauxilio-windows y redauxilio-entrega; no presumir transferidas otras skills de conversaciones previas.
