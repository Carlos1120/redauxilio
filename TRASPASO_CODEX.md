# RedAuxilio — continuidad Laguna

Actualización: 12 de noviembre de 2026 (Colombia). Worktree aislado `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelidad`, rama `codex/laguna-fidelidad`, PR #37 Draft hacia `feat/laguna-shell-home-explore` (PR #36). El último HEAD publicado antes de esta corrección es `bd56c76c6197f54b869a8b6195d83ad603208561`; verificar el HEAD remoto antes de publicar. No tocar otras ramas, worktrees, servidores ni bases; no hacer merge ni atribuir aceptación humana.

## Módulo y contrato visual

Inicio es el único módulo activo. Las referencias oficiales son Figma `4:248` (390×850) y `6:311` (1366×880), archivo `jdOBCS8zKaUs3pvxvUGF1y`. Los frames 09C de 360 y 768 px son auxiliares, nunca fuentes de composición definitiva. Mantener originales SVG, fuentes, textos, tarjetas, ilustraciones, iconos y jerarquía. Los demás módulos, incluido Explorar, esperan la validación de Inicio. Solar y Azul permanecen intactos en Figma.

## Corrección y evidencia vigente

La auditoría del PR #37 encontró Cuenta recortada a 1202 px, navegación superior con fondos y doble subrayado, una esquina cuadrada sobre el fondo Laguna y texto/escudo faltantes en escritorio. Se sustituyó el posicionamiento absoluto de la cabecera por distribución flexible desde 1200 px; de 1100 a 1199 px se usa la navegación inferior. El estado activo muestra una sola línea blanca, el hover conserva texto blanco sin fondo y el foco visible permanece. La tarjeta deja ver sus cuatro radios de 18 px. Se restauraron el texto original de escritorio y el escudo local de la cápsula. No se cambiaron servicios, roles, datos ni reglas de negocio.

Instancia QA: `http://127.0.0.1:61384/`, H2 aislada en `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelity-qa/data/fidelity`. El JAR sirve el árbol local del bloque; no confundirlo con un commit publicado hasta verificarlo. Capturas y scripts de medición están fuera de Git en `C:/Users/cmps-/RedAuxilio-recovery/laguna-fidelity-qa/figma/`. Los límites de logo y navegación pasaron a 1099, 1100, 1199, 1200, 1202, 1256, 1280 y 1366 px: sin recortes, intersecciones ni overflow. A 1202 px, Cuenta termina en x=1147 y el área visible en x=1187. La esquina que era blanca ahora coincide con el verde del Figma. Chrome comprobó hover, selección y foco por teclado. Comparación visual: diferencia media 8,17/255 a 390 px y 4,93/255 a 1366 px; 768 px conservó su composición.

Pruebas JS dirigidas 11/11, Prettier y `git diff --check` aprobados. El CI anterior de `bd56c76` fue SUCCESS; consultar GitHub Actions para el HEAD actual del PR #37. No repetir suites Java/Node completas por esta corrección visual.

## Siguiente acción

Comprobar CI del HEAD publicado y revisar con Carlos las capturas de 1202 y 1366 px. Mantener PR Draft y no pasar a Explorar antes de su revisión visual de Inicio.
