# RedAuxilio — traspaso vigente RA-08

Actualización: 29 de octubre de 2026 (Colombia). Rama `feat/RA-08-moderacion-trazable`, worktree `C:\Users\cmps-\.codex\worktrees\ra08-moderacion\redauxilio`, desde origin/develop `8c9f6b6ca066dca34375751ac6eca2f7abbf0b28` comprobado con fetch. Responsable Carlos Peña; revisión cruzada posterior Santiago Ortiz. Consultar contrato RA08.md y última sección RA-08 de EVIDENCIAS.md; no cargar historia completa. Los worktrees previos y servidores 57238/57656 no se modifican.

## Primer incremento implementado

Solo infraestructura segura de operador y consulta privada. ROLE_OPERATOR persiste por cuenta existente; registro público jamás concede. Aprovisionamiento explícito por configuración GRANT/REVOKE con ID/correo comprobados, actor, motivo y UUID; deshabilitado por defecto, sin credenciales iniciales. Idempotencia no repite un UUID ni revierte una revocación posterior; permiso y auditoría se confirman/revierten juntos. Control de despliegue/base constituye la frontera administrativa: el registro no verifica propiedad del correo. No hay API HTTP de asignación.

Login carga rol persistido; cada petición operativa exige rol de sesión y permiso SQL actual. Revocar o bloquear temporalmente deniega la siguiente petición; conceder requiere nuevo login si faltaba el rol. Cola `/operator`, API `/api/operator/publications`, contexto por ID y auditoría de permisos protegidos 401/403 y no-store. Solo EN_REVISION entra en cola; contextos ocultos o con denuncias históricas siguen disponibles. RN-19 admite oculto sin denuncias; regresión reprodujo 404 y pasó después de corregir lectura. Comparte mapeador público sin alterar confianza, confirmaciones, estado ni visibilidad. Detalle acotado a 50 denuncias/cambios con aviso de truncamiento; navegar a cambios más antiguos queda para siguiente bloque.

UI con componentes Onest/rojo existentes, razones como texto, sin almacenamiento/caché privada ni escrituras pendientes. Limpia datos por salida, red, identidad y ocultar página; descarta respuestas tardías. No implementa ninguna decisión administrativa ni resolución de denuncias.

## Verificación y siguiente paso

Específicas finales: 14 Java y 18 JS pertinentes. Verificación completa final: 91 Java, 84 JS, Spotless/PMD; cobertura de líneas del módulo nuevo >=80 %, cifra exacta en evidencias. npm ci correcto; Prettier/diff aprobados antes de publicar. Navegador aislado comprobó consulta/contexto/permisos, salida, foco y tres resoluciones sin overflow. Son herramientas; aceptación humana pendiente. Instancia propia 49753 usa H2 de archivo exclusiva en TEMP; reiniciar no equivale a borrar su archivo.

Trello RA-08 En progreso con excepción temporal WIP 3/2; RA-05/07 siguen En progreso y RA-04 En revisión, sin cierres artificiales. Mantener PR Draft, consultar CI del HEAD publicado y solicitar revisión solo tras aceptación humana. Antes de decisiones acordar políticas propuestas de corrección sustancial/invalidez de respaldos, nuevas denuncias tras resolución y reapertura explícita de cerrados. No avanzar a ese bloque automáticamente. No autoaprobar, integrar ni implementar RA-06/13/16.
