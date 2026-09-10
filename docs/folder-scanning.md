# ADR-011 — Recorrido local acotado de carpetas

WindowsFolderScanner genera entradas de referencia leyendo una carpeta local. El resultado FolderScan separa archivos leídos, incidencias y cobertura. No firma automáticamente, no escribe en el árbol analizado y no habilita todavía una selección de carpetas desde interfaz.

## Recorrido

Se retiene la cadena de directorios de la raíz con handles Windows. Durante cada descenso se conserva el padre y se abre un handle adicional para el hijo. El stream de enumeración y el handle se cierran al salir de cada nivel, también tras cancelación o fallo del consumidor de progreso.

Se validan nombres relativos, duplicados conservadores por mayúsculas y atributos sin seguir enlaces. Junctions y tipos especiales se registran como incidencias. Cada archivo ordinario se lee con WindowsHandleFingerprintReader; se mantienen sus restricciones de compartición, enlaces físicos y proveedores. No hay fallback a un lector menos protegido.

El progreso entrega entradas terminadas; no calcula un porcentaje inventado. El consumidor puede actualizar contadores y bytes a partir de cada huella. Debe tratar eventos como provisionales hasta recibir el resultado final; si lanza una excepción, no hay resultado completo.

## Cobertura y límites

- COMPLETE: terminó el recorrido observado sin incidencias.
- INCOMPLETE: alguna entrada o directorio no pudo procesarse.
- CANCELLED: cancelación observada; conservar solo las entradas ya terminadas.
- LIMIT_REACHED: se detuvo por presupuesto, sin fingir haber enumerado el resto.

Valores iniciales: 1000 archivos leídos, 4096 entradas visitadas y 32 niveles bajo la raíz. Límites máximos configurables del piloto: 1000/10000/32. Las listas de resultados, incidencias y nombres están acotadas por esos límites. El tamaño final del manifiesto sigue sujeto al límite independiente del codec; completar el recorrido no garantiza que el catálogo quepa en su formato actual.

Las incidencias pueden corresponder a un directorio entero. No representan un recuento exacto de archivos omitidos dentro de él. Por ese motivo, completeReference solo convierte un resultado COMPLETE; rechaza todos los parciales. Antes de permitir referencias parciales habrá que extender el formato con alcance y motivos de las exclusiones, en lugar de convertir el número de incidencias en un falso número de archivos omitidos.

Una carpeta vacía produce un inventario completo vacío. No se incluyen directorios vacíos, permisos, ADS ni otros metadatos; el alcance es contenido de archivos ordinarios. La verificación de un inventario vacío mantiene su estado NO_ENTRIES.

## Limitaciones

No hay instantánea: se pueden crear o modificar archivos durante el recorrido y no se garantiza una vista de un mismo instante. COMPLETE describe el recorrido observado, no la ausencia de cambios concurrentes ni la salud de los archivos. Se conservan las limitaciones del piloto nativo sobre reparse points, control del proceso, cambios de unidades e I/O bloqueada.

El soporte sigue limitado a unidades locales fijas Windows. NAS y extraíbles permanecen pendientes. Los nombres de incidencias son datos no confiables: la interfaz deberá mostrarlos literalmente y no convertirlos en marcado o enlaces ejecutables.

## Evidencia

Ocho pruebas: carpeta anidada, carpeta vacía, cancelación/liberación de handles, límite de archivos, límites de profundidad/visitas, archivo ocupado con continuación del resto, fallo del consumidor y junction sin entrada al destino.

La demo firmada ahora crea su manifiesto mediante este recorrido, en vez de proporcionar hashes manualmente. Se ejecutó desde el JAR y conservó los tres resultados esperados: coincidencia, modificación y rechazo de firma manipulada.
