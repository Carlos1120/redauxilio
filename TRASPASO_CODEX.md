# RedAuxilio — traspaso de trabajo

Actualizado: 23 de noviembre de 2026 (Colombia). Rama `codex/fix-match-download`, basada en `origin/develop` `c9104a2`. PR nuevo pendiente. Respaldo aislado en `codex/backup-redauxilio-2026-11-18` y PR #43. No atribuir revisión humana o CI hasta comprobarlos sobre el HEAD final.

## Estado de producto

Spring Boot sirve Thymeleaf, JavaScript y CSS desde un mismo servicio. Render usa PostgreSQL; Vercel no está configurado. La copia local está en `http://localhost:8091/` con H2 persistente `./data/redauxilio_preview.mv.db`; sus cuentas y reportes son distintos de los de Render.

Inicio, Explorar, Publicar, Mis reportes, Cuenta, auditoría, verificación y Encontrar personas conservan sus rutas. Los permisos de auditor y verificador permanecen separados. La cabecera y navegación móvil son compartidas. La comparación de personas usa dos casos preparados y un resultado predeterminado, sin reconocimiento facial real. El servicio worker sirve recursos públicos y excluye datos privados.

## Incrementos Laguna

Se implementaron los frames Laguna `30:389` (390×844) y `30:439` (1366×880). La página antigua de formulario inmediato pasó a una lista de tarjetas con filtros Todos/Activos/Borradores, pictogramas SVG originales y panel lateral en escritorio. Las tarjetas usan únicamente publicaciones propias de `/api/my/publications`; el borrador procede de IndexedDB. Las acciones abren los formularios existentes de edición, cierre/reapertura, contacto e historial. El servidor mantiene autorización, CSRF y auditoría.

El acceso ciudadano local se comprobó en navegador con cuenta de prueba. La base H2 local contiene `auditor.demo@lagunaviva.local` con permiso `ROLE_OPERATOR`: se restableció su clave solo en esa base y se verificó el ingreso al panel `/operator` en 8091. La cuenta de Render no fue verificada. La vista vacía se observó a 390 y 1366 px; las tarjetas con datos se cubren mediante prueba JS dirigida, pero falta captura visual de una cuenta con publicaciones reales. La barra inferior de auditor ahora acomoda los seis destinos en una fila.

Explorar usa directamente Leaflet en lugar de la ilustración SVG y del botón para alternar vistas. La lista de reportes y sus filtros permanecen. El área geográfica solo filtra después de un movimiento explícito del usuario, para conservar la consulta inicial de todos los reportes. Cuenta muestra texto visible en la cabecera pública y del auditor: antes el icono quedaba oculto en escritorio y dejaba un botón blanco. La ventana de acceso se comprobó desde Explorar en navegador, igual que el mapa interactivo en móvil y escritorio. La PWA se renueva a caché v49.

## Verificación y siguiente paso

`origin/develop` está en `c9104a2` con PR #45 y #46 integrados. En la rama `codex/fix-match-download`, PR #47, la comparación admite la huella saneada y original de archivos válidos; acepta la foto descargada JPEG y referencias preparadas. La descarga ya conserva la extensión PNG/JPEG/WebP. La prueba dirigida, Maven `verify`, 189 pruebas Node y formato aprobaron antes del último ajuste de extensión; repetir verificación en CI sobre HEAD final. Render todavía usa la versión anterior. Siguiente: revisión, integración, despliegue y comprobación desde navegador. Render y H2 local no sincronizan datos. No subir credenciales.
