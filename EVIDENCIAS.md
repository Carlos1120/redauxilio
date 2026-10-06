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
