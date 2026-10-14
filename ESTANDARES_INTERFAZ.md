# Estándares de interfaz de RedAuxilio

Versión 1.0 · 6 de octubre de 2026. Dirección visual solicitada por Santiago Ortiz Ochoa y aplicada en el PR #8. Esta guía define cómo continuar ese diseño; no registra aprobación de Carlos ni sustituye ESTANDARES.md, los requisitos o las pruebas de aceptación.

## 1. Objetivo y fuente de referencia

Cada pantalla debe sentirse parte de la misma aplicación: tipografía Onest, rojo de marca, superficies blancas sobre fondo cálido, información legible y acciones claras. La consulta geográfica usa mapa desaturado como fondo y paneles flotantes. Las pantallas sin mapa conservan los mismos componentes y usan el fondo cálido; no necesitan añadir un mapa decorativo.

La referencia implementada está en `src/main/resources/templates/index.html` y `src/main/resources/static/app.css`. El contrato funcional está en RA02.md. Reutilizar estos componentes antes de crear variantes. La interfaz anterior azul queda como antecedente; no mezclar ambas direcciones en pantallas nuevas.

## 2. Colores y tokens compartidos

Usar variables CSS de `app.css` en componentes nuevos. No crear una paleta propia por pantalla ni copiar colores dispersos en estilos en línea.

| Uso                                | Valor                 | Variable existente                                      |
| ---------------------------------- | --------------------- | ------------------------------------------------------- |
| Marca y acción principal           | `#d7262e`             | `--color-relief-blue`                                   |
| Hover de acción principal          | `#a3151c`             | `--color-relief-blue-hover`                             |
| Fondo general y bloques interiores | `#f5f1f1`             | `--color-information-ground`, `--color-report-inset`    |
| Paneles y campos de información    | `#ffffff`             | Usar superficie blanca del componente existente         |
| Texto principal                    | `#1c1414`             | `--color-report-ink`                                    |
| Texto secundario                   | `#5a4e4e`             | `--color-report-secondary`                              |
| Metadatos legibles                 | `#6f6060`             | `--color-report-tertiary`                               |
| Bordes                             | `#d8cdcd` / `#e9dede` | `--color-report-border`, `--color-report-border-subtle` |
| Borde de controles                 | `#c9bcbc`             | `--color-control-border`                                |
| Foco                               | `#d7262e`             | `--color-focus`                                         |

Los nombres históricos `relief-blue` contienen ahora el rojo. Conservarlos al reutilizar el código; un cambio de nombre debe hacerse en todas sus referencias mediante una tarea coherente.

El rojo identifica marca, selección y acción; no significa automáticamente peligro. Estado operativo, confianza y conexión siempre tienen texto o un nombre accesible. El verde del indicador de conexión no acredita disponibilidad del servidor ni verificación de reportes. Para texto normal nuevo exigir contraste mínimo 4.5:1 y para texto grande 3:1. El gris de metadatos es una referencia visual existente, no una autorización para usar texto pequeño de bajo contraste; comprobar cada combinación y oscurecerla cuando haga falta.

## 3. Tipografía, espacio y superficies

- Fuente: `var(--font-reading)`, definida como `"Onest", "Segoe UI", sans-serif`. Onest se sirve localmente desde `static/vendor/onest`; conservar su licencia. No importar otra fuente desde un CDN.
- Pesos: 400 para texto, 600–650 para controles, 700–800 para títulos y énfasis. Reservar mayúsculas para categoría y etiquetas breves; evitar párrafos en mayúsculas.
- Escala de referencia: título principal 22–24 px, título de sección 18–20 px, texto/formulario 14–16 px. La consulta compacta existente usa tamaños menores para categorías y metadatos; no extenderlos a instrucciones esenciales o formularios nuevos.
- Espaciado: escala 4, 8, 12, 16, 24, 32 y 48 px. Usar 16–24 px dentro de paneles y 8–16 px entre elementos relacionados. Mantener agrupaciones y alineaciones consistentes.
- Radio de control: `var(--radius-control)` = 10 px. Radio de panel/ficha: `var(--radius-report)` = 16 px. Reservar forma de píldora para búsqueda, navegación y filtros rápidos.
- Profundidad: bordes suaves y sombras discretas de tinta cálida. Reutilizar las sombras de paneles existentes; no añadir degradados, brillos ni sombras intensas como decoración.

## 4. Componentes que deben reutilizarse

| Componente        | Regla de continuidad                                                                                                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Cabecera          | Marca roja, búsqueda cuando corresponda, navegación corta, una acción principal y estado de conexión. Mantener `.site-header`, `.header-content`, `.brand` y `.header-action`.       |
| Acción principal  | Fondo rojo, texto blanco, hover rojo oscuro y nombre que explique la acción. Preferir una acción principal por grupo.                                                                |
| Acción secundaria | Usar `.secondary-button`; no competir visualmente con la acción principal. Descartar y guardar son acciones separadas.                                                               |
| Filtros           | Reutilizar `.quick-filters` y el formulario avanzado. La selección rápida refleja el selector real y usa `aria-pressed`; mostrar etiquetas visibles en los campos.                   |
| Ficha de reporte  | Categoría, título, ubicación/resumen, estado operativo y acceso al detalle. Selección con borde y señal textual/accesible. El mismo objeto de la API alimenta lista, mapa y detalle. |
| Detalle           | Reutilizar `#report-detail`, `.detail-top`, `.detail-description` y `.detail-fact`. Separar estado operativo, confianza, coordenadas y fechas. Cierre visible y accesible.           |
| Formularios       | Reutilizar `.field` y controles nativos. Etiqueta visible, ayuda asociada y error específico. Campos relacionados se agrupan con `fieldset` y `legend`.                              |
| Borrador          | Reutilizar `.draft-section`. Explicar que se guarda localmente y que guardar no publica. Mostrar recuperación y fallo de almacenamiento.                                             |
| Avisos            | Mensaje corto con causa y siguiente acción. Mantener estados cargando, vacío, error, datos del servidor y caché fechada. Usar regiones de estado existentes para cambios dinámicos.  |

Los nuevos componentes deben definirse en el CSS compartido con nombres en inglés y comentarios de propósito en español. No duplicar un componente con estilos casi iguales ni añadir una biblioteca visual que sustituya la arquitectura actual.

## 5. Mapa y significado de los reportes

El fondo cartográfico queda desaturado para que fichas y marcadores dominen. Aplicar el filtro visual solo a las teselas, nunca a controles, marcadores o atribución. Mantener la atribución de Leaflet/OpenStreetMap visible y los controles de zoom y restablecimiento accesibles.

| Categoría            | Símbolo y aspecto actual                                           |
| -------------------- | ------------------------------------------------------------------ |
| Punto de asistencia  | `⌂`, cuadrado oscuro con esquinas redondeadas                      |
| Vía afectada         | `↔`, círculo blanco con borde y texto rojos                        |
| Persona desaparecida | `?`, círculo rojo                                                  |
| Solicitud de ayuda   | `!`, círculo rojo                                                  |
| Grupo                | Número de reportes en círculo rojo; permite consultar sus miembros |

Conservar leyenda y nombres accesibles: el color o el símbolo no bastan. Si falla la cartografía, la lista y el detalle siguen utilizables. El aviso conserva los fallos por tesela hasta su recuperación o retirada. No convertir el mapa de demostración en una afirmación de ubicación real.

## 6. Comportamiento adaptable

- Desde 1100 px: lista flotante izquierda y detalle lateral derecho sobre el mapa. Los paneles desplazan su propio contenido; no cubren la atribución ni las acciones del mapa.
- De 761 a 1099 px: cabecera adaptable, lista sobre mapa y detalle modal centrado. Los campos y selectores caben completos.
- Hasta 760 px: cabecera reorganizada, mapa antes de lista, controles y fichas apilados y detalle modal. El borrador queda debajo. Evitar alturas fijas en texto y formularios.
- Verificar 360×800, 768×1024 y 1366×768 con títulos largos, mensajes de error, filtros desplegados y detalle abierto. No debe aparecer desplazamiento horizontal de toda la página ni recortarse contenido esencial.

Una futura aplicación móvil nativa reutilizará estos colores, tipografía, jerarquía y significado de componentes; sus interacciones requieren diseño y pruebas propios. Esta guía no declara implementada esa aplicación.

## 7. Accesibilidad y estados obligatorios

Usar HTML semántico, enlaces para navegación y botones para acciones. Mantener enlace de salto, encabezados ordenados, etiquetas y foco visible. Controles nuevos con área interactiva de al menos 44×44 px; las dimensiones compactas existentes no eximen esta regla. Las excepciones actuales requieren validación o corrección, no copiarse por rutina.

El detalle modal se abre con teclado, permite cerrar con Escape y devuelve foco al control de origen o a una sección válida si ese control desapareció. No trasladar el foco al actualizar búsqueda o filtros sin necesidad. Respetar `prefers-reduced-motion`; no exigir animaciones para entender cambios.

Mostrar estados vacíos y errores sin inventar contenido. Una caché muestra fecha y advertencia de posible desactualización. Tener conexión de red no garantiza éxito de consulta. No ocultar avisos críticos solo para parecerse a una imagen.

## 8. Límites de contenido y arquitectura

Mantener Spring Boot, Thymeleaf, JavaScript/CSS propios, Leaflet, manifiesto y service worker. La presentación no cambia contratos, reglas de confianza, permisos, visibilidad ni almacenamiento. No crear porcentajes de ocupación, cifras, rutas, alertas, aprobaciones oficiales o acciones que el servidor no soporte.

La franja roja actual identifica datos ficticios y no anuncia un sismo activo. Nunca mostrar «publicado» por guardar un borrador. Conservar la distinción entre estado operativo y confianza. Textos en español claro; evitar terminología interna del código en los mensajes para visitantes.

## 9. Lista de revisión para cada cambio de interfaz

### Ajuste local de legibilidad — 8 de octubre de 2026

Propuesta B de Open Design con ajustes de A, solicitada por Santiago para revisión visual;
pendiente de revisión del compañero e integración. Conserva la dirección Onest/rojo/mapa.
El panel de consulta usa 376 px en escritorio/tableta y márgenes interiores de 12 px;
las fichas usan radio de panel, relleno de 12 px, títulos de 18 px y metadatos de 12 px.
Categoría aparece en una insignia roja suave; estado operativo y confianza se agrupan
en superficies cálidas paralelas en escritorio y apiladas en móvil. La selección rápida
usa el rojo de marca. Las fichas muestran fecha de actualización desde la misma API.
El token terciario se oscurece a `#6f6060`; no usar el gris anterior en texto esencial.
Filtros, acceso al detalle, zoom y cierres tienen un área de al menos 44×44 px.
En móvil se conserva el mapa antes de la lista y el detalle modal. La caché v10 renueva
los recursos de esta presentación; no modifica almacenamiento de borradores ni autenticación.

### Comprobaciones

- [ ] Reutiliza fuente, tokens y componentes; no introduce otra paleta o biblioteca.
- [ ] Conserva jerarquía, significado de estados y contrato funcional.
- [ ] Muestra cargando, vacío, error y datos guardados cuando corresponden.
- [ ] Funciona en los tres tamaños, con contenido largo y sin recortes.
- [ ] Permite teclado, foco visible, cierre y retorno de foco; contraste y áreas táctiles comprobados.
- [ ] Ejecuta Prettier y las pruebas pertinentes al comportamiento afectado.
- [ ] Si cambia recursos PWA, verifica recarga, instalación y desconexión/reconexión sobre la versión final.
- [ ] El PR enlaza resultados, versión y responsable real; el compañero revisa antes de integrar.

Cambiar la dirección visual global requiere actualizar esta guía, el CSS compartido y las pantallas afectadas en el mismo PR, con revisión del compañero. No se declara aceptación del equipo únicamente por editar este documento.
