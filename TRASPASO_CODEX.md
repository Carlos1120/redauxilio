# RedAuxilio: estado para continuar

Instantánea del 6 de octubre de 2026 (Colombia). Normas en AGENTS.md y ESTANDARES.md; evidencia en EVIDENCIAS.md; antecedentes completos conservados en Git.

## Objetivo y contexto

Continuar validación RA-02 en Windows; no iniciar otra funcionalidad. Carlos Mario Peña responsable, Santiago Ortiz Ochoa revisor; equipo 4 de dos autorizado según Carlos, interlocutor Javier Charry. Conservar 25 RF/15 RNF originales.
Repositorio local de Santiago `C:\Users\Santiago\redauxilio`; remoto https://github.com/Carlos1120/redauxilio.
Tablero https://trello.com/b/BiGr7UFw/redauxilio-gerencia-de-software; tarjeta RA-02 https://trello.com/c/LYDxDTZT.

## Estado conocido

- Rama de este bloque: `feat/RA-02-interfaz-mapa`, nacida de `develop` en `e0fdb1a`. Comprobar HEAD/status y estado del PR al retomar; la integración requiere revisión de Carlos.
- `origin/develop` observado en `e0fdb1a` el 6 de octubre de 2026; incluye la integración de los PR #5 y #6. RA-00 integrada; versión y límites en EVIDENCIAS.md.
- En este equipo `mvnw verify` pasó con Corretto 21.0.10; OpenJDK 25 ejecutó las pruebas, pero Spotless falló por incompatibilidad del formateador. Arranque actualizado: HTTP 200 y cuatro publicaciones en puerto temporal 8081; servidor 8080 de IntelliJ preservado.
- Pruebas previas: 22 Java/3 Node, formato/Spotless/PMD aprobados. UI preliminar: filtros combinados, detalle Enter/Escape y retorno de foco, anchos sin desbordamiento DOM. No acredita revisión visual completa ni servidor UI reiniciado al SHA actual.
- La corrección del marcador agrupado por autoPan quedó integrada con RA-02. Se conservan capas de grupos sin cambios; datos, miembros o agrupación distintos se reemplazan. Evidencia previa en EVIDENCIAS.md.

## Producto y límites

Monolito Java/Spring Boot, Thymeleaf y JS/CSS; IndexedDB/service worker. Leaflet 1.9.4/OSM, área visible, celdas de 64 píxeles, filtros y detalle del objeto consultado. Cuatro activos visibles, un cerrado visible, un oculto excluido por servidor. Contrato y criterios: RA02.md.
Sin persistencia, autenticación ni publicación real; borrador local no publica. La cartografía requiere red; Leaflet se sirve localmente. No hay descarga masiva ni caché de mosaicos externos por service worker. Consultas guardadas dependen de URL exacta y pueden estar desactualizadas. No ampliar arquitectura ni tocar trabajo compartido de Santiago sin verificar sus ramas.

## Actualización local de Santiago — 6 de octubre de 2026

- Esta rama incorpora el arreglo de carga de Leaflet: recursos 1.9.4 y licencia bajo `static/vendor/leaflet`, URL OSM con subdominios y caché PWA v6. La fuente Onest y su licencia están bajo `static/vendor/onest`. Edge requirió actualizar el service worker para mostrar el mapa antes del rediseño.
- Santiago entregó `Downloads/Red Auxilio.html` y una imagen de referencia, y pidió respetar su aspecto: Onest, rojo `#d7262e`, mapa de fondo, panel de reportes izquierdo y detalle derecho. El prototipo local conserva consultas, filtros, mapa, detalle y borrador; añade búsqueda local entre resultados. El borrador queda debajo del mapa. No añadir geolocalización, alerta sísmica, rutas ni datos oficiales falsos. `system.md` registra la dirección; futuro móvil nativo no se implementó.
- Comprobado en servidor temporal `http://localhost:8081/`: página renderizada, mapa OSM y cuatro marcadores, paneles a 1440 × 900, búsqueda que reduce simultáneamente lista/mapa y filtro rápido de vías con conteo singular correcto. Revisión visual a 360 × 800, 768 × 1024 y 1366 × 768 sin desbordamiento horizontal; selectores completos en tableta y detalle modal móvil. Borrador guardado, recuperado tras recarga y descartado; con servidor propio detenido, consulta guardada con fecha y posterior recuperación desde servidor. Evidencia detallada y límites en EVIDENCIAS.md. Cinco pruebas Node y 22 Java aprobadas, formato Prettier, Spotless, PMD y `git diff --check` sin errores. `mvnw verify` pasa con Corretto 21; con OpenJDK 25 las pruebas pasan, pero Spotless falla por incompatibilidad del formateador.
- Conservar la rama y obtener revisión de Carlos antes de integrar. La app previa en `:8080` puede conservar la plantilla en memoria; reiniciarla desde IntelliJ para actualizarla. El servidor temporal `:8081` se usó como vista previa y puede detenerse.

## Siguiente bloque

1. Comprobar carpeta/Git; usar VALIDACION_WINDOWS.md para arrancar versión identificable preservando servidores ajenos.
2. Completar revisión visual de todas las secciones en tres tamaños, instalación PWA, desconexión total/reconexión y bloqueo CDN/mapa. Ya comprobados en `484e1da`: recorrido Tab, Shift+Tab y diálogo, guardar/recuperar/descartar borrador; caché de consulta exacta ante caída del servidor, error de filtro sin caché y recuperación al restaurar servidor. No equivale a navegador offline ni auditoría completa; evidencia en EVIDENCIAS.md. CI de `484e1da` aprobado. Flujo de selección de grupo comprobado tras corrección.
3. Completar RNF-10 accesibilidad y RNF-01: 1.000 publicaciones/20 usuarios, p95 servidor ≤2 s, primeros resultados ≤3 s con red documentada.
4. DoR contrastado y boceto enlazado en RA02.md. Carlos confirmó Sprint 1 del 5 al 19 de octubre de 2026 y unas 10 h propias disponibles: 7 h efectivas tras reserva del 30 %, sin sumar capacidad de Santiago. Pendiente confirmación del incremento sintético con confianza Reportada. Reestimar esfuerzo restante; los 6–8 h-persona eran estimación de implementación, pruebas y revisión, no saldo medido. Tras resolver estos datos, registrar selección y mover tarjeta a En progreso si cumple las seis condiciones y el WIP. No esperar respuesta previa de Santiago; revisión cruzada antes de integrar se conserva. No declarar DoR/DoD completos sin evidencia.
5. Revisar el PR de `feat/RA-02-interfaz-mapa` con Carlos, resolver bloqueantes y validar la versión integrada en `develop`; no confundir el envío de la rama con cierre DoD.

Lectura mínima: este estado y AGENTS.md; ESTANDARES.md al aplicar normas, RA02.md para aceptación, VALIDACION_WINDOWS.md para ejecución. GUIA_CODIGO.md y .interface-design/system.md solo al estudiar código/diseño. Skills globales: validar-app-local, preparar-entrega, regresion-ui, validar-rendimiento y verificar-trazabilidad; leen los comandos y criterios de este proyecto. Sustituyen redauxilio-windows y redauxilio-entrega; no presumir transferidas otras skills de conversaciones previas.
