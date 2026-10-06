# RedAuxilio: estado para continuar

Instantánea del 6 de octubre de 2026 (Colombia). Normas en AGENTS.md y ESTANDARES.md; contrato en RA02.md; evidencia automática y humana separada en EVIDENCIAS.md.

## Estado actual

- Repositorio de Santiago: `C:\Users\Santiago\redauxilio`; remoto https://github.com/Carlos1120/redauxilio.git.
- Rama `feat/RA-02-interfaz-mapa`; PR https://github.com/Carlos1120/redauxilio/pull/8 hacia develop, en borrador. Commit inicial del rediseño `40eac93`; se incorpora develop actualizado `ad3f205` (PR #7) conservando la corrección por tesela, sus pruebas y evidencias. Consultar HEAD y CI antes de revisar.
- PR #5, #6 y #7 integrados en develop. No fusionar otra vez las ramas antiguas ni reutilizar un traspaso previo como estado actual. La integración anterior no aprueba este rediseño.
- El rediseño solicitado por Santiago usa Onest, rojo `#d7262e`, mapa como fondo y paneles flotantes de lista/detalle. Conserva API, filtros avanzados y borrador local; añade búsqueda local y filtros rápidos. Leaflet 1.9.4 y Onest se sirven localmente con sus licencias; caché PWA v6. No hay geolocalización, rutas, publicación real ni datos oficiales.
- Santiago observó el diseño en IntelliJ y autorizó commit y push; la revisión final corresponde a Carlos. No atribuir aprobación del PR ni pruebas del agente a Santiago.

## Evidencias y límites

- Rediseño: comprobado mediante Codex en servidor propio 8081, Windows/Corretto 21.0.10 y navegador integrado. Tres tamaños 360×800, 768×1024 y 1366×768 sin desbordamiento horizontal; selectores completos en tableta y detalle modal móvil. Búsqueda/filtros actualizan lista/mapa/detalle. Borrador guardado, recuperado y descartado. Caída del servidor muestra caché fechada; reinicio recupera datos del servidor. Esto no simula desconexión total de red.
- Antes de incorporar PR #7: cinco pruebas Node y 22 Java, Prettier, Spotless, PMD y git diff --check aprobados. OpenJDK 25 falla en Spotless; verify pasa con el JDK 21 instalado. El registro posterior a la actualización debe consultarse en el PR y EVIDENCIAS.md.
- PR #7 aporta 23 ejecuciones Java y nueve JS, corrección del aviso cartográfico por tesela y pruebas de carga aisladas. RNF-01: 1.000 datos/20 usuarios, p95 interno 19,802/4,299/0,252 ms (consulta/filtros/detalle), primeros resultados 366,9 ms en el entorno registrado. El rediseño no modifica el servidor; conservar esa evidencia sin afirmar medición nueva.
- Carlos comunicó validación humana de responsive, instalación PWA en Chrome y desconexión/reconexión real sobre `e95b7da`, antes del rediseño. RNF-10 anterior: Lighthouse 100/100 móvil/escritorio. Esos resultados no acreditan automáticamente la nueva interfaz ni la nueva caché; confirmar los casos afectados. No atribuirlos a Santiago.
- Leaflet retiene los fallos de cada tesela hasta tileload/tileunload; el rediseño conserva esta corrección y usa el mismo estado para mostrar el aviso. Cartografía requiere Internet y no se descarga masivamente ni se guarda por service worker. Consultas offline dependen de la URL exacta previamente guardada.

## Siguiente bloque

Guía de continuidad visual preparada a petición de Santiago para el PR #8: ESTANDARES_INTERFAZ.md. AGENTS.md y README.md la enlazan; .interface-design/system.md remite a una única dirección Onest/rojo. La guía requiere revisión de Carlos; no modifica la aplicación ni registra su aprobación.

1. Consultar CI y commit final del PR #8; Carlos revisa código, criterios y evidencias conforme al estándar.
2. Confirmar instalación PWA, desconexión total/reconexión, teclado y accesibilidad sobre el rediseño. Reutilizar solo las evidencias previas sin impacto; no repetir carga del servidor sin nueva incertidumbre.
3. Resolver bloqueantes, completar la revisión y luego integrar por squash con pruebas sobre develop. No declarar DoD ni mover Trello por cuenta del agente.

Servidor de IntelliJ 8080 preservado; puede requerir reinicio para cargar plantillas nuevas. La instancia propia 8081 se usa como vista previa y puede detenerse. Tarjeta RA-02: https://trello.com/c/LYDxDTZT.

Lectura mínima: este estado y AGENTS.md; secciones finales de RA02.md/EVIDENCIAS.md; ESTANDARES.md al aplicar reglas.
