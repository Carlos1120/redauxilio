# RedAuxilio — traspaso RF-16 dentro de RA-08

Actualización: 2 de noviembre de 2026 (Colombia). Base remota verificada: `origin/develop` `b580c6cdc9eee39bac4e3da12a96d60903f99751`. Trabajo en `codex/RA-08-revision-manual`, worktree aislado `C:\Users\cmps-\.codex\worktrees\ra08-revision-manual\redauxilio`. Consultar RA08.md y la última evidencia RF-16; no reconstruir las historias anteriores. Los worktrees, servidores y datos de otras validaciones permanecen intactos. No se modifica la rama de Santiago ni Trello; no hacer merge ni force push.

## Incremento

RF-16 permite enviar manualmente una publicación ciudadana a EN_REVISION con ID y motivo obligatorio de 1–500 caracteres. Usa la acción REVIEW del endpoint privado existente, no una nueva ruta de permisos. Reutiliza sesión, ROLE_OPERATOR persistente, CSRF, bloqueo de publicación y transacción con auditoría. La única columna modificada de publicación es review_status: no actualiza fechas, confianza, confirmaciones, visibilidad, estado operativo ni denuncias. Admite ocultos/cerrados sin restituirlos ni reabrirlos. IDs inexistentes, ejemplos y solicitudes inválidas se rechazan; el duplicado responde 409 sin otro evento, también en concurrencia.

La cola existente admite cero denuncias. El formulario de `/operator` actualiza la cola y abre el contexto del ID confirmado, incluso si pertenece a otra página. Solo comunica éxito tras respuesta del servidor, renueva CSRF y descarta respuestas antiguas cuando cambia sesión/red/visibilidad. Los campos se limpian junto al contexto privado. No usa almacenamiento local. La consulta de contexto reconoce decisiones previas para preservar acceso al historial después de KEEP sin denuncias. Auditoría privada separada del historial del autor y de las respuestas públicas.

## Validación y siguiente acción

Las regresiones iniciales reprodujeron REVIEW desconocida con HTTP 400. Pasan cinco pruebas Java nuevas y 15 pruebas específicas de moderación JavaScript, incluidas cuatro nuevas. Verificación completa final, entorno y resultados en EVIDENCIAS.md; el CI debe comprobarse sobre el HEAD publicado, sin atribuir resultados de otra versión.

Crear un único commit coherente y PR Draft hacia develop después de comprobar Maven/Node/formato. Pendientes: validación humana del formulario, cola sin denuncias, motivo, duplicados e historial tras KEEP; revisar tamaños responsive y desconexión. Las pruebas H2 no certifican concurrencia PostgreSQL. No se declara RA-08 terminada ni aprobación de Carlos/Santiago. Siguiente paso: revisar CI exacto y realizar aceptación humana antes de solicitar revisión cruzada e integración autorizada.
