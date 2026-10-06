# Preparación y primer incremento — RA-00

Fecha: 5 de octubre de 2026 (Colombia). Demostración solicitada: 6 de octubre; no sustituye el calendario del curso.
Equipo de dos autorizado por el docente según Carlos. Javier Charry cumple como interlocutor según Carlos; contacto, agenda y aceptación de participación pendientes. La aprobación del proyecto completo no se presume.

## Objetivo y límites

Incremento: consulta pública de cuatro reportes ficticios con filtro por categoría; borrador textual y coordenadas en IndexedDB; manifiesto y service worker para consulta previamente guardada. No hay mapa, base de datos, fotografías, usuarios, permisos ni publicaciones reales. Los datos están en memoria y son sintéticos. El alcance completo del Word permanece vigente; esta entrega solo inicia su implementación.

## Arquitectura

Un proyecto y un despliegue. Módulo publications con controlador HTTP y servicio de consulta; plantillas y recursos de PWA en resources. Cuando se añada persistencia, el servicio dependerá de un repositorio. Identidad, confianza y moderación serán módulos separados. No añadir microservicios ni infraestructura distribuida.

Java mínimo 17 (compatible con Java 21 del equipo), Spring Boot 3.5.16 y Maven 3.9.11 mediante wrapper. Se mantiene la línea 3.5 conocida por el equipo. Esta elección no demuestra ausencia de vulnerabilidades; revisar dependencias antes de producción.

## Preparación de trabajo

RA-00 habilita RA-02, RF-01/03/04 y prepara RNF-03/12/14. RA-02 no se completa sin mapa, detalle y restantes criterios. Estimación inicial RA-00: 2–4 horas-persona, provisional; el tiempo de IA no se registra como tiempo humano. Responsables humanos propuestos: Santiago, técnica; Carlos, revisión funcional. La guía enviada ya está aprobada según Carlos. Conservar los registros personales de aceptación; realizar revisión cruzada del código sobre su versión final.

Antes de la siguiente historia: requisito y criterios explícitos, estimación, dependencias resueltas y prueba prevista. Integración por PR después de revisar evidencia. No marcar Terminado sin revisión humana. No inventar retrospectivas, encuestas o resultados. Mantener historial real del tablero.

## Pruebas y trazabilidad

- RF-01/03: consulta anónima y filtro por las cuatro categorías; partición de equivalencias.
- RF-03: categoría vacía, categoría desconocida y normalización; partición de equivalencias.
- Preparación RNF-03/12: respuesta de inicio y recursos PWA; pruebas de integración. Esto no demuestra instalación ni funcionamiento offline.
- RF-24/25 y RNF-12: prueba manual de guardar borrador, recargar, desconectar y recuperar; comprobar que nunca aparece como publicado.

Métricas reales se registrarán en EVIDENCIAS.md después de ejecutar. JUnit/MockMvc para integración; JaCoCo para cobertura; PMD para análisis estático; Spotless para formato. No hay módulos críticos de identidad/confianza aún, por lo que no se declara RNF-14 completo. No se declara Taller 5 terminado ni se sustituye su mínimo de doce casos diseñados por el número de ejecuciones parametrizadas.

## Próximas tareas

1. Carlos y Santiago ejecutan los comandos y revisan el PR.
2. Seleccionar proveedor cartográfico y añadir mapa a RA-02.
3. Diseñar esquema PostgreSQL y migraciones antes de publicar datos.
4. Implementar RA-01 con Spring Security antes de habilitar cualquier escritura.
5. Consolidar cinco fichas de calidad y doce casos del Taller 5; medir complejidad y densidad de defectos con definiciones explícitas.
6. Confirmar calendario y acceso docente. Mantener acta radicada original y control de cambios.

## Declaración de uso de IA

Codex de OpenAI apoyó arquitectura, código, pruebas y documentación. Las ejecuciones realizadas por el agente se identifican como tales en EVIDENCIAS.md. Revisión manual de Carlos y Santiago: pendiente. No se declara aceptación ni validación humana que no ocurrió.

## Flujo aprobado

Trabajo desde develop; PR de trabajo hacia develop con squash y mensaje conforme a la guía. Promoción desde develop hacia main preservando el historial. Revisión por el compañero en 24 horas hábiles, sin autoaprobación. WIP: dos tarjetas en progreso, una por persona, y una en revisión. Ante bloqueantes devolver a En progreso con causa y fecha. Terminado solo tras revisión, pruebas de la integración en develop y enlaces a evidencias.

Protecciones de main y develop: mientras no estén configuradas o disponibles en el plan de GitHub, aplicar el control manual documentado: impedir pushes directos, comprobar el revisor y las verificaciones finales antes de integrar.
