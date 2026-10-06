# Estándares de trabajo de RedAuxilio

> Fuente normativa: ESTANDARES_RedAuxilio.docx, versión 1.0. Guía aprobada según confirmación de Carlos en esta conversación. Esta transcripción conserva sus reglas; las evidencias individuales de aceptación y revisión deben registrarse con sus autores reales.

Estándares de trabajo de RedAuxilio

Asignatura: Gerencia de Software
Taller: 4 — Documento de estándares del equipo
Equipo: 4
Integrantes: Carlos Mario Peña y Santiago Ortiz Ochoa
Interlocutor principal: Javier Charry
Fecha: 30 de septiembre de 2026
Versión: 1.0 — Propuesta para aceptación y publicación

Propósito y aplicación

Estos acuerdos definen cómo construimos, revisamos e integramos RedAuxilio, una PWA colaborativa de información georreferenciada ante terremotos. Se aplican al código, pruebas, configuración y documentación del proyecto durante los dos sprints y la entrega final. Regulan el proceso de trabajo; los atributos de calidad del producto se verifican mediante los requisitos no funcionales y el plan de calidad.

La base es el acta de constitución corregida del 18 de septiembre de 2026. Se conserva su alcance: mapa y consulta pública; acceso y permisos; publicaciones y avistamientos; confianza, vigencia y duplicados; moderación y auditoría; instalación PWA y recuperación local. Un estándar no autoriza ampliar ni reducir ese alcance.

La ficha técnica define Java con Spring Boot, Spring Data JPA y Spring Security; PostgreSQL; Thymeleaf, Bootstrap y JavaScript; Leaflet; manifiesto, service worker e IndexedDB. Se mantiene un monolito modular.

Estado de verificación: los documentos suministrados no contienen el repositorio ni su configuración de formato. Google Java Format y Prettier se proponen aquí como herramientas del equipo; su instalación y ejecución deben quedar demostradas en el repositorio antes de presentar este documento como adoptado. No se afirma que ya estén configuradas, que existan pruebas aprobadas ni que los integrantes hayan firmado su aceptación.

1. Guía de estilo y nombres

1.1 Guías, idioma y organización

Java: adoptamos Google Java Style y el formateador Google Java Format, integrado con Spotless en el proyecto Maven. Se usa su estilo Google, con dos espacios de indentación, sin tabulaciones y llaves de apertura en la misma línea.

JavaScript, CSS, JSON, Markdown y plantillas HTML/Thymeleaf: adoptamos Prettier. Configuración compartida: dos espacios, sin tabulaciones, punto y coma en JavaScript, comillas dobles y ancho objetivo de 100 caracteres. El formato de una plantilla debe conservar los atributos th:* y superar una comprobación de renderizado.

Idioma: identificadores de código en inglés; comentarios explicativos, documentación, mensajes de commit y textos de interfaz en español. No mezclamos idiomas dentro de un identificador: usamos createPublication, no crearPublication.

Estructura: paquetes por módulo: identity, publications, geography, trust, moderation, evidence y audit. Dentro de cada módulo se separan controladores, servicios, persistencia y DTO según corresponda. Los controladores reciben peticiones; las reglas de negocio se implementan en servicios.

Convenciones: clases en UpperCamelCase; métodos y variables en lowerCamelCase; constantes en UPPER_SNAKE_CASE; paquetes en minúsculas. No introducimos dependencias entre módulos mediante acceso directo a detalles internos que no sean su interfaz acordada.

1.2 Tres reglas propias de nombres

| Regla                                                               | Aplicación en RedAuxilio                                                                                            | Evitamos                                                                    |
| ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Los booleanos expresan una condición con is, has o can.             | isAccountBlocked, hasOfficialVerification, canEditPublication.                                                      | flag, state, ok.                                                            |
| Los métodos empiezan con un verbo y nombran la acción de dominio.   | createPublication, confirmPublication, recoverDraft.                                                                | process, data, publication() sin acción definida.                           |
| Las magnitudes declaran unidad y los estados declaran su dimensión. | publicationValidityHours, imageSizeBytes, responseTimeMillis; operationalStatus, confidenceLevel, visibilityStatus. | time, size o un solo status para estado operativo, confianza y visibilidad. |

Las colecciones expresan su contenido en plural, por ejemplo activeConfirmations. Los valores de negocio tienen constantes identificables, como REQUIRED_COMMUNITY_CONFIRMATIONS, en lugar de números sin contexto.

1.3 Configuración y comprobación del formateador

Santiago registra Spotless y Google Java Format en pom.xml; Carlos registra Prettier en package.json, su archivo de bloqueo y .prettierrc.json. Ambos configuran su editor para formatear al guardar. Las versiones elegidas deben fijarse en esos archivos y ser compatibles con el JDK y Node utilizados; no se depende de una instalación global diferente en cada computador.

Contenido acordado de .prettierrc.json:

{
"tabWidth": 2,
"useTabs": false,
"semi": true,
"singleQuote": false,
"printWidth": 100,
"endOfLine": "lf"
}

Los scripts format y format:check de package.json deben ejecutar Prettier sobre las plantillas y recursos propios de src/main/resources, los archivos Markdown, el manifiesto y los archivos de configuración compatibles. Se excluyen dependencias, archivos generados, recursos de terceros y directorios de compilación.

Comandos acordados, una vez configuradas las herramientas:

./mvnw spotless:apply
./mvnw spotless:check
npm ci
npm run format
npm run format:check
./mvnw verify

En Windows se usa mvnw.cmd en lugar de ./mvnw. El Maven Wrapper debe estar versionado. El README documenta JDK, Node, preparación de la base de pruebas y comandos. La evidencia de activación es el commit de configuración y el resultado de las comprobaciones, no solo el nombre del formateador en este documento.

2. Convención de commits y ramas

2.1 Formato de mensajes

Usamos Conventional Commits:

tipo(alcance): descripción en infinitivo imperativo [RA-XX]

El alcance nombra el módulo afectado. La descripción explica un cambio concreto: agregar, corregir, documentar, extraer o validar; no se aceptan mensajes como “cambios”, “avance” o “ahora sí”. La cabecera tendrá como máximo 100 caracteres; los detalles y motivos adicionales van en el cuerpo.

| Tipo permitido | Uso                                                          |
| -------------- | ------------------------------------------------------------ |
| feat           | Funcionalidad nueva.                                         |
| fix            | Corrección de comportamiento incorrecto.                     |
| docs           | Documentación sin cambios de lógica.                         |
| refactor       | Reorganización interna sin cambiar el comportamiento.        |
| test           | Creación o corrección de pruebas.                            |
| style          | Formato sin cambios de lógica.                               |
| chore          | Herramientas, dependencias o mantenimiento de configuración. |

Ejemplos de formato, no evidencia de commits realizados:

feat(trust): agregar confirmación comunitaria por usuario [RA-05]
fix(publications): impedir edición por un usuario distinto del autor [RA-04]
test(pwa): validar recuperación del borrador tras recarga [RA-15]
docs(standards): definir estándares de trabajo del equipo 4

Cada commit contiene un cambio coherente. Los cambios funcionales incluyen el identificador real de la historia; una tarea transversal como este documento enlaza su tarjeta de gestión en el PR, sin inventar un código RA. No se incluyen contraseñas, tokens, datos personales reales ni archivos generados. Cada integrante registra sus aportes con su propia identidad; no se alteran fechas para simular historial.

2.2 Ramas e integración

| Rama                                   | Regla                                                                                         |
| -------------------------------------- | --------------------------------------------------------------------------------------------- |
| main                                   | Versiones estables demostrables. Recibe PR desde develop; no se trabaja directamente en ella. |
| develop                                | Integra historias revisadas mediante PR y con comprobaciones aprobadas.                       |
| feat/RA-XX-nombre-corto                | Una historia, por ejemplo feat/RA-05-community-confirmation.                                  |
| fix/RA-XX-nombre-corto                 | Corrección vinculada a una historia.                                                          |
| docs/nombre-corto o chore/nombre-corto | Documentación o configuración transversal.                                                    |

Las ramas de trabajo nacen de develop, se actualizan antes de integrar y se eliminan después de fusionarse. Los PR hacia develop se integran con squash y un mensaje conforme a esta convención; la promoción hacia main conserva la historia con un commit de integración. Se exige una aprobación del compañero y comprobaciones aprobadas; cualquier cambio posterior a la aprobación requiere nueva revisión. Se configuran protecciones en GitHub cuando estén disponibles; si no lo están, la misma regla se cumple manualmente y se documenta en el PR.

3. Definition of Ready DoR

Una historia puede pasar a En progreso solo si cumple las seis condiciones. Ambos integrantes las revisan durante la planificación y registran el resultado en la tarjeta del tablero.

Historia y trazabilidad: tiene identificador RA-XX, responsable, redacción “Como [rol], quiero [acción], para [beneficio]” y vínculo con los requisitos y el alcance del acta.

Aceptación definida: tiene al menos dos criterios observables, con entradas, acción y resultado esperado. Incluye rechazo o fallo cuando corresponde; no basta “debe ser seguro” o “debe funcionar”.

Estimación y capacidad: los dos integrantes registran la estimación en horas-persona y acuerdan su selección dentro de la capacidad del sprint. Se consideran las cinco horas semanales por persona y el descuento del 30 % definido en el acta.

Dependencias resueltas: no necesita una historia pendiente para completar sus criterios; existen los datos sintéticos y accesos necesarios. Si requiere mapas o geocodificación, están identificados proveedor, límites y alternativa para la prueba.

Diseño suficiente: las historias con interfaz tienen un boceto enlazado; las que cambian datos o reglas tienen definidos campos, permisos y transiciones. Se distinguen estado operativo, confianza y visibilidad cuando aplica.

Prueba y dudas resueltas: se identifican los casos de prueba y los RNF aplicables, y no quedan preguntas que cambien el resultado esperado. Si falta una decisión funcional, se registra la consulta a Javier Charry y su respuesta antes de declarar la historia lista.

Si falta una condición, la historia permanece en Por hacer, con el faltante, responsable y siguiente acción registrados. No se reemplaza una validación pendiente por una suposición presentada como confirmada.

4. Definition of Done DoD

Una historia pasa a Terminado únicamente cuando cumple los ocho puntos. El autor adjunta evidencia y el compañero la verifica; un tercero debe poder comprobar cada punto desde el repositorio y los enlaces del PR.

Criterios aceptados: cada criterio tiene un resultado y evidencia enlazados en el PR, y el compañero registra su conformidad. La historia conserva su vínculo con los requisitos y no introduce cambios de alcance sin decisión documentada.

Construcción y formato aprobados: ./mvnw verify, ./mvnw spotless:check y npm run format:check finalizan sin errores sobre el commit final del PR. Se adjunta el resultado de CI o, si aún no existe, una salida reproducible con comando, entorno y hash.

Pruebas trazadas: cada criterio funcional tiene al menos una prueba automatizada identificable y todas pasan. Las verificaciones de interacción visual o instalación que requieran ejecución manual tienen pasos, resultado y capturas revisados por el compañero. Las reglas de negocio no se validan únicamente con capturas.

Reglas críticas y cobertura: los cambios en autoría, permisos, confirmaciones, reportes, transiciones o auditoría tienen pruebas positivas, negativas y de límites aplicables. Si afectan módulos críticos, el informe de cobertura de líneas del código de producción de esos módulos alcanza al menos 80 % y queda enlazado; se documentan las exclusiones de código generado.

Seguridad y datos: la revisión confirma que no se agregaron credenciales ni datos sensibles reales; las acciones restringidas se validan en servidor; las pruebas comprueban rechazo de accesos indebidos cuando aplica. Los errores no muestran trazas internas al usuario.

Calidad del flujo afectado: el PR contiene resultados de los RNF pertinentes. Los cambios de interfaz se prueban en las tres resoluciones registradas en el plan de calidad. Los cambios PWA se prueban con recarga, desconexión y reconexión: se recupera el borrador, se identifica la información guardada y no se anuncia éxito de publicación sin respuesta del servidor. Un criterio no aplicable incluye motivo y aprobación del revisor.

Documentación y revisión completas: README, configuración y documentación de reglas o métodos públicos nuevos se actualizan según el cambio. El PR contiene la aprobación del compañero sobre la versión final y no tiene comentarios bloqueantes pendientes.

Integración y tablero: el código está integrado en develop, la compilación y pruebas de la versión integrada pasan, y la tarjeta enlaza requisito → historia → pruebas → commit/PR. Solo entonces se mueve a Terminado.

Una historia que funciona pero no cumple la DoD permanece En progreso o En revisión y no se presenta como terminada en el incremento. Los pendientes se registran y se replanifican; no se reduce la DoD para ocultarlos. Cumplirla para una historia no equivale a afirmar que los 25 RF y 15 RNF del producto completo están terminados.

5. Política de revisión de código

5.1 Quién revisa y en qué plazo

Si Carlos Mario Peña es autor, revisa Santiago Ortiz Ochoa; si Santiago es autor, revisa Carlos. Nadie aprueba su propio cambio. La rotación de roles de la semana 13 no elimina esta obligación.

El autor abre un PR hacia develop con historia o tarjeta, resumen, criterios, pruebas, resultados y riesgos del cambio. No se solicita aprobación de un PR incompleto.

El revisor responde en un máximo de 24 horas hábiles, entendidas como un día hábil de lunes a viernes, excluidos festivos; un PR abierto el viernes se revisa a más tardar el siguiente día hábil a la misma hora. La respuesta es aprobación o solicitud de cambios con comentarios concretos.

Si el revisor no está disponible, registra el bloqueo antes del vencimiento y ambos replanifican. La ausencia o la proximidad de la entrega no autorizan autoaprobación.

El revisor contrasta criterios, lee el cambio y consulta las pruebas. Su aprobación deja constancia de qué verificó; una aprobación sin lectura no cumple la política.

5.2 Qué bloquea la integración

| Causal                                                                         | Ejemplo de RedAuxilio / salida esperada                                                                                                                                                |
| ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Incumplir un criterio o una regla de negocio.                                  | Contar al autor entre las tres confirmaciones comunitarias. Corregir la regla y agregar prueba de rechazo.                                                                             |
| Compilación, formato o pruebas fallidos; evidencia exigida por la DoD ausente. | Recuperación offline sin prueba de recarga. Completar la prueba y publicar su resultado.                                                                                               |
| Fallo de seguridad o exposición de información.                                | Permitir verificar oficialmente sin autorización, guardar una contraseña en Git o devolver una traza interna. Corregir y comprobar el rechazo; si se expuso una credencial, revocarla. |
| Pérdida de integridad, auditoría o confianza.                                  | No invalidar confirmaciones tras una edición sustancial; ocultar automáticamente por tres reportes; confundir revisión con cierre. Separar estados y probar las transiciones.          |
| Incumplir el flujo o el alcance acordados.                                     | Mostrar “publicado” estando sin conexión, introducir chat o integrar sin revisión. Corregir el flujo o tramitar el cambio de alcance.                                                  |

El autor resuelve los bloqueantes; el revisor verifica la corrección y cierra el comentario. Los tres usuarios distintos y ajenos al autor, una confirmación activa por cuenta y el envío a revisión tras tres reportes independientes se conservan como reglas de la ficha. No se transforman en decisiones nuevas mediante una refactorización.

5.3 Qué no bloquea y cómo comentar

No bloquean preferencias personales que contradigan el formateador acordado, mejoras futuras fuera de la historia ni refactorizaciones opcionales sin impacto en los criterios. Se registran como sugerencias; cuando ameriten trabajo posterior, se crea una tarjeta.

Cada comentario cita archivo y línea, usa la etiqueta [BLOQUEANTE] o [SUGERENCIA], explica el efecto y propone una salida. Se comenta el código, no la capacidad de la persona.

Ejemplos ilustrativos, no comentarios de una revisión ya realizada:

[BLOQUEANTE] En la condición que registra la confirmación falta rechazar al autor. Esto permite una confirmación propia. Validar la identidad en el servicio y agregar una prueba que compruebe el rechazo.

[SUGERENCIA] La conversión de coordenadas está repetida. Podemos extraerla en una tarea posterior; los criterios y las pruebas actuales se cumplen.

5.4 Flujo del tablero

Tablero: https://trello.com/b/BiGr7UFw/redauxilio-gerencia-de-software.

Flujo: Por hacer → En progreso → En revisión → Terminado. Se mantiene el límite propuesto en la ficha: máximo dos tarjetas en progreso, una por integrante, y una en revisión. Si aparecen bloqueantes, la tarjeta vuelve a En progreso y el bloqueo queda registrado con causa y fecha. Los enlaces de pruebas y PR se agregan antes de marcarla terminada.

6. Aceptación y publicación

6.1 Roles para elaborar este taller

Al ser un equipo de dos personas, cada integrante asume dos funciones diferenciadas:

| Integrante           | Funciones en este taller                                                                                                                |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Carlos Mario Peña    | Redactor y abogado del diablo: consolida los acuerdos y plantea situaciones de carga, ausencia o ambigüedad para ajustar su viabilidad. |
| Santiago Ortiz Ochoa | Guardián de lo verificable y responsable del repositorio: exige evidencia comprobable y prepara archivo, configuración, commit y PR.    |

Estos roles de elaboración no sustituyen la revisión cruzada ni la rotación de responsabilidades definida en el acta.

6.2 Declaraciones de aceptación

Las siguientes declaraciones quedan preparadas para que cada integrante las confirme con su propia cuenta en el PR de adopción. Su inclusión en este borrador no constituye una firma ni una aprobación realizada.

Carlos Mario Peña: “conozco y acepto estos estándares”.

Santiago Ortiz Ochoa: “conozco y acepto estos estándares”.

El PR de adopción conserva ambas confirmaciones, fecha y enlace. Las revisiones posteriores del documento necesitan acuerdo de los dos integrantes y registro del motivo e impacto; no se cambian retrospectivamente las reglas para dar por terminada una historia incompleta.

6.3 Publicación y evidencia

Guardar este archivo como ESTANDARES.md en la raíz del repositorio del equipo, tal como pide el Taller 4.

Configurar y versionar los formateadores y comandos del apartado 1.3; ejecutar las comprobaciones y adjuntar los resultados.

Crear la rama docs/standards y registrar el commit docs(standards): definir estándares de trabajo del equipo 4.

Abrir el PR hacia develop, obtener revisión cruzada y las dos confirmaciones personales de aceptación; integrar y compartir con el docente el enlace permanente al archivo y al commit.

La publicación en GitHub, el hash del commit y la aceptación quedan pendientes de ejecución en el repositorio real. No se inventan enlaces, hashes ni revisiones. Los ejercicios de commits históricos y revisión de código entre equipos de la guía requieren evidencias distintas; este archivo corresponde al documento de estándares del Taller 4.

6.4 Declaración de apoyo con inteligencia artificial

Se utilizó ChatGPT de OpenAI para analizar la guía, contrastarla con el acta y la ficha de RedAuxilio y preparar este borrador. La lectura, validación técnica, aceptación, configuración de herramientas y publicación corresponden a los integrantes. No se presenta como realizada ninguna actividad que requiera evidencia del repositorio no suministrado.

6.5 Referencias

Guía suministrada: ntes de empezar.pdf, paso 7, Taller 4 y rúbrica de evaluación.

Acta_Constitucion_RedAuxilio_Corregida.pdf, 18 de septiembre de 2026, especialmente alcance, restricciones, riesgos, roles y trazabilidad.

FICHAREDAUXILIOORTIZPEÑA1.pdf, tecnología, reglas de negocio, calidad y políticas del tablero; en roles e hitos prevalece el acta corregida.

Google Java Style Guide.

Google Java Format.

Spotless para Maven.

Configuración de Prettier.

Código de sesión: LLANO_14

## Estado de aplicación

La aprobación de la guía fue confirmada por Carlos. Las frases de aceptación y los estados de propuesta contenidos en el documento original se conservan como antecedentes; no constituyen firmas ni evidencias actuales. Los cambios anteriores a esta adopción se conservan en el historial. A partir de esta actualización se aplican las convenciones aprobadas.

La base técnica RA-00 está en preparación y revisión. No satisface todavía el DoD: faltan la revisión del compañero, las comprobaciones manuales aplicables y la integración en develop. No se declaran completados los módulos críticos ni los entregables del Taller 5.
