# RA-LAGUNA-01 — Inicio, Explorar y navegación responsive

## Decisión de diseño

Laguna es el estilo aprobado para implementar. Solar y Azul permanecen intactos en Figma.

- [Inicio original · móvil 390 px](https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=4-248)
- [Inicio original · escritorio 1366 px](https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=6-311)
- [Laguna · formularios y campos mínimos](https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=46-1532)

Los frames 09C de 360 y 768 px son auxiliares y no definen Inicio. Estos anchos adaptan los componentes de los dos originales.

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
- La comparación en Chrome de Inicio no sustituye la aceptación humana ni las pruebas de otros módulos.
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

## Fidelidad de Inicio en PR #37

Inicio usa los SVG y fuentes originales de 4:248 y 6:311 guardados en `static/laguna-assets/`. En 360 y 768 px conserva la tarjeta blanca para reportar, la ilustración, las cuatro categorías ilustradas y la tarjeta de apoyo. Ajusta dimensiones y espacios sin adoptar los botones de texto ni la barra verde de 09C. Los accesos siguen activando los filtros públicos.

Las familias Outfit, Atkinson Hyperlegible y Bricolage Grotesque se sirven localmente desde Google Fonts. Sus licencias SIL OFL originales se conservan junto a los archivos como `OFL-Outfit.txt`, `OFL-Atkinson.txt` y `OFL-Bricolage.txt` ([repositorio de fuentes](https://github.com/google/fonts/tree/main/ofl)).

Chrome real se comparó con los PNG oficiales a 390×850 y 1366×880 mediante superposición y mapa de diferencia. Diferencia media por canal: 8,19/255 y 5,36/255, respectivamente. Capturas y comparaciones: `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelity-qa/figma/official-final-{360,390,768,1366}.png` y `compare-official-{390,1366}-{overlay,diff}.png`; no están versionadas. No hubo desplazamiento horizontal a 360, 390 ni 768 px; a 1366 px el contenido mide 1351 px por la barra vertical de Chrome. Persisten diferencias de rasterizado tipográfico, barra vertical y curva blanca del lienzo móvil. La revisión cruzada y aceptación humana siguen pendientes; las tarjetas de ejemplo se identifican como ficticias.
