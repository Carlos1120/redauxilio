# Sistema de interfaz de RedAuxilio

## Intención

Persona que consulta reportes comunitarios desde un celular, posiblemente con conectividad limitada. Encontrar la categoría, entender qué se sabe y distinguir información almacenada de una consulta actual. Sensación: calma, legibilidad y cautela.

## Exploración

Dominio: refugio, punto de encuentro, rutas, reporte comunitario, confianza, vigencia, borrador y conexión. Colores del entorno: azul de señalización, blanco de información, ámbar de precaución, verde de conexión y gris de infraestructura. Azul es el único acento de acciones; los otros colores comunican estados con texto.
Firma: ficha de reporte con categoría, estado operativo, confianza y fecha como información separada. Aparece en etiqueta, título, pares de estado/confianza, fecha y aviso de fuente de consulta.
Evitar: métricas decorativas (sustituidas por información útil), hero de marketing (cabecera breve), falsa disponibilidad de mapas/publicación (solo funciones existentes).

## Dirección y decisiones

Tema claro, consulta dominante y borrador secundario. Tipografía Segoe UI y alternativas locales para lectura inmediata sin conexiones externas. Escala 4/8/12/16/24/32/48 px. Profundidad por superficies de la misma familia, sin sombras decorativas. Formularios con controles nativos estilizados por accesibilidad y compatibilidad; no se añade una biblioteca.

## Tokens

Texto principal #172c3c; secundario #42576a; metadatos #526477. Acción #145777 sobre fondo #ffffff; acción hover #103e57. Superficie #f5f8fa; tarjeta #ffffff; campo #f1f5f7. Aviso #80500a sobre #fff4db; conexión #246342 sobre #eaf5ed. Foco #145777 con anillo de 3 px. Radios de 8 y 12 px. Objetivos interactivos >=44 px.

## Patrones

Cabecera con marca y estado de red, dos enlaces de sección; no menús innecesarios. Selector de categoría + actualizar consulta. Reportes en dos columnas en escritorio y una en móvil. Estado y confianza mediante lista descriptiva; fecha con time. Borrador agrupado y campos de coordenadas separados. Guardar y descartar son acciones distintas. Estados de consulta: cargando, vacía, error, servidor y caché fechada. Texto sin conexión no afirma que existan datos almacenados.

## Verificación

Prettier, pruebas existentes y sintaxis JS; contraste calculado con WCAG 2.x. Revisión visual y pruebas de teclado/PWA deben documentarse sin presumir certificación WCAG. Revisión humana final pendiente. No introduce datos reales ni cambia los requisitos del proyecto.

## Fuente e IA

Aplicada ui-ux-design-pro del ZIP suministrado por Carlos, con sus 12 referencias. Codex elaboró e implementó esta adaptación. Se conserva la guía de estándares aprobada.
