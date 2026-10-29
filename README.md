# RedAuxilio

PWA colaborativa de información georreferenciada ante terremotos. Equipo 4: Carlos Mario Peña y Santiago Ortiz Ochoa. Equipo de dos autorizado; Javier Charry cumple como interlocutor según confirmación de Carlos. Contacto y agenda pendientes.

## Primera versión ejecutable

Consulta pública con mapa Leaflet, filtros, detalle, borrador local y base PWA. RA-01, RA-02 y RA-03 están integradas en `develop`; RA-03 incluye publicación persistente de puntos de asistencia y vías afectadas. RA-04 sigue en revisión en el PR #13 y RA-05 se desarrolla aisladamente desde `develop`. Los seis ejemplos de RA-02 siguen siendo ficticios. No usar en una emergencia real. Se conserva el alcance completo de requisitos.

## Ejecutar en Windows

1. Instalar JDK 17 o 21 y Git. Comprobar `java -version` y `git --version`.
2. Clonar este repositorio con una cuenta autorizada y seleccionar la rama del PR.
3. Abrir la carpeta en terminal o como proyecto Maven en el IDE.
4. Ejecutar `.\mvnw.cmd verify` (primera ejecución requiere Internet).
5. Ejecutar `.\mvnw.cmd spring-boot:run`.
6. Abrir http://localhost:8080. Detener con Ctrl+C.

## Linux y macOS

`chmod +x mvnw`, después `./mvnw verify` y `./mvnw spring-boot:run`.

Maven Wrapper descarga Maven 3.9.11; no exige instalar Maven por separado. Java mínimo 17. Spring Boot 3.5.16. Puerto configurable por variable PORT. El modo local usa una base H2 persistente bajo `data/`, excluida de Git. Para desplegar, activar el perfil `prod` y definir `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `SESSION_COOKIE_SECURE=true` con PostgreSQL; nunca guardar secretos en el repositorio.

## Acceso ciudadano (RA-01)

El correo normalizado identifica la cuenta. La contraseña requiere al menos 12 caracteres, se conserva como hash BCrypt con sal aleatoria y no se registra en texto. Cinco intentos fallidos bloquean esa cuenta durante 15 minutos. La sesión rota su identificador al entrar, usa cookie HttpOnly/SameSite=Lax y termina al cumplir 24 horas. Formularios conservan protección CSRF. El registro no valida que el correo pertenezca a la persona.

## Publicación georreferenciada (RA-03 integrada en `develop`)

Una cuenta autenticada puede publicar un punto de asistencia o una vía afectada desde el formulario. Se exigen categoría, nombre o tramo, descripción, estado y ubicación; los campos específicos dependen de la categoría. La ubicación puede señalarse en el mapa, proponerse con permiso del dispositivo, buscarse explícitamente por Nominatim o escribirse manualmente; latitud y longitud siguen editables. Antes de enviar se guarda el borrador local; el servidor limita a cinco publicaciones por cuenta en una hora y responde HTTP 201 solo después de persistir. Si falla, el borrador local se conserva. La búsqueda usa un proxy con caché y máximo una petición por segundo por instancia; `GEOCODING_SEARCH_URL` permite cambiar el proveedor y `GEOCODING_ENABLED=false` deshabilitarlo. En despliegues con varias instancias se requiere un limitador compartido antes de habilitar Nominatim público.

## Entender el código

- `pom.xml`: dependencias y controles de compilación.
- `RedAuxilioApplication`: inicia el servidor.
- `publications/PublicationController`: recibe las consultas de pantalla y API.
- `publications/PublicationService`: valida filtros y área, excluye contenido oculto y combina ejemplos ficticios con publicaciones visibles persistidas.
- `publications/PublicationCreationService` y `CitizenPublicationRepository`: validan y guardan publicaciones, con cupo transaccional por cuenta.
- `geography/PlaceSearchService`: consulta explícita de lugares con caché y límite de frecuencia.
- `templates/index.html`: pantalla inicial.
- `static/map.js`: mapa, agrupación y selección de reportes con Leaflet.
- `static/app.js` y `static/publication-form.js`: consultan la API, conservan el borrador y publican con sesión y CSRF.
- `static/sw.js`: guarda solo recursos y consultas públicas de demostración, con fecha de almacenamiento; no guarda operaciones de escritura.
- `PublicationTests`: verifica consulta, categorías inválidas y recursos públicos.

## Verificar y demostrar

1. Consultar todas las categorías y filtrar cada una.
2. Visitar `/api/publications?category=ROAD` y después `?category=OTHER`: la primera devuelve datos, la segunda HTTP 400.
3. Guardar texto ficticio y coordenadas, recargar y comprobar recuperación.
4. Tras primera visita y service worker activo, recargar sin red y revisar el aviso y antigüedad. Una categoría nunca consultada puede no estar guardada.
5. Con sesión, crear un punto y una vía; comprobar HTTP 201, lista, detalle y persistencia tras reiniciar.
6. Sin sesión o CSRF, probar rechazo; con coordenadas inválidas, HTTP 400; tras cinco publicaciones en una hora, HTTP 429. Sin red, no se anuncia éxito y el borrador sigue disponible.

La instalación PWA depende del navegador y entorno seguro (localhost para desarrollo; HTTPS al desplegar). La base no demuestra el cumplimiento completo de RNF-03 ni de accesibilidad/usabilidad. Fallos de almacenamiento local se comunican. Borradores solo sobreviven cuando se pulsa Guardar; el autoguardado del formulario final queda pendiente.

## Calidad y colaboración

Para continuar el diseño de las pantallas, leer [ESTANDARES_INTERFAZ.md](ESTANDARES_INTERFAZ.md): paleta, tipografía, componentes, adaptación, accesibilidad y lista de revisión. Reutilizar el CSS compartido; la guía refleja la dirección visual del PR #8 y requiere revisión del compañero.

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

## Entender el código

Leer [GUIA_CODIGO.md](GUIA_CODIGO.md): explica cada archivo principal, el recorrido de una consulta, el almacenamiento local y cómo comprobar el comportamiento. Los comentarios del código describen contratos y decisiones; actualizar ambos cuando cambie la lógica.

## Moderación privada — RA-08, primer bloque

`/operator` permite consultar la cola EN_REVISION, contexto de denuncias e historial interno solo con ROLE_OPERATOR persistido y vigente. Este incremento no incluye las cinco decisiones administrativas. No se cachean datos privados ni se muestran sin conexión. El registro público nunca concede ese rol y no hay cuenta o contraseña predeterminada.

El aprovisionamiento inicial/retiro se ejecuta por configuración controlada sobre una cuenta existente comprobada por el administrador, con UUID idempotente, actor y justificación; se audita transaccionalmente. La revocación se comprueba en la siguiente solicitud, incluso con sesión abierta; conceder exige nuevo login si faltaba el rol. Parámetros, frontera de confianza, riesgos y pruebas en [RA08.md](RA08.md). No asignar permisos a una cuenta solo por su correo: el registro ciudadano no verifica su propiedad.
