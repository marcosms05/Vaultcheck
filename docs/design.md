# Requisitos y decisiones iniciales

## Producto aceptado

Windows, instalación por usuario mediante EXE, operación local y carpetas NAS accesibles desde Windows. Sin nube, cuentas, servicio permanente ni elevación. Motor Java y Spring Boot. JavaFX propuesto, pendiente de prototipo. Un proceso sin HTTP.

## Requisitos trazables

| ID | Requisito | Estado |
|---|---|---|
| RF-01 | Seleccionar carpeta local o NAS | Pendiente de interfaz y recorrido |
| RF-02 | Crear manifiesto versionado con omisiones explícitas | Generación desde recorrido completo probada; parciales bloqueados hasta ampliar formato |
| RF-03 | Autenticar referencia y comprobar confianza | Flujo firmado con política de claves en memoria probado; gestión persistente pendiente |
| RF-04 | Comparar contenido y clasificar diferencias | Etapa interna con coincidencias, diferencias y no verificables; enumeración pendiente |
| RF-05 | Comparar otra raíz usando rutas relativas | Pendiente |
| RF-06 | Progreso y cancelación | Eventos por entrada y cancelación probados; interfaz pendiente |
| RF-07 | Errores parciales sin aparentar éxito | Cobertura separada de diferencias probada en comparación interna |
| RF-08 | Informe legible y estructurado | Pendiente |
| RF-09 | Renovación explícita de referencia | Pendiente |

## ADR-001: monolito modular sin web

Un proceso, paquetes separados y dominio sin framework. Spring Boot aporta configuración y composición. Evitamos una API local porque los casos de uso no requieren conexiones entrantes. CLI futura reutilizará los mismos casos de uso.

## ADR-002: hashes y autenticidad separados

SHA-256 para bytes de archivos. Ed25519 se prueba como candidato de firma con el proveedor del JDK. Faltan formato inequívoco, límites del parser, claves de confianza, protección de clave privada y recuperación. No se acepta automáticamente una clave incluida en el manifiesto. Sin criptografía propia.

Actualización: [ADR-007](manifest-format.md) implementa un codec binario experimental acotado con Ed25519. La gestión de confianza y claves sigue pendiente. El límite de 1 MiB permite un payload en memoria en este piloto; no sustituye el objetivo de catálogos grandes por streaming.

## ADR-003: lectura completa

No omitir contenido por tamaño y fecha. Buffer de 64 KiB por lector; concurrencia futura acotada. El manifiesto no residirá íntegro en memoria por defecto. No se ha medido aún el consumo del proceso completo.

## ADR-004: distribución

JDK 21 como objetivo de compilación y Spring Boot 4.1.1 fijado. Instalador jpackage posterior, runtime privado. Native Image queda fuera del primer hito. No hay base de datos hasta justificarla con mediciones.

## Modelo de resultado previsto

Comparación: coincidente, modificado, nuevo, ausente, no verificable. Cobertura independiente: completa, incompleta o cancelada. Firma válida no implica identidad confiable ni referencia completa.

## ADR-005: rutas de manifiesto estrictas

ManifestPath acepta rutas relativas separadas por `/`, sin normalizar silenciosamente entradas peligrosas. Límites de producto: 4096 unidades UTF-16 por ruta, 128 componentes y 255 unidades por componente. Nombres fuera de la política deberán aparecer como exclusiones, nunca desaparecer del informe. Ver [política de rutas](path-policy.md).

CheckedPathResolver comprueba tipos sin seguir enlaces en la cadena existente. No ofrece contención libre de carreras: falta decidir una estrategia de apertura mediante identificadores del sistema operativo antes de tratar árboles modificados por adversarios. Una comprobación de prefijo o doble validación no resuelve esta garantía.
