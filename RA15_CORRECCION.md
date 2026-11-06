# RA-15: conservar ultima consulta publica

Base a43f3358fad971170e4906aa9cd626962b9acce1. Reproduccion aislada automatizada del coordinador real app.js: tras recibir consulta almacenada, la peticion de otra zona falla y el codigo original vacia mapa/lista. No se ejecuta navegador offline en esta correccion.

Cambio minimo: un fallo conserva la ultima respuesta valida, incluso vacia, y comunica en la region accesible existente que corresponde a la zona y filtros anteriores. Conserva origen/fecha de almacenamiento. Una respuesta posterior valida sustituye resultados y aviso normalmente. Sin cambios al service worker, permisos ni backend. No se declara consulta offline completa ni cierre de RA-15.

Regresion: consulta almacenada, fallo de zona no disponible, conservacion de marcadores/fecha y reconexion consultando south/west/north/east actuales. Pruebas dirigidas report-origin y service-worker. Validacion humana offline pendiente.
