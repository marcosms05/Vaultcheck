# Prueba del portátil · Windows x64

Esta prueba usa copias de archivos prescindibles. No requiere instalar Java ni ejecutar como administrador. El paquete es una vista previa sin firma de editor.

## Preparación

1. Copia `VaultCheck-0.1.0-preview-windows-x64.zip` al portátil.
2. Extrae todo el ZIP en una carpeta del usuario, por ejemplo Documentos. No ejecutes desde dentro del ZIP. Conserva `VaultCheck.exe`, `app` y `runtime` juntos.
3. Abre `VaultCheck.exe`. Si Windows muestra un aviso, anota su texto antes de continuar; no desactives Defender ni otras protecciones para hacer la prueba.
4. Anota versión de Windows, arquitectura y escalado de pantalla. Esta compilación es x64; ARM64 no está validado.

## Recorrido funcional

- Crea una carpeta de ensayo con tres archivos pequeños y otra carpeta externa para identidades y referencias.
- En Identidades genera una identidad de prueba con contraseña nueva. Conserva la clave pública DER y el archivo cifrado .vckey; no compartas el archivo privado ni su contraseña.
- En Crear referencia selecciona la carpeta de ensayo, prepara el recorrido y guarda la referencia fuera de esa carpeta. Una contraseña incorrecta debe impedir firmar, sin declarar éxito.
- En Verificar carpeta selecciona la referencia y su clave pública. Revisa la firma y aprueba la huella comparándola con la de la identidad que acabas de crear. Importar la clave por sí solo no debe aprobarla.
- Verifica sin cambiar archivos: deben aparecer coincidentes.
- Modifica el contenido de un archivo, cambia el nombre de otro y añade uno. Repite: el contenido cambiado debe ser Modificado; el nombre anterior, Ausente; el nuevo nombre y el archivo nuevo, Añadido.
- Prueba filtros y búsqueda juntos, selección de filas y cierre del inspector. Filtrar no debe alterar el resumen global de cobertura.
- Cierra y vuelve a abrir. Selecciona de nuevo los archivos de referencia/clave pública y revisa la identidad: la aprobación no es persistente.
- Si una operación dura lo suficiente, cancela y espera a que termine. No debe presentarse como una verificación completa.

## Interfaz y registro del resultado

Prueba maximizar y restaurar, y el escalado habitual del portátil (idealmente también 125 % y 150 %). Revisa que botones, mensajes y diálogos de contraseña queden accesibles. Recorre controles con Tab y activa con teclado. Los estados deben comprenderse por su texto, sin depender del color.

Devuelve: versión de Windows, escalado, arranque sí/no, resultado de cada paso y cualquier texto de error. Si envías capturas, oculta rutas personales y huellas que no quieras compartir. No envíes contraseñas ni claves privadas. No hace falta instalar herramientas de desarrollo.

Una prueba correcta valida este equipo y este flujo; no certifica soporte de NAS, grandes conjuntos ni una auditoría completa. Para retirar la vista previa, cierra la aplicación y elimina su carpeta extraída. Las identidades y referencias que hayas guardado fuera permanecen en su destino.
