# RedAuxilio: estado para continuar

Instantánea del 27 de octubre de 2026 (Colombia). Reglas en AGENTS.md/ESTANDARES.md; dirección visual en ESTANDARES_INTERFAZ.md. Historial de comprobaciones en EVIDENCIAS.md.

## Trabajo actual: RA-04

- Rama `feat/RA-04-actualizacion-cierre`, PR #13 abierto para revisión hacia develop (GitHub ya no lo muestra Draft); Santiago solicitó cambios en la revisión 5670680417. Base del bloque `fe4b15b1260a11857f0e938fc3fe9f5656619bd7`; HEAD identifica las correcciones y evidencias. Base develop de la historia `2abc847` (RA-03 integrada). No fusionar ramas antiguas ni modificar ramas de Santiago.
- Worktree propio `C:\Users\cmps-\.codex\worktrees\ra04-auditoria\redauxilio`; original RA-01 y worktree/servidor de validación RA-03 preservados. Sin cambios locales o commits propios no publicados al iniciar.
- Se atendió auditoría #6445652807: script estático público sin abrir API privada; sesión/perfil y respuestas tardías; cierre coherente HABILITADA desde alta/lectura antigua; historial legible; preservación de entrada ante error y cabecera móvil sin overflow. Requisitos, matriz y límites en RA04.md.
- Corrección posterior de listOwn: una consulta SQL de reportes propios visibles, mapeo público compartido, cerrados y orden conservados. Regresión real H2 con cuatro reportes: antes 5 consultas, después 1; sin dependencias nuevas. Base de este ajuste 494cd2a.
- Validación final del ajuste: 58 pruebas Java, Spotless/PMD; 42 JS; Prettier y diff --check. Pruebas nuevas reproducen fallos antes del arreglo; matriz de 25 pares, rollback SQL y cierre concurrente incluidos. No repetir suites sin cambio o incertidumbre nueva. Comprobar CI del commit de corrección publicado, sin reutilizar el verde de 494cd2a como prueba del HEAD nuevo.
- Navegador del agente: publicación/edición/cierre/reapertura de punto/vía, historial, filtros, logout sin residuos; tres tamaños sin overflow; shell recuperado al detener servidor. Evidencia final en EVIDENCIAS.md, sin atribuir pruebas humanas a Carlos ni Santiago.
- Instancia propia de prueba en `http://127.0.0.1:58414/`, H2 exclusiva en memoria; reiniciar elimina cuentas y publicaciones sintéticas. Geocodificación externa deshabilitada. Servidor anterior no reiniciado en este ajuste: su URL no acredita la nueva lógica SQL sin reiniciar. No detener servidores ajenos ni perder la H2 de validaciones manuales. No hay cambios de Trello, merge ni aprobación humana.

## Siguiente acción

1. Validación manual humana del HEAD final, incluida instalación PWA y desconexión/reconexión total. La caída del servidor solo verifica respaldo del SW, no desconexión del navegador.
2. Nueva revisión de Santiago sobre la corrección de listOwn y sus evidencias; no autoaprobar ni integrar desde este bloque.
3. RA-05 es dependencia futura de invalidación de confirmaciones; no hay confirmaciones implementadas. Las ediciones concurrentes se serializan con bloqueo y auditoría, sin control optimista entre formularios de dispositivos distintos.

Lectura mínima: este traspaso, RA04.md y última sección RA-04 de EVIDENCIAS.md. RA-01/02/03 conservan sus contratos e historial; consultar solo si el cambio los afecta.
