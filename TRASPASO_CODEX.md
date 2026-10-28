# RedAuxilio — traspaso vigente

Actualización: 27 de octubre de 2026 (Colombia). Resumen operativo para retomar; no sustituye GitHub ni el estado del árbol. Reglas en AGENTS.md/ESTANDARES.md, contrato funcional en RA05.md y resultados extensos en la última sección RA-05 de EVIDENCIAS.md. No cargar el historial completo por rutina.

## Rama y estado

Historia activa RA-05, confirmación independiente, en `feat/RA-05-confirmacion-independiente`. Base integrada RA-03/develop `2abc847`; se conservó el documental `94d30bc` como ancestro, ya existente en remoto. El HEAD identifica los commits de núcleo y cliente/evidencias. La entrega se prepara en PR Draft hacia develop; verificar su URL, HEAD y CI publicados en GitHub antes de asumir que están aprobados. No hay merge, aprobación humana ni cambios de Trello.

Worktree propio: `C:\Users\cmps-\.codex\worktrees\ra05-confirmacion\redauxilio`. Se preservaron RA-04, la copia de validación RA-03 y el repositorio original RA-01, sin descartar ni trasladar cambios. Comprobar status/remotos antes de continuar. Mantener commits lógicos y la identidad Git configurada por Carlos; no atribuir responsabilidad del proyecto al agente.

## Núcleo implementado y límites

Confirmaciones persistentes por publicación/versión/cuenta; cuota móvil de 30 aceptaciones por hora; autor excluido, una activa por cuenta y umbral de tres solo para confianza comunitaria. Carlos confirmó ocultos 404, cerrados 409 y reconfirmación después de invalidar, conservando historia y cupo. POST requiere sesión, CSRF y versión observada; rechaza versiones obsoletas. Se serializan cuenta/publicación y se conserva auditoría de invalidación. No expone correos/IDs de confirmadores ni concede verificación oficial.

La UI reutiliza el detalle, deshabilita sin sesión/red y no guarda ni reintenta escrituras. SW v15 precarga el script y excluye POST. El hook interno `invalidateForSubstantialChange` está probado aisladamente; **no** equivale a integración automática con RA-04. No copiar ese PR pendiente.

## Evidencias y siguiente paso

Verificación local: 55 Java (9 específicas), Spotless/PMD, 39 JS, Prettier y diff --check aprobados. JaCoCo del servicio/controlador y records de confirmación: 82/82 líneas. Navegador: aceptación 2→3, duplicado, visitante/logout y tres tamaños sin overflow. No repetir suites del mismo código sin incertidumbre o cambio nuevo. La CI debe corresponder al HEAD exacto del Draft.

Instancia propia en `http://127.0.0.1:61213/`, H2 exclusiva en memoria; reiniciar elimina datos sintéticos. Mantenerla para revisión manual y preservar servidores ajenos.

Siguiente paso: revisar el núcleo Draft con Santiago, completar validación humana de PWA/desconexión y, cuando RA-04 se integre, conectar invalidación en la misma transacción de edición/transición y probar regresión conjunta. RA-05 no cumple DoD ni está lista para merge antes de esa integración y aprobación.
