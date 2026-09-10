# ADR-006 — Piloto de lectura mediante handles de Windows

## Problema

CheckedPathResolver valida nombres y tipos existentes, pero devuelve una ruta que puede cambiar antes de abrirse. No sirve por sí solo como frontera contra sustitución concurrente.

## Implementación probada

WindowsHandleFingerprintReader recibe raíz y ManifestPath. Usa JNA 5.19.1 para un conjunto pequeño de funciones de kernel32, sin reflexión sobre descriptores privados del JDK, privilegios elevados ni shell en producción.

1. Restringir el piloto a unidades locales fijas del proveedor Windows. Rechazar UNC y unidades de red antes de acceder al archivo.
2. Validar y acotar la cadena completa de componentes.
3. Abrir desde la raíz, reteniendo cada directorio con FILE_READ_ATTRIBUTES, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS y FILE_FLAG_OPEN_REPARSE_POINT. Compartir solo lectura: no FILE_SHARE_WRITE ni FILE_SHARE_DELETE.
4. Examinar atributos del handle recién abierto y rechazar cualquier reparse point o tipo inesperado. Mantener abiertos los padres al abrir el siguiente componente.
5. Abrir el archivo para GENERIC_READ, también compartiendo solo lectura, rechazar más de un enlace físico y leer mediante ReadFile sobre ese mismo handle.
6. Calcular SHA-256 por bloques de 64 KiB y contrastar tamaño/fecha del mismo handle. Cerrar todos los handles en orden inverso incluso tras cancelación o error. No devolver resultado si falla el cierre.

BACKUP_SEMANTICS permite abrir directorios; no se habilita SeBackupPrivilege. No se cambian ACL ni contenido. Si una apertura encuentra conflicto de compartición, devuelve error: no volver al lector por rutas de forma silenciosa.

## Evidencia

Nueve pruebas nativas ejecutadas en Windows sin omisiones: hash conocido y layout de estructura, múltiples bloques y archivo vacío, rechazo de writer ya abierto, bloqueo de nuevo writer y renombrado de padre mientras se lee, cancelación inicial y con archivo abierto, rechazo de junction como raíz/antecesor, rechazo de enlaces físicos y de UNC. Las pruebas comprueban que vuelven a ser posibles escritura/renombrado al terminar o fallar la lectura.

Las pruebas de modificación se ejecutan desde otro handle durante callbacks controlados; ejercitan las reglas de compartición del sistema operativo. No equivalen a una auditoría exhaustiva de todos los mecanismos de sustitución.

## Alcance y trabajo pendiente

- Es un adaptador piloto conectado a la comparación interna y demostración sintética, no a una verificación autenticada ni interfaz. El lector anterior conserva sus limitaciones.
- Faltan pruebas de modificación de reparse points en directorios ya abiertos, proveedores/volúmenes adicionales y revisión específica de seguridad antes de dar por cerrada la frontera de archivos.
- No garantiza una instantánea ni cubre escritura mediante mapeos preexistentes, manipulación del proceso, control del sistema operativo o cambios maliciosos de asignación de unidades DOS.
- El cierre de compartición puede impedir trabajo legítimo de otras aplicaciones mientras dure cada lectura. La interfaz debe informar conflictos como «No verificable», sin pedir elevación.
- Cancelación cooperativa entre lecturas; una llamada nativa bloqueada debe retornar antes de observar la cancelación. Faltan estrategia y pruebas de I/O bloqueada antes de NAS.
- Límite conservador de longitud/profundidad aplicado a la ruta completa. Puede excluir rutas que NTFS admita; informar siempre de exclusiones.
- Sin soporte NAS ni extraíbles en este piloto. Se mantiene como requisito del producto y no se declarará disponible hasta probarlo.
- JNA añade una biblioteca nativa y extracción al directorio temporal: validar empaquetado, licencias y carga en Windows limpio antes del EXE. No requiere red al ejecutar.

## Fuentes

- [Microsoft: CreateFile](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-createfilea), permisos de compartición y apertura de reparse points.
- [Microsoft: GetFileInformationByHandle](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getfileinformationbyhandle), atributos del objeto abierto.
- [Microsoft: BY_HANDLE_FILE_INFORMATION](https://learn.microsoft.com/en-us/windows/win32/api/fileapi/ns-fileapi-by_handle_file_information), layout y metadatos.
- [JNA](https://github.com/java-native-access/jna), acceso a bibliotecas nativas desde Java.
