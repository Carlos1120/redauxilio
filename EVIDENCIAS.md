# Evidencia técnica del primer incremento RA-00

Ejecución por Codex: 6 de octubre de 2026 UTC / 5 de octubre en Colombia. Revisión humana pendiente. Datos exclusivamente ficticios.

## Resultados ejecutados

| Comprobación                                 | Resultado                                                | Evidencia reproducible                                              |
| -------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------- |
| Maven verify con Java 17.0.20 y Maven 3.9.11 | BUILD SUCCESS                                            | `./mvnw verify`                                                     |
| JUnit y MockMvc                              | 14 ejecuciones; 0 fallos, 0 errores, 0 omitidas          | `target/surefire-reports/`                                          |
| Spotless                                     | formato Java aprobado                                    | etapa `spotless:check` de verify                                    |
| PMD                                          | 0 infracciones con las reglas predeterminadas del plugin | `target/pmd.xml`                                                    |
| Cobertura de líneas Java                     | 18/20 = 90 %                                             | `target/site/jacoco/index.html`                                     |
| Cobertura de ramas Java                      | 10/10 = 100 %                                            | mismo reporte                                                       |
| Cobertura de instrucciones Java              | 149/154 = 96,75 %                                        | mismo reporte                                                       |
| Sintaxis JavaScript                          | app.js y sw.js aprobados                                 | `node --check src/main/resources/static/app.js` y equivalente sw.js |
| JAR ejecutable                               | arranca en puerto 8080; inicio HTTP 200                  | `java -jar target/redauxilio-0.1.0-SNAPSHOT.jar`                    |
| API por HTTP real                            | ROAD: HTTP 200, un reporte ROAD; OTHER: HTTP 400         | consulta de endpoints con servidor arrancado                        |

La cobertura solo corresponde al código Java existente, cuatro clases incluyendo el record. No mide JavaScript, service worker, IndexedDB, accesibilidad ni módulos futuros. El número de ejecuciones incluye parámetros; no equivale a doce casos académicos diseñados. No se declara cumplimiento global de RNF-14 ni ausencia de defectos por estos resultados.

## Casos implementados

| Prueba                              | Ejecuciones | Resultado esperado                   |
| ----------------------------------- | ----------: | ------------------------------------ |
| Consulta anónima sin categoría      |           1 | cuatro datos ficticios, HTTP 200     |
| Cada categoría válida               |           4 | un resultado de la categoría         |
| Categoría con espacios y minúsculas |           1 | normalización a ROAD                 |
| Categoría desconocida               |           1 | HTTP 400                             |
| Categoría vacía                     |           1 | cuatro resultados                    |
| Inicio                              |           1 | plantilla y aviso de datos ficticios |
| Recursos PWA                        |           5 | recursos servidos con HTTP 200       |

## Comprobaciones pendientes de ejecutar

- En navegador: guardar, recargar, recuperar y descartar un borrador; límites del formulario y fallo de almacenamiento.
- En navegador: activar service worker, consultar, desconectar, recargar y revisar fecha de caché; categoría no almacenada y recuperación de conexión.
- Instalación PWA en el dispositivo objetivo. El icono inicial SVG y manifiesto deben validarse con ese navegador.
- Pruebas de teclado, contraste, móvil y revisión funcional de Carlos y Santiago.
- Ejecución de GitHub Actions: consultar el resultado real del PR. Un workflow escrito no prueba una ejecución aprobada.
- Guía aprobada según Carlos; registrar las evidencias personales reales y realizar revisión cruzada antes de integrar en develop.
- Cinco fichas ISO, doce casos académicos diseñados y métricas completas del Taller 5.

## Reproducir

Windows: `.\mvnw.cmd verify`; luego `.\mvnw.cmd spring-boot:run`. Linux/macOS: `chmod +x mvnw`, `./mvnw verify`, `./mvnw spring-boot:run`. Primera ejecución necesita Internet para dependencias. El proxy temporal usado en el entorno del agente no forma parte del repositorio.

Se excluyó Mockito porque estas pruebas usan el servidor y servicios reales, sin objetos simulados. La primera ejecución falló por la inicialización del agente de Mockito; tras retirar esa dependencia innecesaria, las 14 pruebas pasan. Una descarga posterior falló por configuración temporal de red; la ejecución completa posterior terminó correctamente. Los resultados anteriores no se presentan como aprobados.

## Referencias técnicas

- Spring Boot 3.5: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Maven Wrapper: https://maven.apache.org/tools/wrapper/

## IA y revisión

Codex de OpenAI generó y comprobó esta base con herramientas automáticas. Carlos y Santiago deben revisar el código, ejecutar las pruebas manuales y registrar su aceptación real. No se ha atribuido esta ejecución al equipo ni al interlocutor.

## Ajuste a la guía aprobada

Prettier 3.9.9 y sus seis opciones aprobadas incorporados; npm ci y format:check reproducibles. Formato de JS, CSS, HTML, Markdown, JSON y manifiesto aplicado. Propiedades operationalStatus y confidenceLevel diferenciadas en servidor y cliente; nombres de funciones en inglés con acción y dominio. Configuración compartida de sangría y plantilla de PR con los seis criterios DoR y ocho DoD. Comprobación local repetida después del ajuste: verify aprobado, 14 ejecuciones sin fallos, Spotless y PMD aprobados. La cobertura de esta pequeña base no acredita cobertura de módulos críticos aún no implementados.

## Interfaz con ui-ux-design-pro

Se aplicaron las 12 referencias del ZIP proporcionado por Carlos. Consulta principal, borrador secundario, fichas con estado/confianza/fecha separados, diseño claro, foco visible, campos con ayuda, enlace para saltar al contenido y estados de carga/vacío/error. Sin descarga de fuentes o dependencias de UI. Cache v2 para distinguir los recursos nuevos. Patrón persistido en .interface-design/system.md.

La aprobación automática no reemplaza pruebas reales de teclado, lector de pantalla, instalación ni desconexión/reconexión. La revisión humana final sigue pendiente; no se declara certificación WCAG.

Contraste de pares principales calculado: texto principal 13.47:1, secundario 7.49:1, metadatos 6.09:1, acción 7.90:1, aviso 6.26:1 y conexión 6.39:1. Referencia: https://www.w3.org/TR/WCAG22/ . Se inspeccionó una representación estática del diseño; no equivale a una prueba en navegador. El navegador de esta sesión bloquea archivos locales.

## Validación manual en Windows — 2026-10-05

Responsable: Carlos Mario Peña.
Entorno: Windows, Google Chrome y servidor local en http://localhost:8080.
Versión de la interfaz: be1fef8, ejecutada desde la descarga ZIP con el ajuste de Spotless a LF.
Corrección de compilación publicada posteriormente: commit 438bb79.

### Resultados

| Prueba                               | Resultado observado                                                                                                                                         | Estado          |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------- |
| Consulta inicial                     | Se muestran cuatro reportes ficticios obtenidos del servidor.                                                                                               | Aprobada        |
| Filtro de reportes                   | Vía afectada muestra una tarjeta; Todas las categorías recupera las cuatro.                                                                                 | Aprobada        |
| Guardar y recuperar borrador         | El texto, la latitud y la longitud se conservan después de recargar.                                                                                        | Aprobada        |
| Descartar borrador                   | Los tres campos quedan vacíos y siguen vacíos después de recargar.                                                                                          | Aprobada        |
| Consulta sin conexión                | Con Offline en Chrome, la página y los cuatro reportes cargan desde caché; aparece la fecha de almacenamiento y la advertencia de posible desactualización. | Aprobada        |
| Recuperación de conexión             | Al restaurar la red y actualizar, aparecen los avisos de conexión disponible y datos obtenidos del servidor.                                                | Aprobada        |
| Adaptación a 360, 768 y 1280 píxeles | Los bloques se reorganizan sin superposiciones visibles en las capturas. En 768 píxeles se recorta el texto del selector.                                   | Con observación |
| Campos obligatorios                  | El formulario bloquea el guardado con campos vacíos.                                                                                                        | Aprobada        |
| Latitud fuera de rango               | El formulario rechaza la latitud 91.                                                                                                                        | Aprobada        |
| Límites de coordenadas               | Acepta latitud 90 y longitud 180; rechaza longitud 181.                                                                                                     | Aprobada        |
| Navegación por teclado               | El foco es visible y permite recorrer los controles con Tab y Shift + Tab y activar botones con Enter.                                                      | Aprobada        |

### Verificación automática de la copia clonada

- Java y compilador: Temurin 17.0.20.1.
- Maven verify: BUILD SUCCESS.
- Pruebas: 14 ejecuciones, cero fallos, cero errores y cero omitidas.
- Spotless y PMD: aprobados.
- GitHub Actions: ejecución número 32 aprobada para el commit 438bb79.

### Evidencias y alcance

Carlos compartió registros de terminal y capturas durante la sesión.
Las capturas respaldan la consulta inicial, la consulta offline, la adaptación
a tres anchos y el resultado de GitHub Actions. Las demás pruebas manuales
se registran según su confirmación durante la sesión. Los adjuntos todavía
no están incorporados al repositorio.

Pendientes: mejorar el ancho del selector a 768 píxeles, comprobar la
instalación PWA, realizar la revisión de Santiago sobre la versión final
e integrar y verificar en develop.

Estas pruebas corresponden al demostrador con datos ficticios. No acreditan
el cumplimiento completo de accesibilidad, PWA ni de todos los requisitos
del proyecto. El mapa, la autenticación, PostgreSQL y la publicación real
al servidor siguen pendientes.

Este registro complementa las notas anteriores que indicaban pruebas
manuales pendientes. No constituye la aprobación de Santiago ni completa
por sí solo la definición de terminado.

## Preparación RA-02 — 6 de octubre de 2026

Codex ejecutó sobre la propuesta: 22 pruebas JUnit/MockMvc, sin fallos; 3 pruebas Node de agrupación, sin fallos; comprobación de sintaxis de app.js/map.js; formato Prettier y Spotless. Maven verify falló inicialmente por resolución de red al obtener el descriptor de sitio; la ejecución posterior con Maven 3.9.11 en modo offline finalizó correctamente, incluido PMD (0 infracciones). JaCoCo: 66/68 líneas Java cubiertas; no equivale a cobertura de interacción visual.

Estos resultados acreditan la propuesta local, no el cierre. CI remoto, revisión de Santiago, tres resoluciones, teclado/PWA, accesibilidad y carga con 1.000 publicaciones/20 usuarios quedan pendientes. No se atribuyen a Carlos o Santiago ejecuciones realizadas por el agente. Contrato y pasos: RA02.md.

## Preparación y comprobación parcial en Windows — 2026-10-06

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. No constituye revisión de Santiago.
Repositorio local actualizado mediante avance directo a `2371a92674a42fc26459f78568b6b52b81560d8e`.
Entorno: Windows, Temurin Java/javac 17.0.20.1, Node 24.19.0, npm 11.17.0 y navegador integrado de Codex.
Se utilizó el servidor ya activo en `http://localhost:8080`; no se reinició ni se acreditó el SHA de sus clases cargadas. Los resultados siguientes son una comprobación preliminar del servidor activo, no una certificación del commit actualizado.

| Acción ejecutada                               | Resultado observado                                                                                                                                                                                     |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Consultar inicio                               | Cuatro reportes, marcadores, leyenda y atribución cartográfica visibles.                                                                                                                                |
| Combinar Vías afectadas, Bloqueada y Reportada | Un reporte y marcador compatibles.                                                                                                                                                                      |
| Abrir detalle de la vía con Enter              | Título, descripción, estado, confianza, coordenadas y fechas visibles; foco en Cerrar detalle.                                                                                                          |
| Cerrar con Escape                              | El foco vuelve al botón de detalle. No acredita el recorrido completo con Tab.                                                                                                                          |
| Limpiar e incluir cerrados                     | Cinco reportes y grupo de dos cerca del refugio.                                                                                                                                                        |
| Abrir grupo                                    | Se muestran ambos refugios; después cambia el área consultada y desaparece el menú antes de seleccionar el cerrado. Observación pendiente de reproducción; sin diagnóstico confirmado.                  |
| Limpiar y volver al área inicial               | Cuatro reportes; filtros restablecidos.                                                                                                                                                                 |
| Aplicar 360×800, 768×1024 y 1366×768           | Ancho del documento 345, 753 y 1351 respectivamente, sin desbordamiento horizontal según DOM. Capturas parciales del panel; revisión visual completa pendiente. Se restableció el tamaño del navegador. |

### Siguiente validación reproducible

Arranque del SHA actualizado comprobado posteriormente con `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=0'`: compilación Java 17 correcta, servidor iniciado en puerto asignado 63852, inicio HTTP 200 y API con cuatro publicaciones. El intento previo en 8081 falló por puerto ocupado; el primer intento sin comillas falló por interpretación del argumento en PowerShell. Esto acredita arranque y consulta HTTP de la versión actual, sin extender las comprobaciones preliminares de UI a ese servidor. Se detuvo únicamente el proceso iniciado por Codex al terminar.

1. Ejecutar `.\mvnw.cmd spring-boot:run` desde la rama actualizada en una terminal libre. Si 8080 está ocupado, detener el servidor anterior desde su terminal y arrancar de nuevo; no confundir la versión cargada con HEAD.
2. Registrar SHA, navegador/versión, red, fecha y responsable; recorrer los casos de RA02.md, incluyendo selección de cada detalle desde grupos y marcadores.
3. Revisar filtros, mapa, lista, diálogo y borrador completos en los tres tamaños, foco visible y recorrido Tab/Shift+Tab.
4. En Chrome o Edge, probar instalación, service worker, recarga, desconexión/reconexión y bloqueo de CDN/cartografía; distinguir consulta exacta guardada y consulta sin caché.
5. Completar auditoría de accesibilidad y carga con 1.000 publicaciones/20 usuarios bajo red documentada. No extrapolar rendimiento del demostrador de seis datos.

No se repitieron las pruebas automáticas del traspaso: este bloque no cambia código funcional. PR #5 abierto y #6 en borrador comprobados públicamente; CI actual no verificable en esta sesión (CLI con HTTP 401 y checks públicos sin resultados). Pendientes acuerdo DoR/estimación y revisión de Santiago.

## Corrección del menú de grupos — 6 de octubre de 2026

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Entorno Windows/Java 17, navegador integrado de Codex, conexión disponible sin perfil de velocidad medido. Base `6c4bf1d` más la corrección de map.js y su prueba/documentación incluida en el commit `8aaec3a`. No representa revisión de Carlos o Santiago.

Se arrancó el repositorio con `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=0'`. En puerto 61055 se reprodujo: incluir cerrados, abrir grupo 2, esperar la consulta por movimiento y seleccionar el refugio cerrado; el menú desaparecía. La referencia Leaflet 1.9.4 confirma autoPan y eliminación individual de capas: https://leafletjs.com/reference.html#popup-autopan y https://leafletjs.com/reference.html#layergroup-removelayer.

Diagnóstico: abrir el popup desplaza la vista y dispara la consulta; render eliminaba todas las capas. Se conservan ahora grupos con los mismos datos y se retiran solo los grupos reemplazados o ausentes. La consulta por área continúa; datos o miembros distintos pueden cerrar un menú ya obsoleto.

La primera recarga tras copiar recursos en el mismo servidor siguió mostrando el fallo; no se tomó como prueba aprobada ni se confirmó la causa de esa recarga. Se reinició la instancia en puerto 53859, origen nuevo sin almacenamiento previo, desde los recursos modificados. En esa instancia se comprobaron:

| Caso                                                            | Resultado observado                                                                                                                                |
| --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Abrir grupo, dejar terminar consulta y elegir cada refugio      | Menú conservado; detalles Disponible y Cerrado correctos.                                                                                          |
| Cerrar detalle con Escape                                       | Foco vuelve al botón del refugio dentro del popup.                                                                                                 |
| Acercar al grupo                                                | Cambia zoom y aparecen marcadores separados; consulta sigue funcionando.                                                                           |
| Restablecer vista y combinar Vías afectadas/Bloqueada/Reportada | Un resultado compatible; detalle de lista abre con Enter.                                                                                          |
| Seleccionar refugio cerrado en 360×800, 768×1024 y 1366×768     | Detalle correcto en cada tamaño; se restableció viewport. Esto acredita el flujo afectado, no una revisión visual completa de todas las secciones. |
| npm.cmd test                                                    | Cinco pruebas aprobadas: tres existentes y dos nuevas de conservación/selección y retirada por datos, zoom o consulta vacía.                       |
| .\mvnw.cmd verify                                               | BUILD SUCCESS: 22 ejecuciones Java, sin fallos/errores/omitidos; Spotless y PMD aprobados.                                                         |
| npm.cmd run format:check y formato de nueva prueba CJS          | Aprobados.                                                                                                                                         |

Los dobles DOM/Leaflet prueban el ciclo de capas; el navegador comprueba el autoPan y selección reales. Capturas revisadas por el compañero, recorrido completo de teclado, instalación/offline/PWA, accesibilidad y carga 1.000/20 siguen pendientes. No se declara DoD ni CI remoto aprobado para esta corrección. Las instancias temporales se detuvieron; servidor previo 8080 preservado.
