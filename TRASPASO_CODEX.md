# RedAuxilio — traspaso vigente

Actualización: 27 de octubre de 2026 (Colombia). Este archivo contiene **solo el estado necesario para retomar**; no reemplaza la comprobación de GitHub y del árbol local. Para normas, leer `AGENTS.md`; para requisitos, `RA05.md`. El historial está en Git y `EVIDENCIAS.md`, que no debe cargarse completo de rutina.

## Rama y trabajo inmediato

- Repositorio: `Carlos1120/redauxilio`.
- Historia activa: **RA-05, confirmación independiente**. Carlos implementa con Codex y Santiago (`Arch0102`) revisa.
- Rama: `feat/RA-05-confirmacion-independiente`, creada desde `develop` en `2abc847700379484a713a0aff84e1f01e51090bc`. **Comprobar HEAD y árbol local** antes de trabajar; no asumir que siguen en ese SHA.
- RA-05 debe contar confirmaciones válidas e independientes: el autor no confirma, solo una activa por cuenta y publicación, tres elevan exclusivamente la confianza comunitaria y el límite es de 30 por cuenta por hora. Persistencia, concurrencia, API, UI y pruebas se implementan en su rama. Criterios y límites en `RA05.md` y tarjeta Trello.
- Al retomar, localizar solo el servicio/repositorio/JS/prueba que afecte la tarea. Las clases de publicaciones actuales están en `src/main/java/co/redauxilio/publications/`; identidad en `identity/`. Estilos visuales: `ESTANDARES_INTERFAZ.md`, solo cuando se cambie interfaz.

## Dependencia externa

- **RA-04**: PR [#13](https://github.com/Carlos1120/redauxilio/pull/13), rama `feat/RA-04-actualizacion-cierre`, HEAD verificado `393602d` en esta actualización. Santiago solicitó cambios de rendimiento; Carlos publicó corrección y CI verde con 58 pruebas Java y 42 JS. Aún requiere nueva aprobación de Santiago e integración a `develop`. Verificar estado actual, sin reutilizar estas cifras para otros commits.
- RA-05 puede construir **el núcleo independiente** ya. La invalidación de confirmaciones al editar ubicación, descripción principal, estado o datos específicos se conectará a RA-04 **después de su integración**, con pruebas de extremo a extremo. No copiar código no integrado de RA-04 ni declarar RA-05 terminada antes de esa comprobación.

## Próxima acción y entrega

Iniciar implementación RA-05 en su rama; abrir PR **Draft** a `develop`, mantener cambios y commits por funcionalidad, y dejar constancia precisa del punto de integración diferido. Ejecutar pruebas específicas durante el trabajo; antes de publicar, Maven/Node/formato completos y comprobar CI del HEAD final. No hacer merge sin revisión humana de Santiago. Al cerrar el bloque, actualizar **este resumen en 300–500 palabras**, indicando HEAD, PR, resultados y próximo paso. Documentar pruebas y límites extensos por referencia en `EVIDENCIAS.md`.
