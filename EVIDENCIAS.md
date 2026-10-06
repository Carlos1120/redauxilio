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
