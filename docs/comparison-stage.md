# Comparación de entradas — etapa interna

CompareEntries consume entradas de referencia, delega lectura en RootedFingerprintReader y publica un EntryComparison por archivo procesado. Devuelve contadores y cobertura al acabar. No carga todos los resultados en una lista interna; el consumidor decide cómo almacenarlos o mostrarlos.

## Semántica

- MATCHED: tamaño y SHA-256 coinciden.
- MODIFIED: cambia tamaño o SHA-256. No implica malware.
- NOT_VERIFIABLE: la lectura falla con IOException. No deducir «ausente» a partir de una apertura fallida.
- COMPLETE: se procesaron todas las entradas suministradas sin errores de lectura, aunque existan diferencias.
- INCOMPLETE: al menos una entrada no pudo verificarse.
- CANCELLED: se detuvo el trabajo; solo se conservan contadores de resultados publicados.
- NO_ENTRIES: entrada vacía; no presentar como verificación satisfactoria de una carpeta.

El resumen siempre expone SUPPLIED_ENTRIES_ONLY y NOT_CHECKED para ámbito y autenticación. No hay parámetro que permita a un consumidor marcarlo como firmado o confiable. La creación de ReferenceEntry no establece confianza.

## Límites del piloto

No enumera la carpeta ni detecta archivos nuevos. Aún no procesa firmas, manifiestos persistentes ni autenticación. No es un endpoint para entradas externas; solo se conecta a fixtures propios y pruebas.

Límite explícito de 10 000 entradas. Conserva un conjunto acotado de nombres para rechazar duplicados y colisiones simples por mayúsculas antes de leer una segunda vez. Esta comparación conservadora no reproduce todos los alias de Windows; faltan índice en disco y políticas completas de nombres cortos/Unicode para manifiestos grandes.

Si el parser/iterador, la validación o el consumidor de resultados falla, propaga la excepción y no devuelve un resumen completo. Los eventos ya emitidos son provisionales hasta la terminación; una futura interfaz debe marcar la operación fallida y no convertir esos eventos en un informe completo.

La cancelación no es un error de lectura. Los errores internos del sistema no se copian al resultado visible; más adelante se incorporarán códigos de incidencia tipados sin exponer rutas personales ni mensajes nativos sin filtrar.

El lector nativo sigue siendo piloto. Conectarlo a esta demostración no cierra la revisión de carreras, NAS ni I/O bloqueada descrita en windows-handle-reader.md.
