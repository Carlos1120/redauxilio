# Estándares del equipo 4 — RedAuxilio


Estado: propuesta inicial pendiente de revisión y aceptación de Carlos Mario Peña y Santiago Ortiz Ochoa. La aceptación personal sigue pendiente. Los controles implementados en RA-00 y sus resultados se documentan en EVIDENCIAS.md; esta propuesta no demuestra cumplimiento global.


## 1. Estilo y nombres


Tecnología prevista: Java/Spring Boot, Thymeleaf, Bootstrap, JavaScript, PostgreSQL y Leaflet; monolito modular con PWA para consulta previamente almacenada y borradores locales; el alcance completo del documento de requisitos permanece vigente. Adoptar Google Java Style (https://google.github.io/styleguide/javaguide.html) y google-java-format. RA-00 configura Spotless con google-java-format, PMD, JUnit y JaCoCo; Maven verify y GitHub Actions ejecutan los controles disponibles. Identificadores en inglés; interfaz, documentación y commits en español.


- Clases en UpperCamelCase; métodos y variables en lowerCamelCase; constantes en UPPER_SNAKE_CASE.
- Métodos con verbo y responsabilidad concreta, por ejemplo createReport. Booleanos como isVerified o hasPermission.
- Expresar unidades cuando corresponda: timeoutSeconds, maxImageSizeBytes.
- Organizar por módulos de dominio y capas dentro de cada módulo; validar reglas en servicios y permisos en el servidor.


## 2. Commits y ramas


Conventional Commits 1.0.0: tipo(alcance): descripción. Tipos: feat, fix, docs, refactor, test, style, chore, build y ci. Ejemplo: feat(publicaciones): agrega formulario de reporte.


main conserva versiones integradas. Los cambios posteriores a esta preparación se realizan en feat/identificador-descripcion, fix/identificador-descripcion o docs/identificador-descripcion y se integran mediante pull request. Vincular la tarjeta de Trello o issue en la descripción del pull request. No se requiere develop inicialmente. No reescribir el historial compartido.


## 3. Definition of Ready


Antes de iniciar una historia:


1. Tiene identificador, necesidad del usuario y requisito RF/RNF asociado.
2. Incluye criterios de aceptación observables y casos de error relevantes.
3. El equipo registró una estimación compatible con su disponibilidad.
4. Las dependencias necesarias están resueltas o existe una alternativa acordada.
5. Se conoce cómo probarla y qué evidencia registrar.


## 4. Definition of Done


Antes de marcar una historia terminada:


1. Cada criterio de aceptación tiene resultado de prueba registrado; reglas y permisos críticos tienen pruebas automatizadas.
2. Compilación y pruebas aplicables pasan; sus comandos se documentan en README al añadir el código.
3. El otro integrante aprobó el pull request y los comentarios bloqueantes quedaron resueltos.
4. Los controles de calidad aplicables cumplen los umbrales del documento de requisitos; se adjunta evidencia con versión y contexto.
5. No se incorporan credenciales ni datos personales reales; los errores públicos no muestran información interna.
6. README y documentación afectada están actualizados.
7. El cambio está integrado sin conflictos y la tarjeta enlaza commit/pull request y resultados.


Las pruebas manuales se admiten para instalación PWA, desconexión, accesibilidad y usabilidad, con pasos y resultados reproducibles. Cobertura mínima del 80 % en módulos críticos según RNF-14; configurar su medición al implementar esos módulos. No marcar como terminado un control no ejecutado.


## 5. Revisión de código


Carlos revisa cambios de Santiago y Santiago los de Carlos. Plazo propuesto: dos días hábiles desde la solicitud; acordar sesiones según las cinco horas semanales por persona. Si no se puede cumplir, registrar impedimento y replanificar, sin autoaprobar.


Bloquean: reglas incorrectas, acceso indebido, pérdida de datos o confirmación falsa de envío, credenciales expuestas, pruebas obligatorias fallidas o ausentes y criterios de aceptación incumplidos.


No bloquean: preferencias personales de estilo que no contradigan la guía, mejoras futuras y optimizaciones sin evidencia de necesidad. Comentar sobre el código, indicar el problema y proponer una salida.


En la PWA revisar específicamente que el borrador no se confunda con publicación enviada y que los datos almacenados muestren fecha y estado de conexión.


## 6. Aceptación


- Carlos Mario Peña: pendiente de revisión y declaración personal de aceptación.
