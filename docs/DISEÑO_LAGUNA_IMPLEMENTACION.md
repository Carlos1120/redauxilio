# RA-LAGUNA-01 — Inicio, Explorar y navegación responsive

## Decisión de diseño
Laguna es el estilo aprobado para implementar. Solar y Azul permanecen intactos en Figma.

- [Laguna · responsive 360 y 768 px](https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=136-2955)
- [Laguna · formularios y campos mínimos](https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=46-1532)

## Alcance de esta primera entrega
1. CSS Laguna complementario a `app.css`, para conservar el DOM y toda la lógica.
2. Cabecera, semicírculo con sombra, Inicio, mapa, lista y filtros con identidad Laguna.
3. Navegación inferior en anchos <=1099 px: Inicio, Explorar, Publicar y Cuenta. **No duplicar Cuenta arriba en móvil.**
4. El botón Cuenta reutiliza el disparador de autenticación actual; no implementa sesiones nuevas.
5. PWA con nuevo CSS y JS en precaché; se actualiza el color de interfaz.
6. Pruebas estructurales de contrato del DOM y caché.

## Fuera de alcance
- No se modifican controladores, entidades, autorizaciones, ni datos persistentes.
- No se implementan todavía los formularios guiados de tres pasos de 03G.
- No se añaden nuevos estados del backend ni se inventan datos faltantes.
- No se declara validación visual en navegador hasta probar con dispositivo/Playwright.
- Solar y Azul continúan disponibles exclusivamente en Figma, sin eliminaciones.

## Validaciones antes de integrar
- `npm test`, `npm run format:check`, pruebas Java y GitHub Actions.
- Recorridos públicos a 360, 390, 768 y 1366 px. Sin barra de desplazamiento horizontal.
- Inicio, Explorar y mapa + lista; ubicación y filtro; apertura/cierre de cuenta.
- Sin duplicación de Cuenta en móviles. Estado en línea y sin conexión visible; sin falso éxito.
- Navegación por teclado, foco, retroceso, zoom y PWA offline.
- El formulario de publicación existente, fotografías, reportes propios y moderación no deben tener regresiones.

## Próximas iteraciones
Integrar pantalla por pantalla el asistente de publicación de 03G, detalle de publicación,
Mis reportes y las interfaces de operador. Cada fase se valida antes del siguiente merge.
