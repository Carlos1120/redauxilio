# Reportes iniciales

El manifiesto crea reportes visuales idempotentes para la presentación y asocia las imágenes aportadas a su evidencia pública. La cuenta técnica no posee una contraseña conocida.

- `DEMO_REPORTS_ENABLED=true` habilita la carga; es el valor predeterminado de la aplicación.
- `DEMO_REPORTS_MANIFEST` permite sustituir el manifiesto sin cambiar código.
- Cada título debe ser único dentro de la cuenta técnica porque identifica la fila que se actualiza al reiniciar.
- Se admiten entre una y tres imágenes JPEG, PNG o WebP de máximo 5 MB por reporte.

La carga sanea las imágenes con la misma política de RA-12 y se ejecuta en una sola transacción.
