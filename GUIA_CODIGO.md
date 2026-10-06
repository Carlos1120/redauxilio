# Cómo leer el código de RedAuxilio

Esta guía explica la implementación actual de RA-00 y la propuesta RA-02. El código y las pruebas son la referencia: todavía se utilizan datos ficticios en memoria, sin base de datos del servidor ni autenticación. Los comentarios explicativos están en español; los identificadores siguen en inglés según ESTANDARES.md.

## Orden para empezar

| Archivo                                              | Función y qué conviene leer                                                                                                                                                                                                                       |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pom.xml`                                            | Maven prepara el servidor Java. Web aporta HTTP; Thymeleaf renderiza la plantilla; Test aporta herramientas de prueba. Spotless revisa formato, JaCoCo mide cobertura Java y PMD busca infracciones. Las versiones exactas están en este archivo. |
| `RedAuxilioApplication.java`                         | `main` inicia Spring Boot. Spring construye y conecta los objetos que tienen anotaciones como `@Controller` y `@Service`.                                                                                                                         |
| `publications/PublicationController.java`            | Entrada de las peticiones. `/` devuelve la plantilla `index`; `/api/publications` devuelve la consulta JSON; `/api/publications/{id}` devuelve un detalle visible.                                                                                |
| `publications/PublicationService.java`               | Valida filtros y aplica las reglas de visibilidad, cierre y área. Mantiene seis ejemplos: cuatro activos, un cerrado y un oculto.                                                                                                                 |
| `templates/index.html`                               | Define estructura, etiquetas, controles y diálogo. Los `id` conectan cada elemento con `getElement` en JavaScript. Carga Leaflet, `map.js` y `app.js` mediante scripts `defer`, en orden.                                                         |
| `static/app.js`                                      | Coordina eventos, consulta HTTP, lista, diálogo y borrador local. Empieza por `loadPublications`, `renderPublications` y `executeDraftOperation`.                                                                                                 |
| `static/map.js`                                      | Encapsula Leaflet: dibujo, agrupación, área visible y restablecimiento. Notifica acciones a `app.js`; no consulta la API.                                                                                                                         |
| `static/sw.js`                                       | Intercepta recursos propios permitidos y consultas públicas GET. Intenta red primero y usa caché ante fallo de red.                                                                                                                               |
| `static/app.css`                                     | Define colores/espaciado compartidos, presentación de componentes y adaptación por ancho. Las reglas finales sin capa ajustan el mapa y tienen prioridad sobre las capas declaradas.                                                              |
| `static/manifest.webmanifest` y `icon.svg`           | Describen nombre, inicio, presentación e icono de la PWA; no implementan su funcionamiento sin conexión.                                                                                                                                          |
| `PublicationTests.java` y `src/test/js/map.test.cjs` | Comprueban el contrato HTTP y la agrupación. No sustituyen pruebas en navegador, de accesibilidad o de carga.                                                                                                                                     |
| `package.json` y `.github/workflows/verify.yml`      | Definen comandos de formato/pruebas JavaScript y su ejecución junto a Maven en GitHub. Node se usa para estas herramientas; el servidor sigue siendo Java.                                                                                        |

Las rutas Java de la tabla se encuentran bajo `src/main/java/co/redauxilio/`; la prueba Java está bajo `src/test/java/co/redauxilio/publications/`. HTML, CSS y JavaScript de producción están bajo `src/main/resources/`.

## Ejemplo: filtrar una vía bloqueada

1. El usuario cambia categoría a «Vía afectada» y estado a «Bloqueada». Los eventos `change` llaman a `loadPublications()`.
2. Esa función toma los valores del formulario y, si existe mapa, agrega su rectángulo visible: `south`, `west`, `north`, `east`. `URLSearchParams` codifica los parámetros para la URL.
3. `fetch` realiza una petición GET a `/api/publications?...`. El navegador no envía un reporte nuevo: está pidiendo información.
4. Spring dirige la petición a `PublicationController.list`. `@RequestParam` transforma parámetros HTTP en argumentos Java. Si llega un límite geográfico, el controlador exige los cuatro.
5. `PublicationService.findPublications` valida los valores y filtra la lista. Cada `.filter` agrega una condición: el reporte debe ser visible, cumplir la regla de cierre y coincidir con TODOS los filtros activos. Un valor vacío significa «sin restricción».
6. `@ResponseBody` permite a Spring serializar la lista como JSON, un formato de datos que JavaScript puede leer con `response.json()`.
7. Si esa respuesta corresponde a la consulta más reciente, `renderPublications` entrega la misma lista al mapa y construye las tarjetas. El contador de consultas evita que una petición lenta anterior sobrescriba resultados más nuevos.
8. El diálogo utiliza el mismo objeto recibido desde un marcador o tarjeta. No vuelve a consultar el endpoint de detalle, que existe y se comprueba por separado. Para refrescar datos se ejecuta otra consulta.

Un filtro inválido produce HTTP 400. Consultar un detalle inexistente u oculto produce HTTP 404. Un filtro válido sin coincidencias devuelve HTTP 200 con `[]`; esos resultados significan cosas distintas.

## Las decisiones importantes

**Separación de responsabilidades.** El controlador entiende HTTP; el servicio aplica reglas; `app.js` coordina la pantalla; `map.js` dibuja. Así una corrección de visibilidad se hace en el servidor y no se duplica entre tarjetas y mapa. Un reporte oculto se excluye antes de serializarlo: esconderlo únicamente en la pantalla dejaría sus datos en la respuesta de red.

**Datos en memoria.** `Publication` es un `record`, un contenedor de datos con campos finales. `Entry` agrega la marca interna `visible`, que no se entrega en el JSON público. La copia inmutable evita cambios accidentales en la lista. Todo se reconstruye al reiniciar: no sirve aún para conservar publicaciones reales.

**Agrupación del mapa.** Leaflet proyecta latitud/longitud a píxeles para el zoom actual. `groupMapPoints` coloca puntos en celdas de 64 píxeles. Un número representa varios reportes; acercar vuelve a calcularlos. No mide distancia en metros y puntos muy próximos a lados distintos de una celda pueden aparecer separados. La ubicación del grupo es la media de sus coordenadas, no un nuevo lugar reportado.

**Consultas al mover el mapa.** El evento `moveend` avisa a `app.js`, que espera 250 ms después del último aviso antes de consultar. Esa espera se llama debounce y reduce peticiones repetidas. Cambiar el zoom recalcula también los grupos. Limpiar filtros conserva el área; «Volver al área inicial» restablece la vista.

**Contenido como texto.** Las tarjetas y los resúmenes se crean con nodos DOM y `textContent`. Un título recibido no se interpreta como etiquetas HTML ni código. El HTML de los iconos solo usa símbolos definidos por la aplicación o el número de reportes.

**Foco del diálogo.** Se recuerda el botón que abrió el detalle para devolverle el foco al cerrar. Si una consulta reemplazó ese botón, se usa la sección como destino. Esto ayuda a continuar la navegación con teclado.

## Tres almacenamientos distintos

| Lugar                            | Qué conserva                                                                            | Qué no garantiza                                                                                           |
| -------------------------------- | --------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Memoria Java del servidor        | Ejemplos públicos y marcas internas mientras el proceso está activo                     | Persistencia después de reiniciar ni escritura de usuarios                                                 |
| IndexedDB del navegador          | Un borrador con texto y coordenadas, clave `current`                                    | Publicación al servidor, sincronización con otro dispositivo ni recuperación si se borra el almacenamiento |
| Cache Storage del service worker | Recursos propios y respuestas de consultas GET exitosas, identificadas por URL completa | Datos actuales, otras combinaciones de filtros/área ni disponibilidad de cartografía/CDN sin conexión      |

`executeDraftOperation` utiliza transacciones: conjuntos de operaciones que terminan confirmándose o abortándose. Solo muestra éxito después de `transaction.oncomplete`; el éxito de una petición individual todavía no garantiza la confirmación final.

El service worker intenta red primero. Si obtiene HTTP 400 o 500, devuelve ese error: no lo oculta con una respuesta antigua. Si la petición falla por red, busca la misma URL en caché. La copia guardada incluye `X-RedAuxilio-Saved-At` para mostrar su antigüedad; la respuesta de red no lleva esa marca añadida. Si no hay copia, devuelve HTTP 503.

La versión de caché permite retirar recursos antiguos durante `activate`; una nueva versión puede borrar también consultas guardadas de la versión anterior. No es un archivo histórico durable. La primera instalación necesita obtener los recursos y no toma necesariamente control de inmediato sobre todas las pestañas ya abiertas.

## Cómo comprobar lo que entendiste

1. Abre Chrome → herramientas de desarrollador → Network. Cambia filtros y observa la URL de `/api/publications` y el JSON devuelto. Con cuatro activos dentro del área, limpiar filtros recupera esos cuatro.
2. Marca «Incluir casos cerrados»: en el área inicial, debe añadirse el quinto visible. El oculto con id 6 nunca debe aparecer; `/api/publications/6` debe responder 404.
3. Mueve el mapa fuera de los ejemplos y vuelve al área inicial: compara parámetros geográficos y resultados.
4. Guarda un borrador, recarga y comprueba que reaparece. Descártalo, recarga y comprueba que sigue vacío. No debe haber petición de publicación al servidor.
5. Después de que el service worker controle la página y hayas consultado con conexión, activa Offline. Repite la misma consulta: si sigue guardada, debe indicar su fecha. Otros filtros o área pueden carecer de copia; el mapa externo no tiene garantía offline.
6. Ejecuta `npm.cmd test`, `npm.cmd run format:check` y `.\mvnw.cmd verify` en Windows. Los tests verifican reglas concretas; el resultado aprobado no acredita por sí solo el uso visual ni los objetivos de rendimiento.

## Alternativas y límites

La lista en memoria facilita demostrar consultas con datos controlados; un repositorio persistente y migraciones serán necesarios para datos reales. La agrupación por celdas es sencilla de probar; una librería especializada tendría sentido con más puntos o requisitos de agrupación más precisos. Mostrar el detalle del objeto ya cargado evita otra petición; consultar el endpoint al abrirlo sería útil si se necesita comprobar información más reciente. Estas son alternativas futuras, no cambios incluidos en esta documentación.

Para aprender con este código, céntrate primero en petición/respuesta HTTP, controlador frente a servicio, promesas y `async/await`, eventos DOM, transacciones y diferencia entre datos actuales y caché. Al modificar una regla, actualiza sus comentarios, esta guía y las pruebas que demuestran su comportamiento. Los comentarios deben explicar propósito, contrato y motivo; no repetir cada instrucción obvia ni declarar funciones que aún no existen.
