# Comparación autenticada de carpetas

`VerifyFolder` resuelve primero la identidad aprobada y valida firma y contenido de la referencia. Solo después solicita el inventario a `FolderInventory`, implementado por el lector de carpetas Windows.

| Estado | Significado |
|---|---|
| MATCHED | Mismo tamaño y SHA-256 en referencia y lectura actual. |
| MODIFIED | Tamaño o SHA-256 distinto. |
| ADDED | Archivo observado que no aparece en una referencia sin omisiones. |
| ABSENT | Entrada firmada que no aparece en un recorrido actual completo. |
| NOT_VERIFIABLE | No hay evidencia suficiente o existe ambigüedad de nombres. |

Una exploración incompleta, cancelada o limitada nunca convierte una entrada no observada en AUSENTE. Una referencia con omisiones no permite calificar entradas adicionales como AÑADIDAS. Las colisiones de nombres hacen no verificable también el contenido de un directorio ambiguo. Las incidencias del recorrido se conservan en el resultado aunque no tengan una entrada firmada asociada.

La cobertura y la autenticación son independientes de las diferencias: COMPLETE puede contener archivos modificados, añadidos o ausentes. Una firma válida identifica una referencia aprobada; no certifica que los archivos sean seguros. Los resultados y las incidencias son listas inmutables, limitadas por el piloto de 1.000 archivos por inventario y referencia. Los duplicados en un adaptador se rechazan sin producir resultado exitoso.

Las comparaciones usan la misma normalización conservadora de mayúsculas que la referencia. No modelan todos los casos de equivalencia de nombres de Windows. No se incluyen directorios vacíos, ACL ni flujos alternativos. AUSENTE significa no observado en un recorrido sin incidencias conocidas, no prueba histórica de eliminación. La enumeración no es una instantánea: siguen aplicándose los límites de concurrencia del lector y del recorrido.

Validación: siete pruebas del caso de uso cubren estados, cobertura parcial/cancelada/limitada, omisiones, rechazo previo a lectura, colisiones y duplicados. La demo firmada comprueba además añadidos y ausentes con el recorrido nativo real y datos sintéticos.
