# RedAuxilio — traspaso de trabajo

Actualizado: 19 de noviembre de 2026 (Colombia). Carpeta: C:\Users\Santiago\.codex\worktrees\laguna-fidelidad\redauxilio. Rama `codex/render-live-work`, PR borrador #44 hacia `develop`. La copia de respaldo sigue aislada en `codex/backup-redauxilio-2026-11-18` y PR #43; no modificarla. No se requiere autorización de Carlos para revisar, pero no fusionar ni desplegar sin revisar el HEAD y el CI.

## Estado de producto

Spring Boot sirve Thymeleaf, JavaScript y CSS desde un mismo servicio. El backend de Render usa PostgreSQL; Vercel no está configurado. La copia local está en `http://localhost:8091/` con H2 persistente `./data/redauxilio_preview.mv.db`; sus cuentas y reportes son distintos de los de Render. El puerto 8090 ejecuta otro JAR local y puede impedir que Maven reempaquete `target/redauxilio-0.1.0-SNAPSHOT.jar`; usar `spring-boot:run` en 8091 para revisar sin detener 8090.

Inicio, Explorar, Publicar, Mis reportes, Cuenta, auditoría, verificación y Encontrar personas conservan sus rutas. Los permisos de auditor y verificador permanecen separados. La cabecera y navegación móvil son compartidas. La API muestra trece reportes base/fotográficos; la comparación de personas usa dos casos preparados y un resultado predeterminado, sin reconocimiento facial real. El servicio worker sirve recursos públicos y excluye datos privados.

## Incremento actual: Mis reportes

Se implementaron los frames Laguna `30:389` (390×844) y `30:439` (1366×880). La página antigua de formulario inmediato pasó a una lista de tarjetas con filtros Todos/Activos/Borradores, pictogramas SVG originales y panel lateral en escritorio. Las tarjetas usan únicamente publicaciones propias de `/api/my/publications`; el borrador procede de IndexedDB. Las acciones abren los formularios existentes de edición, cierre/reapertura, contacto e historial. El servidor mantiene autorización, CSRF y auditoría. El quinto destino global «Encontrar personas» permanece porque fue añadido después de Figma.

El acceso ciudadano local se comprobó en navegador con cuenta de prueba. La base H2 local contiene `auditor.demo@lagunaviva.local` con permiso `ROLE_OPERATOR`: se restableció su clave solo en esa base y se verificó el ingreso al panel `/operator` en 8091. La cuenta de Render no fue verificada. La vista vacía se observó a 390 y 1366 px; las tarjetas con datos se cubren mediante prueba JS dirigida, pero falta captura visual de una cuenta con publicaciones reales. La barra inferior de auditor ahora acomoda los seis destinos en una fila.

## Verificación y siguiente paso

`npm test`: 183/183; `mvnw test -q` y `npm run format:check`: correctos antes del último ajuste de navegación. Volver a validar el formato, publicar el HEAD y revisar su CI. Conservar las credenciales fuera del repositorio y solicitar revisión visual humana del PR #44.
