# RedAuxilio — traspaso RA-12 y RA-13

Actualización: 3 de noviembre de 2026 (Colombia). El repositorio principal `C:\Users\Santiago\redauxilio` permanece en `feat/RA-08-acciones-moderacion`, HEAD `d6a2348659eb3c3133ae58eb3ee2ac1dfe72387b`, con modificaciones locales en `TRASPASO_CODEX.md` y `src/main/resources/static/app.css`; no se tocaron. Remoto `origin`: `https://github.com/Carlos1120/redauxilio.git`.

## Estado remoto comprobado

`origin/develop` es `366a55045150e9252c081fd3d7627dccf2310353`; avanzó tras integrar el arreglo PostgreSQL del PR #29. PR #26 (RA-12) sigue abierto en `a3c232597c5e83d811f1730a79ec87455d660bc2` y PR #27 (RA-13) en `7655f0f4a9b165d22f1582b286c71c4287977f1b`. Ambos se basaban en `3fa21ac2abdcbc38306f92fa5608b3f67f385458` y GitHub actualmente informa conflictos. La aprobación formal de Santiago y la integración de #29 están verificadas; #26 y #27 no tienen aprobación formal de Carlos. No se integrarán sin su revisión y nueva CI verde.

## Alcance

RA-12 agrega hasta tres imágenes JPEG, PNG o WebP por publicación, con límite de 5 MiB, validación de contenido, guardado SQL, transacción conjunta al crear, borrador local y permisos de autor para agregar o retirar. Las imágenes solo son públicas si la publicación es visible; el servidor no publica nombres originales ni cachea bytes. Contrato y límites en `RA12.md`.

RA-13 agrega permisos auditados para verificadores, eventos oficiales por versión y retiro trazable, además de administración privada de operadores. La salida pública separa verificación oficial de confianza comunitaria y no expone correos. El service worker excluye los recursos y datos privados. Contrato y límites en `RA13.md`.

## Sincronización y siguiente paso

Los worktrees originales RA-12 y RA-13 conservan sus notas locales; la actualización se probó en worktrees auxiliares separados. El merge incorpora el arreglo PostgreSQL y su prueba; el código se combinó sin conflictos y se resolvió el documento de traspaso. Con Corretto 21, H2 y Node 24/npm 11: RA-12 `mvn verify` pasó 167 pruebas Java y `npm test` 122/122; RA-13 pasó 166 pruebas Java y 123/123 JS. Spotless, PMD, Prettier y `git diff --check` pasaron en ambas ramas. El primer intento Maven con Java 25 no pasó Spotless por incompatibilidad conocida; se repitió íntegramente con Java 21 y pasó. No se certificó PostgreSQL.

Siguiente: crear commits de sincronización con la identidad Git configurada, publicar cada rama mediante push fast-forward y comprobar CI en los nuevos HEAD exactos. Mantener #26/#27 abiertos hasta la aprobación formal de Carlos; no fusionarlos ni actualizar Trello. La inspección visual humana y QA PWA siguen pendientes y no se atribuyen a estas pruebas automatizadas.
