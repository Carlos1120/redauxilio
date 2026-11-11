# RedAuxilio — RA-14 estabilizada para revision

Actualizacion: 11 de noviembre de 2026, Colombia. Rama feat/RA-14-posibles-duplicados desde develop201910dafc39bea291edf3a08089d507d5763634, worktree C:/Users/cmps-/RedAuxilio-recovery/ra14-posibles-duplicados. Conservar todos los worktrees, respaldos y servidores anteriores. No mezclar RA-16/PR31. Publicacion autorizada solo en esta rama y PR Draft hacia develop; sin merge ni Trello.

## Contrato y arquitectura

RA14.md contiene RF-18/RN-26/RN-27 y las reglas confirmadas por Carlos. Geograficos: misma categoria, <=250m y ultimas24h inclusivas, excluyendo futuro/ocultos; cerrados geograficos pueden advertir. Desaparecidos abiertos visibles: nombre normalizado exacto O edad informada + sector + descripcion fisica coincidentes. Fecha aproximada solo contexto, sin distancia ni ventana temporal. No fusionar automaticamente.

Preconsulta privada POST con sesion/CSRF/no-store, advertencia accesible, enlace a detalle publico y justificacion privada1–500 al continuar. Servidor vuelve a comprobar inmediatamente antes de insertar; altas con fotos comparten transaccion. Cambiar campos invalida decision anterior y fallo no borra borrador. No exponer contactos/autores/justificaciones en candidatos o caché publica.

Bloqueo por autor conserva cuota; guardia SQL por categoria serializa altas entre cuentas distintas hasta commit/rollback. Se comprobo201/409 concurrente, categorias independientes con conexiones distintas, fotos/justificacion atomicas y operacion posterior al rollback. No son bloqueos distribuidos ni una guardia global. Limite: serializacion de una categoria durante altas con fotos; comparacion Unicode de desaparecidos por bloques de casos abiertos, sin indice normalizado ni promesa de escala masiva.

## Estado verificado y continuidad

Regresiones iniciales16, corregidas16, restantes0. Tabla por prueba en RA14_REGRESIONES.md. No se agregaron motivos genericos ni se debilitaron reglas. Politica TDD RED real por clase ausente; concurrencia RED con dos201 y GREEN por guardia. H2 dirigido11/11, PostgreSQL18.3 aislado11/11, Maven verify198Java con Spotless/PMD y JavaScript135/135. Asercion dirigida posterior de HTTP400 eleva controlador a100%; servicio97,01%, politica100%, creacion94,34%. Fuentes y limites de cada ejecucion en EVIDENCIAS.md; logs/bases exclusivos fuera de Git en ra14-qa. No atribuir esos controles a interacciones humanas.

Siguiente: comprobar remoto, revisar diff final, publicar commit coherente, crear PR Draft y comprobar CI del SHA exacto. Mantener revision visual y aceptacion humana pendientes; no solicitar ni registrar aprobaciones inexistentes. No marcar RA-14 terminada. Preservar evidencia RED/GREEN y entornos existentes.
