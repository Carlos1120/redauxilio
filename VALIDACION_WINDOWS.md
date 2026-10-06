# Validar RedAuxilio en Windows

Procedimiento, no evidencia de ejecución. Usar casos de RA02.md y registrar resultados en EVIDENCIAS.md. ESTANDARES.md determina los checks de integración.

## Preparación y arranque

Comprobar carpeta, status, HEAD, rama y remotos antes de modificar. Verificar Java/javac, JAVA_HOME, Node/npm al iniciar entorno o si hay indicios de cambio. Usar Java 17, Node 24/npm 11; no fijar rutas JDK sin comprobarlas. En PowerShell usar `npm.cmd` y `.\mvnw.cmd`; entrecomillar argumentos Maven `-D` con puntos. `npm.cmd ci` solo si faltan dependencias o cambió el lockfile.

Un servidor preexistente no acredita HEAD. Registrar SHA, cambios locales, comando, puerto y proceso. Si 8080 está libre, `.\mvnw.cmd spring-boot:run`; si está ocupado, preservar el proceso existente y usar puerto asignado:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=0'
```

Leer el puerto real del log; comprobar inicio HTTP 200 y `/api/publications`. No asumir 8081 libre. Detener solo procesos propios al finalizar, salvo que el usuario necesite continuar; registrar cómo detenerlos.

## Casos y evidencia

- Registrar fecha local, responsable real, SHA/cambios, navegador/versión, URL y red. Separar ejecución del agente de validación humana.
- Cubrir los casos pertinentes de RA02.md: área, categorías, combinación, limpieza, cerrados/ocultos, detalle desde lista/marcador/grupo y errores API.
- Revisar filtros, mapa, lista, diálogo y borrador completos en 360×800, 768×1024 y 1366×768; texto, superposiciones y desbordamiento. Ancho DOM no sustituye inspección visual. Restablecer viewport.
- Recorrer Tab/Shift+Tab, foco visible, Enter/Escape y retorno de foco. No confundir eso con auditoría de accesibilidad o lector de pantalla.
- Usar perfil de prueba sin datos del usuario: activar service worker, consultar URL exacta, recargar offline, revisar aviso/fecha, consultar URL sin caché y reconectar. Probar instalación y bloqueo de CDN/cartografía con lista utilizable. No borrar datos del usuario ni desconectar Windows entero.
- Si faltan controles de red en la herramienta, dejar caso pendiente con pasos concretos. No inventar resultados.
- Reproducir cierre del menú de grupo comprobando área antes/después y consulta estabilizada; registrar síntoma sin atribuir una causa no probada.

## Calidad y cierre

Java: `.\mvnw.cmd verify`; agrupación JS: `npm.cmd test`; formato: `npm.cmd run format:check`. No repetir suites aprobadas sin cambio, entorno distinto o incertidumbre; antes de integrar cumplir todos los checks de ESTANDARES.md en la versión final.

Carga pendiente: 1.000 publicaciones/20 usuarios con duración, calentamiento, errores y red documentados. Medir p95 servidor ≤2 s y primeros resultados del navegador ≤3 s por separado. Duración HTTP cliente no equivale a procesamiento interno; seis datos no acreditan carga de 1.000. No generar carga contra OSM/CDN.

Registro: `Caso | versión/entorno | pasos | esperado | observado | estado | responsable | evidencia`.
Conservar logs completos fuera del chat; mostrar resumen/errores pertinentes. Las capturas siguen la norma vigente, sin asumir integrado el PR #5. Actualizar traspaso; DoD y revisión humana siguen requisitos independientes.
