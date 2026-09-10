# Política de rutas y garantías

Una ruta de manifiesto es relativa y usa `/` como único separador. Rechazar entradas inválidas; no transformarlas mediante normalize, decodificación URL o sustitución de separadores para hacerlas aceptables.

Se rechazan componentes vacíos, `.` y `..`, rutas absolutas, letras de unidad, UNC, barras inversas, ADS mediante `:`, controles, Unicode malformado, caracteres prohibidos y puntos o espacios finales. También nombres de dispositivo con o sin extensión, incluidos COM/LPT con dígitos superíndice. Los errores no repiten la entrada no confiable.

Se conservan acentos, espacios interiores y Unicode válido. Los límites son decisiones conservadoras del producto, no la capacidad máxima de NTFS. Faltan políticas de duplicados y colisiones por mayúsculas, nombres cortos y alias; se resolverán junto al parser. No convertir nombres a minúsculas ni normalizar Unicode sin analizar esas consecuencias.

CheckedPathResolver verifica los tipos de todos los componentes existentes mediante NOFOLLOW_LINKS, rechazando isSymbolicLink e isOther. Una prueba crea una junction en un directorio temporal y verifica su rechazo como raíz y antecesor. No se ha probado en NAS ni con todos los tipos de reparse point.

Otro proceso puede sustituir un directorio después de comprobarlo. Una firma válida y startsWith(root) no resuelven esta carrera. El resolvedor es una comprobación previa, NO una frontera de seguridad atómica. No ofrecer garantías contra modificación adversaria concurrente hasta resolver apertura por handles o una estrategia equivalente validada en Windows.

Se ha añadido un [piloto por handles](windows-handle-reader.md), separado del resolvedor. Bloqueo de renombrado y nuevas escrituras probado localmente; aún requiere revisar otros mecanismos de modificación antes de integrarse como frontera definitiva.

Tampoco hay garantía de instantánea del contenido. Cambios con metadatos restaurados pueden escapar a la comprobación antes/después del lector.

## Fuentes

- [Microsoft: Naming Files, Paths, and Namespaces](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file): nombres reservados y particularidades de Windows.
- [Java: SecureDirectoryStream](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): operaciones relativas a directorios abiertos para evitar carreras. No asumir disponibilidad en el proveedor Windows.
