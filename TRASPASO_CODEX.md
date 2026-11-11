# RedAuxilio — traspaso vigente

Actualización: 11 de noviembre de 2026 (Colombia). RA-16 del PR #31 queda como registro histórico. La rama `codex/ux-separated-app` parte de `develop` `31cc5fb`; PR #34 abierto hacia develop, head `f9fb246`, Carlos solicitado como revisor formal; sin aprobación ni integración. Mapa, Publicar y Mis reportes son vistas separadas con historial, foco accesible y ajuste de Leaflet al volver. El formulario tiene tarjetas cálidas, mapa de ubicación, ancho completo y controles táctiles grandes. Navegación: botones de 124 px en escritorio y distribuidos en móvil; caché PWA v33. `localhost:8081` sirve esta rama con H2 temporal en memoria. `localhost:8080` permanece en el checkout IntelliJ `C:\Users\Santiago\redauxilio`, rama RA-08 `d6a2348`, con base H2 en archivo; no se modificó. Maven verify: 204 pruebas; Node: 148/148; Spotless/PMD, Prettier y diff check aprobados con JDK 21. CI pendiente; no tocar la base ni el proceso de 8080.

## Integración conservada

El único conflicto textual fue este traspaso. Las evidencias históricas de ambas ramas permanecen en `EVIDENCIAS.md`: RA-16 del 4 de noviembre y RA-14 del 11 de noviembre. Sus resultados anteriores no constituyen pruebas del árbol combinado. Los registros RA-10 y las pruebas PostgreSQL anteriores siguen siendo históricos; consultar GitHub antes de actuar sobre otro PR.

RA-14 conserva preconsulta privada, justificación obligatoria ante coincidencias, bloqueo por categoría, control definitivo transaccional y limpieza de advertencias después del alta. `RA14.md` y `RA14_REGRESIONES.md` mantienen contrato y regresiones. RA-15 conserva la última consulta pública válida cuando falla otra zona sin red, con aviso de procedencia anterior. No se añaden recursos privados al service worker ni se cambia su política de caché.

## Corrección RA-16

Después de esperar el token CSRF, el panel comprueba nuevamente la generación de pantalla y la conexión antes de iniciar el POST. Ocultar la pantalla, cambiar identidad o desconectarse invalida la operación pendiente. Esto no cancela solicitudes ya enviadas ni revierte decisiones confirmadas. La restricción persistente continúa separada del bloqueo temporal de autenticación y no elimina permisos previamente concedidos.

Las pruebas dirigidas cubren invalidación asíncrona, rutas RA-14, alta JSON/multipart, confirmaciones, denuncias, avistamientos, verificación y permiso operativo persistente. Incluyen sesión abierta, motivos, auditoría, rollback, paginación y bloqueo SQL entre conexiones independientes. PostgreSQL se limita a una base local exclusiva con nombre validado; la limpieza elimina únicamente cuentas ficticias propias. Logs de esta ejecución fuera de Git en `ra16-qa`; ninguna credencial se incorpora al repositorio.

Validación local completada: Maven verify 204 Java, Node 147/147, Spotless, PMD, Prettier y diff aprobados; ampliación HTTP de historial verificada aparte (13 pruebas dirigidas). PostgreSQL 18.3: 13 pruebas más preconsulta RA-14 y paginación HTTP dirigidas, sin fallos. Cobertura crítica superior al 95 %. Siguiente: publicar normalmente solo si el remoto sigue en el HEAD esperado y comprobar CI exacto. Mantener PR Draft y revisión visual/aceptación humana pendientes. No presentar comprobaciones automatizadas como revisión de Carlos ni Santiago.
