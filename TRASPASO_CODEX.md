# RedAuxilio — traspaso de trabajo

Actualizado: 18 de noviembre de 2026 (Colombia). Repositorio: `C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio`. `origin` apunta a `Carlos1120/redauxilio`. La rama actual `codex/fix-live-header` parte del `develop` remoto verificado (`4075cc7`). El Web Service de Render aparece como **Live** según Santiago; queda comprobar la versión pública después de integrar la corrección visual.

## Estado de producto

La app es un monolito Spring Boot/Thymeleaf con interfaz Laguna y PostgreSQL administrado en Render. La PWA sirve Inicio, Explorar, publicación, Mis reportes, Encontrar personas, Cuenta y paneles privados de auditoría/verificación. Los paneles dependen de permisos consultados al servidor; las credenciales de DB se configuran solo en Render y nunca se registran en el repo.

Inicio usa como referencia Figma los frames `4:248` (móvil) y `6:311` (escritorio). La cabecera debe mantener el mismo logo Laguna y la navegación apropiada en todos los módulos; por debajo de 1200 px usa la barra inferior. La sección Encontrar personas compara imágenes en memoria contra casos preparados y no persiste las cargas. El catálogo público incluye nueve reportes de demostración georreferenciados en el perímetro urbano de Villavicencio.

## Cambio activo

Las capturas del 18 de noviembre muestran la marca RedAuxilio duplicada, acciones desbordadas y el texto “En línea” bajo el botón Cuenta. Se localizó duplicación real en `src/main/resources/templates/index.html`: dos marcas, tres grupos de navegación y tres accesos de publicación compartían `.header-content`. El PR #43 consolida una marca, navegación con destinos de Encontrar personas y accesos de rol, una acción de publicación, el buscador y Cuenta. El CSS corrige la reactivación del botón entre 1100–1199 px y reduce el estado de conexión a un indicador compacto entre 1200–1499 px. Los reportes permanecen separados de Inicio: se consultan en Explorar. En local, la consulta de Explorar devuelve 13 reportes; se confirmó HTTP 200. El parche está en la rama del PR #43; Render despliega desde `develop`, así que no estará en línea hasta integrar. No cambia backend ni permisos.

## Verificaciones y siguiente paso

`git diff --check` pasó y Prettier confirmó los archivos modificados sin reformateo adicional. El servicio local de esta rama responde HTTP 200 en `http://localhost:8080`, con H2 en memoria y geocodificación externa desactivada; los datos se pierden al reiniciar. No se ejecutaron suites de pruebas, conforme a la preferencia de Santiago de evitar pruebas repetidas. El PR #43 sigue abierto, sin estado CI reportado en su HEAD anterior. La CI debe revisar el nuevo HEAD; no inventar aprobación manual. Después de integrar a `develop`, Render debe desplegarlo y se debe comprobar la URL pública en escritorio y móvil, junto con rutas de Inicio, Explorar, Encontrar personas, Mis reportes y accesos según rol.

## Riesgos pendientes

El despliegue está en proceso de configuración y no se ha validado desde este traspaso con una petición HTTP pública. Las credenciales PostgreSQL fueron expuestas en logs y mensajes anteriores; confirmar en Render que las comprometidas fueron rotadas y que `DB_URL` contiene solo la URL JDBC interna, con usuario y contraseña separados. No registrar ni solicitar secretos por chat. Si el nuevo log vuelve a fallar, revisar primero el mensaje `Caused by` más profundo y no copiar valores de conexión.
