# Fotos para la comparación

Esta carpeta contiene únicamente fotografías aportadas para la exposición. Conserva solo material con autorización expresa.

1. Copia entre una y tres imágenes JPEG, PNG o WebP de referencia por persona dentro de esta carpeta.
2. Añade entre una y tres imágenes separadas que funcionen como entradas controladas de comparación.
3. Completa `candidates.json` con una entrada como esta:

```json
[
  {
    "code": "PERSONA_DEMO_01",
    "referenceName": "Nombre de referencia",
    "ageRange": "25–35 años",
    "description": "Caso preparado exclusivamente para la exposición.",
    "score": 92,
    "images": ["classpath:demo-match/persona-demo-01.png"],
    "comparisonImages": ["classpath:demo-match/persona-demo-01-busqueda.png"]
  }
]
```

4. Reinicia la aplicación. Los recursos se sanean y se guardan en SQL.
5. Descarga la foto de búsqueda desde **Encontrar personas** y súbela para reproducir el resultado.

El porcentaje es predispuesto. No se ejecuta reconocimiento facial, no se confirma identidad y las fotos que el usuario compara no se almacenan.

El catálogo de exposición vigente contiene dos casos autorizados:

- Jesús Peña: tres referencias, incluida la imagen visible del escenario de desastre, y una entrada controlada de comparación.
- Santiago Ortiz: tres referencias, incluida la imagen visible del escenario de desastre, y una copia separada de esa escena para reproducir el resultado preparado.
