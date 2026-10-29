# RedAuxilio — traspaso vigente

Actualización: 28 de octubre de 2026 (Colombia). Este resumen contiene el estado necesario para retomar; verificar GitHub y el árbol antes de actuar. Normas en AGENTS.md/ESTANDARES.md, contrato de esta rama en RA07.md y resultados extensos en la última sección RA-07 de EVIDENCIAS.md. No cargar el historial completo por rutina.

## Rama y base preservada

Trabajo activo RA-07 en `feat/RA-07-reporte-contenido`, worktree `C:\Users\cmps-\redauxilio-ra07`. El árbol estaba limpio al iniciar en `7632893`, con dos commits locales no publicados. Ese merge ya incorporaba develop `0cfdc709` (PR #15); se preservaron sus padres e historial, sin repetir merge, rebase ni force push. El HEAD final identifica implementación/evidencias y se entrega como PR Draft hacia develop. Comprobar su HEAD y CI en GitHub antes de continuar. No se modificaron ramas ajenas, PR #15 ni Trello.

## Comportamiento implementado

Denuncias persistentes con actor, motivo y fecha, identidad de sesión y CSRF. Una activa por cuenta/publicación; autor, duplicado, visitante y publicación oculta/inexistente/ejemplo se rechazan. Carlos confirmó que los cerrados visibles también se pueden denunciar, incluida una vía HABILITADA heredada; nunca se reabren por denunciar. La razón admite 1–500 caracteres y no aparece en respuestas públicas ni en el historial privado del autor.

Tres denunciantes activos independientes activan reviewStatus EN_REVISION en una transacción con registro y auditoría. No se pierde confianza comunitaria, confirmaciones, estado operativo, cierre ni visibilidad. SIN_REVISION significa ausencia de alerta, no validación. La tabla de denuncias reserva active_slot NULL para resolución futura, conserva históricos y restringe la activa a slot 1. No hay API administrativa: RA-08 añadirá resolución y su auditoría.

La acción Reportar contenido está en el detalle; el aviso de revisión se muestra separado de confianza. Motivos solo en memoria del formulario; se limpian en logout, selección y desconexión. No se guardan en caché ni se encolan/reintentan POST. SW v18 renueva recursos públicos.

## Validación, límites y siguiente paso

Verificación local final: 76 Java (7 específicas nuevas), Spotless/PMD, 72 JS, Prettier y diff --check aprobados. Núcleo nuevo: 62/65 líneas JaCoCo, 95,4 %. Se probaron umbral, permisos, CSRF, rollback, concurrencia, privacidad y cerrados; navegador comprobó tercera denuncia, duplicado, logout y tres tamaños sin overflow. Reutilizar resultados del mismo código; no repetir sin cambio o duda nueva.

Instancia propia en `http://127.0.0.1:52657/`, H2 exclusiva en memoria, datos sintéticos; reiniciar elimina ese estado. Preservar servidores ajenos. Falta aceptación humana de interfaz/PWA, instalación y desconexión total, y revisión cruzada de Santiago. Mantener el PR Draft hasta registrar esas validaciones y solicitar revisión; no autoaprobar ni integrar.
