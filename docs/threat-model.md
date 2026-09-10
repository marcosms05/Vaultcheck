# Modelo de amenazas inicial

Activos: contenido del usuario, clave privada, confianza en claves públicas, autenticidad y cobertura de referencias, disponibilidad y privacidad de rutas.

Entradas no confiables: manifiestos importados, nombres y estructura del árbol, archivos concurrentemente modificados, carpetas de red. No asumir que un archivo local es confiable.

| Amenaza | Control previsto | Situación actual |
|---|---|---|
| Modificar contenido conservando tamaño | Volver a calcular SHA-256 | Prueba disponible |
| Alterar manifiesto | Firma sobre bytes inequívocos | Rechazo antes de leer destino probado en flujo autenticado |
| Sustituir clave del firmante | Almacén de confianza externo | Política inmutable de claves aprobadas probada; persistencia/alta/revocación pendientes |
| Traversal, rutas absolutas, ADS, rutas de dispositivo | Gramática restrictiva y validación contextual Windows | ManifestPath probado; parser completo pendiente |
| Enlaces, junctions y cambios de antecesores | Recorrido sin redirecciones y política fail-closed | Cadena existente comprobada; junction local probada; carrera pendiente |
| Sustitución durante lectura | Metadatos y estrategia específica Windows | Piloto nativo bloquea renombrado/escritura en pruebas; revisión de reparse pendiente; sin instantánea |
| Archivos o manifiestos enormes | Streaming y límites de recursos | Lector por bloques; codec limitado a 1 MiB/1000 entradas, catálogo grande pendiente |
| Robo de clave privada | Protección estándar y exportación cifrada | Codec PBKDF2/AES-GCM probado; almacenamiento y gestión de claves pendientes |
| NAS desconectado | Cobertura incompleta y error explícito | Pendiente de integración |

Un atacante con control total del proceso o del sistema operativo queda fuera de las garantías. No se garantiza integridad de un archivo originalmente corrupto ni restaurabilidad del sistema.

No etiquetar una referencia como confiable hasta completar custodia de claves, parser y recorrido. No desplegar este hito sobre datos como mecanismo de protección.
