# ADR-007 — Formato firmado experimental v1

Estado: codec interno probado, no formato público estable. Sin guardado de archivos, importación desde interfaz ni custodia de claves. No usar todavía como copia de referencia de datos reales.

## Garantías del codec

SignedManifestCodec firma el payload exacto mediante Ed25519 del JDK. Antes de interpretar entradas, verifyAndRead valida la firma con una clave pública que el llamante proporciona por separado. No se incorpora una clave al archivo y no se decide aquí si la clave externa es confiable.

ReferenceManifest es un modelo de datos; construirlo no prueba autenticidad. El resultado del codec tampoco incorpora una etiqueta de confianza en el firmante. [VerifySignedReference](trusted-verification.md) resuelve una política externa de claves antes de usar el codec y la comparación. CompareEntries sigue informando NOT_CHECKED como etapa aislada; el resultado exterior autenticado aporta el estado de firma y confianza. La política persistente y su gestión siguen pendientes.

## Estructura binaria

Enteros big-endian. Ninguna deserialización de objetos Java, compresión, interpretación de URLs ni ejecución de contenido.

Envoltorio: longitud del payload (int32 positivo), payload exacto y firma Ed25519 de 64 bytes. Rechazar truncamiento y cualquier byte extra al final. La longitud del envoltorio solo delimita; la firma cubre todos los bytes del payload.

| Campo del payload | Tamaño / representación |
|---|---|
| Identificador | 4 bytes ASCII VCMF |
| Versión | 1 byte, valor 1 |
| Fecha de creación | int64, segundos de época no negativos |
| Entradas omitidas | int32 no negativo |
| Número de entradas | int32 entre 0 y 1000 |
| Cada ruta | longitud uint16 y bytes UTF-8 estrictos |
| Cada tamaño | int64 no negativo |
| Cada huella | 32 bytes SHA-256 |

Algoritmos fijos por versión; no se eligen mediante una cadena del archivo. Identificador y versión se firman junto con los metadatos. Fecha es una declaración del firmante, no un sello de tiempo confiable ni protección contra reproducción de referencias antiguas.

Entradas ordenadas por comparación lexicográfica Java de la ruta exacta. El escritor ordena y el lector rechaza orden distinto o duplicado. ManifestPath conserva su gramática; se rechazan además colisiones conservadoras por mayúsculas. No normalizar Unicode ni rutas para hacer aceptable un archivo inválido.

Una firma válida sobre contenido malformado también se rechaza: versión desconocida, rutas peligrosas, UTF-8 inválido, metadatos negativos, recuentos fuera de límite o bytes sobrantes. El codec no accede al sistema de archivos.

## Recursos y límites

Payload máximo: 1 MiB. Envoltorio máximo: 1 MiB + 68 bytes. Máximo 1000 entradas y 16 KiB codificados por ruta, además de los límites de ManifestPath. Se valida longitud antes de reservar memoria; no se usa readAllBytes sobre la entrada. No se cierra el stream del llamante.

Este piloto mantiene el payload acotado en memoria y construye la lista de entradas. Puede haber copias del payload y objetos adicionales: 1 MiB no es un presupuesto de memoria total del proceso. Es una excepción temporal, explícita, al objetivo de procesar catálogos grandes incrementalmente. Antes de ampliar límites, diseñar spool/índice en disco y medir recursos; no aumentar constantes sin esa revisión.

Leer hasta EOF puede bloquear en streams que no terminan; usar solo fuentes locales finitas controladas en este hito, no sockets ni streams remotos arbitrarios.

## Cobertura pendiente

El número de omisiones está firmado y se conserva, pero todavía no hay detalle por omisión, motivos ni catálogo completo de errores. Tampoco se demuestra que el escaneo original fuese exhaustivo o que los datos originales estuvieran sanos. Esa información requiere el futuro recorrido y formato definitivo.

Sin cifrado del catálogo: nombres y hashes pueden leerse. Sin claves persistentes, recuperación, rotación, revocación, escritura atómica o protección contra rollback. Las claves de los tests son efímeras. La demo de consola anterior no cambia de semántica y sigue sin autenticar referencias.

## Pruebas

Once pruebas: ida y vuelta con omisiones, orden canónico, clave incorrecta, alteraciones de payload/firma, truncamiento, bytes sobrantes, límites de lectura/escritura, versiones/metadatos inválidos aun firmados, traversal y UTF-8 malformado firmados, duplicados y tamaños negativos. Suite completa: 80 casos sin fallos ni omisiones.
