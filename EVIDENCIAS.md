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

## Teclado y recuperación local — 6 de octubre de 2026

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Versión `484e1da`, Windows/Java 17 y navegador integrado de Codex. Instancia propia en `http://localhost:51928`, origen nuevo sin borradores del usuario; conexión disponible sin velocidad medida. CI `verify` de `484e1da` comprobado aprobado mediante API pública de GitHub antes de esta ejecución.

| Caso y pasos                                                                      | Resultado observado                                                                                                                                                                                                                                                                                                                                                                                                |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Tab desde enlace inicial y categoría, por controles hasta el final del formulario | Orden observado: marca, navegación, categoría, estado/confianza/cerrados, limpieza/actualización, restablecer/mapa, cuatro marcadores, zoom/atribución, cuatro detalles, texto/coordenadas y guardar/descartar. Controles del recorrido con outline solid de 3 px. Al salir del documento el foco pasó al cuerpo y terminó el seguimiento; no se considera fallo funcional ni auditoría completa de accesibilidad. |
| Shift+Tab desde Latitud; Enter para detalle de vía; Escape para cerrar            | Retrocede al texto del borrador; abre detalle y devuelve foco al botón de la vía.                                                                                                                                                                                                                                                                                                                                  |
| Guardar texto sintético y coordenadas 4.15/-73.63; recargar                       | Tres valores recuperados; aviso de borrador local no publicado.                                                                                                                                                                                                                                                                                                                                                    |
| Detener únicamente servidor propio y recargar misma URL                           | Página utilizable; cuatro reportes desde consulta guardada con fecha y advertencia de posible desactualización; borrador recuperado.                                                                                                                                                                                                                                                                               |
| Sin servidor, seleccionar Vías afectadas nunca consultada en este origen          | Error de consulta sin respaldo guardado; no se anuncia éxito.                                                                                                                                                                                                                                                                                                                                                      |
| Reiniciar servidor en el mismo puerto y consultar Vías afectadas                  | Un reporte obtenido del servidor; desaparece el error.                                                                                                                                                                                                                                                                                                                                                             |
| Descartar borrador sintético y recargar                                           | Texto, latitud y longitud vacíos.                                                                                                                                                                                                                                                                                                                                                                                  |

La caída del servidor verifica el respaldo del service worker, pero no simula desconexión total del navegador: Internet/CDN/cartografía permanecieron disponibles y el aviso de conexión de red siguió activo. Instalación PWA, navegador offline/reconexión real, bloqueo CDN/cartografía, captura/revisión visual del foco, lector de pantalla y validación humana siguen pendientes. Se preservó el servidor ajeno de 8080 y se detuvo la instancia propia al cerrar. Solo se modifica documentación; no se repiten suites de código ya aprobadas.

## Validación de cierre RA-02 desde develop — 6 de octubre de 2026

Comprobación ejecutada mediante herramientas automatizadas; validación humana pendiente. Ejecución mediante Codex/herramientas, sin atribuir estas pruebas a Carlos ni Santiago. Esta sección reemplaza los pendientes antiguos solo en los casos que acredita; no declara DoD completo ni modifica Trello.

### Versión, entorno y comandos

Base `e0fdb1ae7a001dc1cf640c0fa9134ba22bb87c6e`, CI `verify` aprobado comprobado mediante API pública. PR #6 integrado por `ad7755a8fc9c8c5e9b408649e8b82442c7fc7a52` y #5 integrado por `e0fdb1a`, comprobados mediante `/repos/Carlos1120/redauxilio/pulls/{5,6}`. Rama nueva `test/RA-02-cierre-validacion`; no se fusionó la rama antigua. Únicamente se reaplicó el diff de RA02.md de `0594565`. Código/pruebas finales: `9bbdbd9`; el commit documental posterior no modifica el código ejecutado.

Windows, Temurin Java 17.0.20.1, Maven Wrapper 3.9.11, Node 24.19.0/npm 11.17.0; Intel Core i9-10900K, 20 procesadores lógicos y aproximadamente 32 GiB RAM. Navegador integrado Chromium 154.0.0.0; Lighthouse 13.5.0 con Google Chrome headless 154.0.0.0. HTTP de aplicación por loopback, sin limitación artificial de red/CPU. CDN/OSM por conexión disponible, sin medición de ancho de banda o latencia externa. No se extrapola a redes móviles/lentas; caché HTTP de bibliotecas externas ya calentada en las pruebas de tiempo visible. Otros procesos del equipo y Lighthouse concurrente no se aislaron durante la última carga; no se capturó utilización de CPU/RAM.

| Comando                                                            | Resultado                                                                                                           |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| `npm.cmd ci`                                                       | Instalación reproducible; 0 vulnerabilidades informadas para las dependencias del proyecto.                         |
| `npm.cmd run format:check`                                         | Aprobado.                                                                                                           |
| `npm.cmd test`                                                     | Seis pruebas JS aprobadas tras corregir el defecto; cinco en la base inicial.                                       |
| `.\mvnw.cmd -B verify`                                             | En `9bbdbd9`: 23 ejecuciones Java, 0 fallos/errores/omitidas; Spotless, PMD y BUILD SUCCESS. Incluye la carga real. |
| `npm.cmd exec -- prettier --check src/test/js/map-render.test.cjs` | Comprobación adicional de la prueba CJS, fuera del glob de format:check.                                            |

Informes regenerables: `target/surefire-reports/`, `target/site/jacoco/`, `target/ra02-performance.json` y los JSON de Lighthouse descritos abajo. No se versionan binarios, logs completos ni archivos generados.

Instancia normal: `.\mvnw.cmd -B spring-boot:run '-Dspring-boot.run.arguments=--server.port=0'`, puerto 59607. Se detuvo únicamente PID propio 8624 para probar caída; se restauró en el mismo origen con `--server.port=59607`, PID propio 2300. Fixture final en puerto 60756/PID 37764. Instancias temporales anteriores del fixture (12168, 37356, 33184) detenidas al cambiar pruebas. Servidores ajenos preservados; las instancias propias se detienen al finalizar.

### Regresión funcional y responsive

| Pasos reproducibles                                                                                                           | Resultado observado                                                                                                                                                                                                                          | Estado                                                    |
| ----------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| Abrir sin iniciar sesión; seleccionar cada una de las cuatro categorías y actualizar                                          | Cuatro activos iniciales; un reporte por categoría; mapa y lista correspondientes.                                                                                                                                                           | Aprobado                                                  |
| Combinar ROAD, Bloqueada y REPORTADA                                                                                          | Un reporte de vía compatible; mismo contenido al abrir detalle.                                                                                                                                                                              | Aprobado                                                  |
| Seleccionar VERIFICADA y actualizar; después limpiar                                                                          | Estado vacío explícito; limpiar devuelve los cuatro activos. No fabrica confianza adicional.                                                                                                                                                 | Aprobado                                                  |
| Incluir cerrados y actualizar                                                                                                 | Cinco visibles y grupo 2; oculto ausente. Sin selección de cerrados, cuatro activos.                                                                                                                                                         | Aprobado                                                  |
| Abrir grupo 2, dejar consulta por autoPan completar, elegir ambos refugios; acercar y volver al área inicial                  | Menú conservado; detalle Disponible/Cerrado; zoom separa marcadores y conserva sincronización por área.                                                                                                                                      | Aprobado                                                  |
| Detalle de lista con Enter y cierre Escape; detalle de grupo y Escape                                                         | Título, descripción, estado/confianza, coordenadas y fechas coherentes; foco regresa al control que abrió el diálogo.                                                                                                                        | Aprobado                                                  |
| HTTP real `/api/publications`, `?includeClosed=true`, filtros combinados, `?confidenceLevel=VERIFICADA`                       | 200 y tamaños 4, 5, 1 y 0 respectivamente.                                                                                                                                                                                                   | Aprobado                                                  |
| HTTP real `?category=OTHER`, área parcial `?south=4`, detalles `/6` y `/999`                                                  | 400, 400, 404 y 404; complementado por los 22 casos Java existentes.                                                                                                                                                                         | Aprobado                                                  |
| Inspección visual completa de cabecera, aviso, filtros, mapa, leyenda, fichas, borrador y pie en 360×800, 768×1024 y 1366×768 | Navegación y controles utilizables; diálogo abierto/cerrado en cada tamaño; sin overflow horizontal. Anchos DOM/client: 345/345, 753/753 y 1351/1351 (barra vertical ocupa 15 px). Una columna de fichas en móvil/tablet, dos en escritorio. | Aprobado mediante herramientas; revisión humana pendiente |

La revisión funcional/responsive inicial corresponde al código integrado sin diferencias en esos flujos. La única corrección de producción posterior afecta al aviso de teselas; fue reproducida y comprobada de nuevo en el fixture final. No se repiten comprobaciones del mismo CSS ni se afirma certificación por observar el ancho DOM; también se inspeccionaron las capturas completas.

### RNF-10

La aplicación tiene una página principal con secciones de consulta y borrador. Lighthouse 13.5.0 la auditó con configuración móvil y escritorio: **100/100 en ambas** (umbral ≥90), sin auditorías de accesibilidad con score 0. No equivale a certificación WCAG ni revisión de lector de pantalla. Informes: `target/ra02-accessibility-mobile.json` y `target/ra02-accessibility-desktop.json`.

Reproducir con Chrome instalado y una instancia identificable (reemplazar PUERTO):

```powershell
$env:CHROME_PATH='C:\Program Files\Google\Chrome\Application\chrome.exe'
npm.cmd exec --yes --package=lighthouse@13.5.0 -- lighthouse http://localhost:PUERTO/ --only-categories=accessibility --output=json --output-path=target/ra02-accessibility-mobile.json --chrome-flags=--headless --quiet
npm.cmd exec --yes --package=lighthouse@13.5.0 -- lighthouse http://localhost:PUERTO/ --only-categories=accessibility --preset=desktop --output=json --output-path=target/ra02-accessibility-desktop.json --chrome-flags=--headless --quiet
```

Lighthouse se ejecuta desde el registro npm en ámbito temporal; no cambia package.json/lockfile ni añade dependencias productivas. Navegación real Tab por filtros, checkbox, acciones, mapa, marcadores y zoom: foco con outline sólido de 3 px. Shift+Tab desde Latitud vuelve al texto del borrador. Enter/Escape y retorno de foco comprobados en detalle. Etiquetas y nombres accesibles presentes; estados/confianza, conexión, caché/error y categorías incluyen texto/símbolos, además de color. Falta revisión humana final, no la auditoría automática pedida.

### RNF-01

`Ra02ValidationApplication.Fixture` existe solo en `src/test/java`. Inyecta lista inmutable de **1.000 registros sintéticos**, cuatro categorías: 900 activos visibles, 50 cerrados visibles y 50 ocultos. No altera el constructor productivo de seis registros, no introduce persistencia y no se empaqueta en el JAR productivo.

`Ra02PerformanceTests` usa HTTP real con Tomcat en puerto aleatorio: 20 trabajadores/usuarios virtuales simultáneos en circuito cerrado (una petición pendiente por usuario), 3 s de calentamiento y 10 s medidos, mezcla uniforme de consulta por área, filtros combinados y detalle. Timeout 5 s. Cada respuesta debe ser 200 y tener los IDs/campos/recuentos esperados; un error hace fallar la prueba. Percentil por rango más próximo; no es promedio. No realiza carga contra OSM/CDN.

Medición de servidor: filtro de pruebas cronometra cadena HTTP/DispatcherServlet y serialización JSON antes de copiar el cuerpo a la conexión; publica `Server-Timing: ra02;dur=...`. Incluye sobrecarga del buffer de pruebas, excluye cola previa al filtro, transferencia de red y renderizado. **No es duración HTTP del cliente ni latencia total de infraestructura.** La métrica cliente se registra por separado.

Última ejecución sobre `9bbdbd9`: 13.725 muestras, cero errores, aproximadamente 1.372,5 peticiones/s en esta ventana corta. No es una prueba de resistencia ni garantía de capacidad productiva.

| Operación         | Muestras | p50 interno ms | p95 interno ms | p99 interno ms | p95 cliente ms |
| ----------------- | -------: | -------------: | -------------: | -------------: | -------------: |
| Consulta por área |    4.577 |          8,624 |         19,802 |         25,602 |        24,0601 |
| Filtros           |    4.572 |          1,865 |          4,299 |          7,509 |         8,7102 |
| Detalle           |    4.576 |          0,078 |          0,252 |          0,543 |         4,3105 |

**Umbral interno ≤2 s aprobado para las tres operaciones.** Las ejecuciones preliminares sirvieron para preparar/cambiar el fixture; se informa la última, sin escoger la más rápida. Informe completo regenerable con `.\mvnw.cmd -B test '-Dtest=Ra02PerformanceTests'` o `verify`.

Tiempo visible: en el fixture, un MutationObserver insertado exclusivamente por el filtro de pruebas espera lista no vacía y marcadores, y registra `performance.now()` tras dos frames de renderizado en `#report-map[data-first-results-millis]`. Inicio de navegación es el origen de tiempo; no confunde respuesta HTTP con resultado visible. Chromium integrado 154, viewport 1366×768, loopback sin throttling y bibliotecas externas calentadas: **366,9 ms** en código final, 900 fichas/15 grupos. Umbral ≤3 s aprobado para este entorno; antes de la corrección se observaron 750,4 ms. No mide mapa base completamente cargado ni arranque frío de CDN bajo red lenta.

Para reproducir navegador/primer resultado y fallos de proveedores, arrancar:

```powershell
.\mvnw.cmd -B spring-boot:test-run '-Dspring-boot.run.main-class=co.redauxilio.publications.Ra02ValidationApplication' '-Dspring-boot.run.arguments=--server.port=0'
```

Abrir el puerto real a 1366×768 y leer el atributo DOM indicado después de ver mapa y lista. Las URL `/?validation=cdn-failure` y `/?validation=tile-failure` aplican CSP solo en el fixture: la primera bloquea scripts/estilos externos y la segunda imágenes externas. No debilitan seguridad ni cambian el proveedor productivo.

### PWA, conectividad y defecto corregido

| Pasos                                                                | Observado                                                                                                                                                                                        | Estado                                  |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------- |
| Guardar borrador ficticio y coordenadas 4.15/-73.63; recargar        | Texto y coordenadas recuperados; no anuncia publicación.                                                                                                                                         | Aprobado                                |
| Detener servidor propio 59607, mantener navegador/red; recargar      | Shell/borrador utilizables y cuatro reportes con fecha de consulta guardada y advertencia de desactualización.                                                                                   | Aprobado para caída del servidor        |
| Sin servidor, seleccionar estado Lleno no consultado y actualizar    | Error claro de consulta sin respaldo; estado vacío; no anuncia éxito.                                                                                                                            | Aprobado                                |
| Restaurar servidor en mismo origen y actualizar Lleno, luego limpiar | Cero resultados legítimos desde servidor; limpieza restaura cuatro reportes.                                                                                                                     | Aprobado para restauración del servidor |
| Descartar borrador de prueba y recargar                              | Tres campos vacíos; datos del usuario ajenos a este origen no modificados.                                                                                                                       | Aprobado                                |
| CSP `cdn-failure` en fixture                                         | Leaflet rechazado, aviso de mapa no cargado, 900 fichas y detalle utilizables.                                                                                                                   | Aprobado                                |
| CSP `tile-failure` en fixture                                        | 15 teselas fallidas (complete=true/naturalWidth=0), 15 grupos y 900 fichas; detalle utilizable.                                                                                                  | Aprobado tras corrección                |
| Navegar a fixture sin CSP de fallo                                   | Fondo y aviso normal recuperados; consulta utilizable.                                                                                                                                           | Aprobado                                |
| Instalar y abrir como PWA                                            | Navegador integrado sin interfaz/capacidad de instalación expuesta; no se simuló appinstalled.                                                                                                   | Pendiente por capacidad de herramienta  |
| Desconexión total y reconexión del navegador                         | Las capacidades admitidas son viewport/visibilidad y lectura/interacción DOM; no hay offline/contexto/red o DevTools accesible. No se desconectó Windows ni se sustituyó por caída del servidor. | Pendiente por capacidad de herramienta  |

Defecto real: el evento Leaflet `load` se emite al terminar las teselas incluso tras `tileerror`, y el manejador sobrescribía el aviso de cartografía fallida. `map.js` conserva ahora el error del lote y solo recupera el aviso normal en una carga sin errores. Prueba añadida: `fin de carga conserva fallo cartográfico y un lote exitoso posterior recupera el aviso`; regresión real comprobada con CSP y navegación posterior. Código documentado en español. No se promete mapa offline completo ni se añade funcionalidad posterior.

### Pendientes que bloquean el cierre

**RA-02 NO está preparada para cierre/revisión final de Santiago mientras falten instalación PWA y desconexión total/reconexión reproducibles.** En Chrome/Edge con perfil de prueba: consultar/guardar, poner únicamente ese navegador offline mediante sus herramientas, recargar, verificar shell/borrador/fecha de caché; consultar URL nueva y exigir fallo; restaurar red y exigir respuesta normal. Instalar desde la interfaz real cuando sea elegible y abrir la app instalada. Registrar versión, pasos y observado; una validación de manifiesto o un evento simulado no acredita instalación. Después Santiago registra su revisión humana final sobre los cambios y evidencias. PR #6 ya integrado no aprueba automáticamente esta corrección posterior.

Fuentes decisivas: [Lighthouse: alcance y puntuación](https://developer.chrome.com/docs/lighthouse/accessibility/scoring), [Playwright: comportamiento visible y aislamiento](https://playwright.dev/docs/best-practices), [fuente Leaflet 1.9.4: GridLayer y fin de carga](https://github.com/Leaflet/Leaflet/blob/v1.9.4/src/layer/tile/GridLayer.js). La referencia genérica de Leaflet consultada hoy corresponde a 2.0 alpha; para el diagnóstico se utilizó la fuente de la versión 1.9.4 fijada por el proyecto.
