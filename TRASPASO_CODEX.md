# RedAuxilio — traspaso Laguna

Actualizado: 15 de noviembre de 2026 (Colombia). El estado remoto se debe comprobar de nuevo antes de cualquier publicación o integración. La rama de trabajo es `codex/laguna-fidelidad`, creada localmente desde `origin/codex/laguna-fidelidad` en un worktree aislado. El punto de partida comprobado fue `0b4ab1aed79f24821b1f53983b03d6fc01743c61`. El PR [#37](https://github.com/Carlos1120/redauxilio/pull/37) está abierto como Draft hacia `feat/laguna-shell-home-explore`; el PR #36 lleva esa base hacia `develop`. Ninguno está integrado. No mezclar los PR #34 y #35 sin revisar solapamientos. No atribuir aprobación visual a Carlos ni hacer merge.

## Contrato visual y alcance

Inicio usa los frames oficiales de Figma `4:248` (390×850) y `6:311` (1366×880), archivo `jdOBCS8zKaUs3pvxvUGF1y`. Los frames 09C son auxiliares. Conservar componentes, textos, fuentes SVG y comportamiento responsive de Laguna, junto a búsqueda, filtros, Leaflet, publicación, sesión, privacidad y PWA existentes. `ESTANDARES_INTERFAZ.md` registra la transición; su antigua estética roja/Onest sigue orientando módulos aún no migrados y no sustituye los frames Laguna. Las reglas de accesibilidad y seguridad continúan vigentes.

## Verificación de este bloque

La revisión local de Inicio detectó 15 px perdidos por la barra de desplazamiento clásica a 390 px. Se ocultó únicamente la barra visual móvil sin impedir el desplazamiento, y se recuperó la curva blanca de 21 px debajo del encabezado. En Chrome a 390×850, la tarjeta de acción quedó en `x=20, y=178, ancho=350`, la ilustración en `x=20, y=270, ancho=350` y el apoyo empieza en `y=657`. Se midieron 360, 390, 768, 1099, 1100, 1199, 1200, 1202, 1256, 1280 y 1366 px sin overflow horizontal. El enlace «Refugios» activó el filtro de asistencia y mostró un resultado. Las 11 pruebas JS Laguna y las 158 pruebas JS completas pasaron; Prettier pasó. `mvn verify` con Corretto 21.0.10 terminó correctamente: 205 pruebas Java, cero fallos, errores u omitidas. Java 25 detuvo Spotless por incompatibilidad del formateador, sin fallo de las pruebas de interfaz.

## Siguiente paso

Guardar y compartir las capturas Figma/Chrome de 390×850 y 1366×880, actualizar el mismo PR y comprobar CI del nuevo HEAD. Inicio todavía requiere revisión visual humana. Los frames Laguna de Explorar son `8:559` (móvil) y `8:648` (escritorio). Preparar ese módulo después sin sustituir Leaflet por el mapa ilustrativo de Figma; su fidelidad y pruebas permanecen pendientes.
