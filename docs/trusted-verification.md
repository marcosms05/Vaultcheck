# ADR-008 — Verificación con una política explícita de confianza

Estado: flujo interno probado, sin gestión de identidades desde interfaz ni persistencia. No se han aprobado claves reales del usuario. Los tests generan claves efímeras.

## Orden obligatorio

VerifySignedReference recibe una raíz, un stream finito del manifiesto, el identificador del firmante y los callbacks de cancelación/resultados.

1. Resolver el identificador mediante TrustedSignerSource. Si no es confiable, rechazar sin leer el manifiesto ni archivos de destino.
2. Verificar firma y contenido mediante SignedReferenceReader. El adaptador actual es SignedManifestCodec; rechaza firma incorrecta y contenido malformado antes de acceder a la carpeta.
3. Comparar las entradas con CompareEntries y el lector explícitamente configurado. La prueba integrada utiliza el lector nativo Windows.
4. Crear el resultado autenticado con la huella del firmante, omisiones firmadas, contadores y cobertura. Errores de validación/consumidor no producen un resultado exitoso.

La capa de aplicación depende de contratos, no de implementaciones de almacenamiento o codecs. El resultado final tiene constructor privado a la orquestación para evitar marcar como autenticado un ComparisonSummary construido directamente.

## Política del piloto

PinnedSigners recibe un conjunto previamente aprobado de claves públicas Ed25519 y crea una copia inmutable. Reconstruye cada clave mediante KeyFactory, evitando conservar objetos de clave mutables proporcionados por el llamante. Identificador: SHA-256 hexadecimal minúsculo de la codificación pública X.509/SPKI canónica. Máximo 100 claves.

La política no añade claves al verificar, no importa confianza desde el manifiesto, no consulta servidores ni guarda nada en disco. Construir esa política es una operación del componente de confianza; los datos importados no deben construirla automáticamente. La futura interfaz deberá vincular el alta a una acción explícita y a una comprobación de huella fuera del archivo recibido.

No se ofrece revocación durante una operación: la política es una instantánea inmutable. Cambiar la lista exige nueva política y nueva operación. Persistencia, revocación, rotación y protección de la clave privada siguen pendientes. La huella identifica una clave, no una identidad humana certificada.

## Resultado y presentación

Result.authentication() devuelve VALID_SIGNATURE_TRUSTED_PIN. Esto significa firma verificada con una clave admitida por la política suministrada; no certifica que esa política tenga un origen externo legítimo ni que los archivos estén libres de malware.

Result.coverage() conserva CANCELLED con prioridad. Si la referencia tiene omisiones, devuelve INCOMPLETE aunque todas las entradas leídas hayan coincidido. Un manifiesto vacío permanece NO_ENTRIES si no declara omisiones.

Result.entries() conserva el resumen de la etapa de comparación; su NOT_CHECKED se refiere a esa etapa aislada. La interfaz debe usar authentication() y coverage() del resultado exterior como estado de la operación, y entries() para los contadores y cobertura de lectura. No derivar una etiqueta global del resumen interno.

El alcance sigue siendo SUPPLIED_ENTRIES_ONLY: no se enumera la carpeta ni se detectan archivos nuevos. COMPLETE significa que se procesaron las entradas firmadas sin errores/omisiones declaradas, no que se certificó todo el árbol. No se garantiza instantánea ni protección contra rollback.

Cancelación cooperativa: se comprueba antes del flujo y durante comparación. El codec acotado no tiene interrupción interna; no ofrecer cancelación inmediata de streams bloqueados.

## Evidencia

Pruebas de identidad desconocida sin lectura de origen/destino, firma alterada sin lectura de destino, omisiones con coincidencias, cancelación, copia de política, identificadores malformados y claves de otro algoritmo. Una prueba adicional integra firma Ed25519, política de confianza y lectura nativa de un archivo temporal real.
