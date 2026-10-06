# RedAuxilio

PWA colaborativa de información georreferenciada ante terremotos. Equipo 4: Carlos Mario Peña y Santiago Ortiz Ochoa. Equipo de dos autorizado; Javier Charry cumple como interlocutor según confirmación de Carlos. Contacto y agenda pendientes.

## Primera versión ejecutable

Consulta pública con mapa Leaflet, agrupación y filtros por categoría, estado, confianza y área visible; detalle de reportes ficticios, borrador local y base PWA. La propuesta RA-02 está en validación; sin persistencia, autenticación ni publicación al servidor todavía. No usar en una emergencia real. El alcance completo del Word sigue vigente.

## Ejecutar en Windows

1. Instalar JDK 17 o 21 y Git. Comprobar `java -version` y `git --version`.
2. Clonar este repositorio con una cuenta autorizada y seleccionar la rama del PR.
3. Abrir la carpeta en terminal o como proyecto Maven en el IDE.
4. Ejecutar `.\mvnw.cmd verify` (primera ejecución requiere Internet).
5. Ejecutar `.\mvnw.cmd spring-boot:run`.
6. Abrir http://localhost:8080. Detener con Ctrl+C.

## Linux y macOS

`chmod +x mvnw`, después `./mvnw verify` y `./mvnw spring-boot:run`.

Maven Wrapper descarga Maven 3.9.11; no exige instalar Maven por separado. Java mínimo 17. Spring Boot 3.5.16. Puerto configurable por variable PORT. No se necesita PostgreSQL para este incremento de datos ficticios.

## Entender el código

- `pom.xml`: dependencias y controles de compilación.
- `RedAuxilioApplication`: inicia el servidor.
- `publications/PublicationController`: recibe las consultas de pantalla y API.
- `publications/PublicationService`: valida filtros y área, excluye contenido oculto y entrega datos ficticios.
- `templates/index.html`: pantalla inicial.
- `static/map.js`: mapa, agrupación y selección de reportes con Leaflet.
- `static/app.js`: consulta la API y conserva el borrador en IndexedDB.
- `static/sw.js`: guarda solo recursos y consultas públicas de demostración, con fecha de almacenamiento; no guarda operaciones de escritura.
- `PublicationTests`: verifica consulta, categorías inválidas y recursos públicos.

## Verificar y demostrar

1. Consultar todas las categorías y filtrar cada una.
2. Visitar `/api/publications?category=ROAD` y después `?category=OTHER`: la primera devuelve datos, la segunda HTTP 400.
3. Guardar texto ficticio y coordenadas, recargar y comprobar recuperación.
4. Tras primera visita y service worker activo, recargar sin red y revisar el aviso y antigüedad. Una categoría nunca consultada puede no estar guardada.
5. Descartar borrador. Nunca se anuncia publicación enviada porque no hay endpoint de escritura.

La instalación PWA depende del navegador y entorno seguro (localhost para desarrollo; HTTPS al desplegar). La base no demuestra el cumplimiento completo de RNF-03 ni de accesibilidad/usabilidad. Fallos de almacenamiento local se comunican. Borradores solo sobreviven cuando se pulsa Guardar; el autoguardado del formulario final queda pendiente.

## Calidad y colaboración

`./mvnw spotless:apply` aplica formato; `./mvnw verify` compila, prueba, comprueba formato y ejecuta PMD. Reportes en `target/surefire-reports`, `target/site/jacoco/index.html` y `target/pmd.xml`.

Leer [ESTANDARES.md](ESTANDARES.md), [PREPARACION.md](PREPARACION.md) y [EVIDENCIAS.md](EVIDENCIAS.md). Guía aprobada según Carlos; conservar los registros personales reales de aceptación. Cambios por rama y PR; no subir secretos ni datos personales reales.

Tablero: https://trello.com/b/BiGr7UFw/redauxilio-gerencia-de-software

## Declaración de uso de IA

Codex de OpenAI apoyó código, pruebas y documentación. La evidencia diferencia comprobaciones ejecutadas por el agente de revisión humana pendiente. Carlos y Santiago son responsables de revisar y comprender el resultado.

## Aplicación de la guía aprobada

Guía completa: [ESTANDARES.md](ESTANDARES.md). Java 17, Node 24.x y npm 11.x; Prettier 3.9.9 está fijado con package-lock.json. El demostrador usa datos sintéticos y no requiere PostgreSQL. La persistencia del proyecto completo sí requerirá PostgreSQL; aún no está implementada.

```bash
npm ci
npm run format
npm run format:check
./mvnw spotless:apply
./mvnw spotless:check
./mvnw verify
```

En Windows sustituir ./mvnw por mvnw.cmd. Activar formato al guardar en los editores personales y usar las versiones del proyecto; la configuración compartida no demuestra que cada integrante haya configurado su editor.

Crear las ramas de trabajo desde develop: feat/RA-XX-descripcion, fix/RA-XX-descripcion, docs/nombre o chore/nombre. PR hacia develop con revisión del compañero y comprobaciones aprobadas. main recibe únicamente promociones desde develop. Mensaje: tipo(modulo): descripción en infinitivo imperativo [RA-XX]; tipos feat, fix, docs, refactor, test, style y chore. Para tareas transversales, enlazar la tarjeta de gestión en el PR sin inventar una historia funcional.

La rama RA-00 se creó antes de adoptar develop; se conserva su historial y se ajusta el destino del PR. Los commits anteriores son antecedentes y no se reescriben. La aprobación de la guía no equivale a aprobar automáticamente el código ni a completar el DoD.

## Validar RA-02

Leer [RA02.md](RA02.md) para contrato, proveedor, casos de prueba y pendientes. Ejecutar `npm test` además de formato y Maven. El mapa base requiere conexión; la lista sigue disponible si el CDN o la cartografía fallan. Las bibliotecas CDN no se guardan con el service worker.
