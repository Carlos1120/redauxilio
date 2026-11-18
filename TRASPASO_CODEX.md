# RedAuxilio — traspaso de trabajo

Actualizado: 18 de noviembre de 2026 (Colombia). Repositorio: `C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio`. `origin` apunta a `Carlos1120/redauxilio`. La rama actual `codex/fix-live-header` parte del `develop` remoto verificado (`4075cc7`). El Web Service de Render aparece como **Live** según Santiago; queda comprobar la versión pública después de integrar la corrección visual.

## Estado de producto

La app es un monolito Spring Boot/Thymeleaf con interfaz Laguna y PostgreSQL administrado en Render. La PWA sirve Inicio, Explorar, publicación, Mis reportes, Encontrar personas, Cuenta y paneles privados de auditoría/verificación. Los paneles dependen de permisos consultados al servidor; las credenciales de DB se configuran solo en Render y nunca se registran en el repo.

Inicio usa como referencia Figma los frames `4:248` (móvil) y `6:311` (escritorio). La cabecera debe mantener el mismo logo Laguna y la navegación apropiada en todos los módulos; por debajo de 1200 px usa la barra inferior. La sección Encontrar personas compara imágenes en memoria contra casos preparados y no persiste las cargas. El catálogo público incluye nueve reportes de demostración georreferenciados en el perímetro urbano de Villavicencio.

## Cambio activo

La captura enviada el 18 de noviembre muestra la marca RedAuxilio duplicada, con la cruz roja anterior junto al wordmark Laguna, y navegación/acciones repetidas que desbordan la cabecera de escritorio. Se localizó duplicación real en `src/main/resources/templates/index.html`: dos marcas, tres grupos de navegación y tres accesos de publicación compartían `.header-content`. El trabajo en curso consolida una marca, una navegación con destinos de Encontrar personas y accesos de rol, una acción de publicación, el buscador y el control de cuenta. Los scripts existentes conservan los IDs de Auditorías, Verificaciones y publicación. No cambia backend ni permisos.

## Verificaciones y siguiente paso

En el cambio actual, `git diff --check` pasó y Prettier confirmó `index.html` sin reformateo adicional. No se ejecutaron suites de pruebas, conforme a la preferencia de Santiago de evitar pruebas repetidas. Antes de abrir/integrar el PR, inspeccionar el diff final y confirmar que la navegación contextual y el diseño responsive permanecen. La CI del PR debe revisar el HEAD exacto; no inventar aprobación manual. Después de integrar a `develop`, Render debe desplegarlo y se debe comprobar la URL pública en escritorio y móvil, junto con rutas de Inicio, Explorar, Encontrar personas, Mis reportes y los accesos según rol.

## Riesgos pendientes

El despliegue está en proceso de configuración y no se ha validado desde este traspaso con una petición HTTP pública. Las credenciales PostgreSQL fueron expuestas en logs y mensajes anteriores; confirmar en Render que las comprometidas fueron rotadas y que `DB_URL` contiene solo la URL JDBC interna, con usuario y contraseña separados. No registrar ni solicitar secretos por chat. Si el nuevo log vuelve a fallar, revisar primero el mensaje `Caused by` más profundo y no copiar valores de conexión.
