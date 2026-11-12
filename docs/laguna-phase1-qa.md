# RedAuxilio — Laguna, implementación visual (fase 1)

## Decisión de diseño
La propuesta **Laguna** es la referencia de implementación. Las alternativas
**Solar** y **Azul** permanecen en el archivo de Figma, sin eliminarlas.

- Inicio y escritorio Laguna: https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=0-1
- Explorar Laguna: https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=8-648
- Responsive 360/768 Laguna: https://www.figma.com/design/jdOBCS8zKaUs3pvxvUGF1y/RedAuxilio?node-id=136-2955

## Qué se implementó
- **index.html** conserva los identificadores de formularios, filtros, mapa,
  detalle y sesión que ya consumen los módulos existentes; añade la portada,
  navegación y buscador visual.
- **laguna.css** centraliza los colores y componentes de Laguna sin reescribir
  el CSS anterior. Adaptación en escritorio y barra inferior en 1099 px o menos.
- **laguna-ui.js** vincula el buscador nuevo a la búsqueda de `app.js`,
  conecta las tarjetas de Inicio con filtros reales y abre el diálogo de cuenta
  con el mismo controlador usado por escritorio.
- **sw.js** aumenta la versión de la caché del shell. No habilita escrituras
  desconectadas ni almacena respuestas privadas.
- **laguna-ui.test.cjs** cubre funciones puras de navegación/conectividad y
  la conservación de IDs críticos.

## Decisiones de UX
En móvil, **Cuenta aparece exclusivamente en la navegación inferior**;
no debe duplicarse en la cabecera. El indicador En línea/Sin conexión informa
la conectividad del dispositivo, no garantiza que la API esté disponible.

La portada y el buscador usan información comunitaria y los servicios existentes.
No se generan reportes o confirmaciones ficticios adicionales.

## Validación pendiente antes de fusionar
1. Ejecutar CI completa: formato, pruebas JavaScript, Maven y comprobaciones.
2. Inspeccionar 360, 390, 768 y 1366 px: scroll horizontal, mapa y lista.
3. Comprobar Inicio → categoría → Explorar y el filtro aplicado.
4. Probar la búsqueda nueva sin perder el texto ni duplicar solicitudes.
5. Abrir Cuenta desde móvil y escritorio; validar cierre de sesión y datos privados.
6. Probar Internet desconectado, consulta guardada, reconexión y PWA.
7. Probar publicación/borrador, historial y modal de detalle sin regresiones.
8. Verificar teclado, foco visible, zoom 200 % y lectores de pantalla.

## Fuera de alcance de este PR
El rediseño completo de formularios, Detalle, Mis reportes y Moderación
corresponde a siguientes incrementos. La lógica del backend, contratos, permisos,
estados y reglas de privacidad permanece igual.

La rama es de trabajo y el PR debe seguir en borrador hasta concluir estas pruebas.
