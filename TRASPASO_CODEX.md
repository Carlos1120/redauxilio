# RedAuxilio — traspaso vigente RA-08

Actualización: 29 de octubre de 2026 (Colombia). Repositorio `C:\Users\Santiago\redauxilio`, rama `feat/RA-08-acciones-moderacion`, basada en `origin/develop` `73034e57c92df05f69ca9d4897153c3c67513ac3`. El incremento funcional está publicado en el PR Draft [#20](https://github.com/Carlos1120/redauxilio/pull/20), hacia `develop`. El primer bloque RA-08 se integró por el PR #18. No cambiar a Terminado: aceptación y revisión cruzada de Carlos, CI del HEAD final e integración siguen pendientes.

## Incremento implementado

`ModerationActionService` y `POST /api/operator/publications/{id}/actions` habilitan mantener, corregir, ocultar, restituir, cerrar y reabrir con motivo obligatorio de 1–500 caracteres. El backend vuelve a validar ROLE_OPERATOR persistente, bloquea la fila de publicación y registra actor, decisión, motivo y snapshots antes/después en `publication_moderation_action`; todo se confirma o revierte en una transacción. La auditoría administrativa no contamina `citizen_publication_change`, visible al autor.

Mantener retira EN_REVISION y pasa las denuncias activas a `active_slot=NULL`, conservándolas como historia; una misma cuenta puede denunciar de nuevo en una ronda posterior. Corregir valida los campos de RA-04 y rechaza cambios a autor, categoría o estado. Correcciones sustanciales y transiciones operativas invalidan respaldos mediante una nueva versión, sin borrar confirmaciones históricas. Ocultar/restituir no modifica confianza ni cierre. Cerrar/reabrir sigue la matriz por categoría, con las vías HABILITADA heredadas interpretadas como cerradas.

`/operator` presenta formularios accesibles, renueva el header CSRF correcto y no anuncia éxito sin respuesta. Ignora respuestas privadas tardías tras salida/cambio de contexto. Cola, detalle y auditoría permanecen `no-store`; el service worker v20 renueva los estilos públicos sin almacenar las rutas privadas.

## Verificación y límites

Maven `verify`: 95/95 pruebas; Spotless y PMD correctos con Corretto 21. Node: 86/86; `npm ci`, Prettier y `git diff --check` correctos. JaCoCo mide 130/139 líneas (93.5 %) en `ModerationActionService` y 16/20 (80 %) en su modelo `Case`. Las integraciones usan H2: concurrencia y migración PostgreSQL no están certificadas. La prueba visual manual a 360×800, 768×1024 y 1366×768 aún no se ha realizado.

Riesgo funcional restante: repetición abusiva de denuncias se difiere a RA-16; el operador no obtiene verificación oficial ni gestión de cuentas. No se actualizó Trello. Revisar en GitHub el CI del HEAD vigente y esperar la revisión humana antes de cualquier integración. Consultar `RA08.md` y la última sección RA-08 de `EVIDENCIAS.md`; no cargar el historial completo por rutina.
