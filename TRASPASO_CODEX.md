# RedAuxilio — traspaso visual RA-08

Actualización: 2 de noviembre de 2026 (Colombia). Base verificada `origin/develop` `0d270cb742627bde69891ead2938e27eca37f23c`, CI verify aprobado al iniciar. Rama propia `codex/RA-08-dashboard-operador`, worktree aislado `C:\Users\cmps-\.codex\worktrees\ra08-dashboard\redauxilio`. Consultar la última sección de RA08.md y EVIDENCIAS.md; no reconstruir historias anteriores. No modificar ramas, servidores o bases ajenos; no hacer merge, force push ni cambios de Trello.

## Incremento visual

El dashboard carga moderation.css después de app.css, conservando Onest, rojo y componentes compartidos. Revisión manual compacta, cola lateral y contexto principal en escritorio; una columna en tableta/móvil. Las tarjetas identifican ID, categoría y las tres dimensiones sin mezclarlas ni presentar métricas inventadas. La cola conserva paginación y selección accesible. El contexto agrupa descripción, ubicación, fechas y estado; las acciones distinguen mantener/restituir de ocultar/cerrar.

Ocultar y cerrar requieren confirmación integrada con cancelar/Escape y retorno de foco. No usa diálogo nativo ni altera reglas del servidor. KEEP sin denuncias dice «Finalizar revisión y mantener publicación». Historial en línea de tiempo con diferencias conocidas antes/después; snapshots originales plegados como detalle técnico, siempre texto. No se cambian Java, SQL, API, roles, CSRF ni transacciones. La UI mantiene no-store, limpieza privada, ausencia de persistencia local y descarte de respuestas tardías, incluido el refresco posterior a decidir. El service worker no incorpora recursos de operador.

## Verificación y siguiente paso

Pruebas específicas de moderación JS: 19 aprobadas. Verificación completa final y entorno en EVIDENCIAS.md; consultar CI del HEAD publicado, sin extrapolar resultados a otros commits. La instancia ficticia propia usa puerto 59759 y H2 exclusiva en el directorio temporal redauxilio-operator-dashboard; credenciales solo en su instance.json, nunca en Git. Allí están las capturas locales de escritorio, tableta, móvil e historial.

Herramientas comprobaron los tres tamaños, foco/Tab, confirmación/cancelación/Escape, historial plegado, KEEP, envío manual, logout y error por interrupción del servidor sin éxito ficticio. Offline total del navegador queda pendiente de aceptación humana; eventos offline/online tienen pruebas JS. No se afirma auditoría WCAG ni aceptación de Carlos/Santiago. Mantener PR Draft independiente hacia develop. Siguiente acción: comprobar CI y pedir revisión visual humana de la instancia y revisión cruzada, antes de cualquier integración autorizada.
