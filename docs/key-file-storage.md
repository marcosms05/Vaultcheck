# ADR-010 — Almacenamiento local de claves cifradas

Estado: primitivas internas probadas en Windows. No interfaz de gestión ni ubicación permanente seleccionada para el usuario.

## Directorio privado

WindowsPrivateDirectory.create crea un directorio nuevo de nombre aleatorio bajo un padre controlado por la aplicación. La ACL inicial permite acceso a la identidad actual de Windows y se hereda a archivos/subdirectorios. Se consulta la identidad del sistema mediante GetUserNameW, no mediante una propiedad Java o nombre de usuario importado.

Antes de escribir secretos se comprueba el propietario, tipo ordinario sin redirección y ausencia de concesiones ALLOW a otras identidades. También se comprueba la ACL del archivo de clave. Si la plataforma no ofrece estas garantías o la ACL es más amplia, se rechaza la operación. No se relajan permisos como alternativa ni se cambian ACL de carpetas existentes del usuario.

El padre debe ser parte del espacio privado de la aplicación, no una carpeta compartida ni un destino arbitrario procedente de un manifiesto. El componente no convierte una cadena de antecesores hostil en un espacio seguro. Cambios concurrentes por procesos con la misma identidad, administradores o control del sistema operativo quedan fuera de esta primitiva. La futura integración debe elegir y validar una ubicación del perfil local.

## Escritura y publicación

EncryptedKeyFiles.create recibe una clave y contraseña, produce el contenedor cifrado en memoria y solo entonces accede a disco. Nunca escribe PKCS#8 sin cifrar.

1. Validar el directorio privado.
2. Crear staging aleatorio `.pending-*.vckey` en ese directorio y comprobar su ACL.
3. Escribir el ciphertext completo y ejecutar FileChannel.force(true).
4. Publicar un nombre UUID `.vckey` mediante createLink al archivo ya completo.
5. Eliminar el nombre de staging. El nombre publicado conserva los permisos del mismo archivo.

El enlace duro evita reemplazar un nombre existente y no requiere copiar datos después de publicar. Si el proveedor no admite enlaces, la operación falla; no se usa una copia o renombrado con semántica más débil como fallback. Esto es una técnica interna de publicación, no aceptación de enlaces de directorio importados.

Los UUID se generan internamente; el llamante no puede elegir un archivo a sobrescribir. Los guardados crean entradas distintas. Falta un índice de identidad a archivo y la operación de cambio/rotación de claves.

## Lectura y fallos

Solo se aceptan identificadores UUID con extensión .vckey; no rutas arbitrarias. Se validan ACL, tipo y tamaño, y se lee como máximo el límite del codec más un byte. El desbloqueo exige autenticación GCM; un archivo truncado no devuelve una clave.

Los nombres de staging se rechazan como identificadores publicados. Un cierre abrupto puede dejar staging cifrado. Un fallo al limpiar después de crear el enlace se informa como publicación realizada con error de limpieza: no se debe suponer que no existe una clave ni borrar publicaciones automáticamente. Falta reconciliación de restos con un índice persistente.

force(true) y publicación del enlace no constituyen una garantía universal de durabilidad tras corte eléctrico, ni se ha simulado ese escenario. Las pruebas cubren archivos truncados y staging incompleto, no una inyección real de caída del proceso en cada instrucción. No afirmar recuperación frente a cualquier fallo físico.

## Pruebas y límites

Siete pruebas de almacenamiento: guardar/recuperar y firmar, permisos del archivo publicado, dos exportaciones sin modificación de la anterior, truncamiento, nombres/tipos/tamaños inválidos, fallo previo de cifrado sin publicación, ACL ampliada rechazada y staging no importable (algunas comprobaciones se agrupan en un caso).

No hay datos reales del usuario ni migración de claves existentes. Pendientes integración con identidades persistentes, exportación de backup, selección segura del perfil local, manejo de restos, revocación, pruebas en Windows limpio y calificación de proveedores/volúmenes. Este almacén es local; el requisito NAS corresponde a los datos analizados, no a guardar claves privadas en una carpeta de red.

Fuentes: [Microsoft — GetUserNameW](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getusernamew), [Microsoft — seguridad de archivos](https://learn.microsoft.com/en-us/windows/win32/fileio/file-security-and-access-rights), [Java — enlaces](https://dev.java/learn/java-io/file-system/links/).
