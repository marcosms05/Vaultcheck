# Crear y exportar una referencia firmada

En la ventana, selecciona la carpeta y abre **Crear referencia** en la barra lateral. **Preparar referencia** ejecuta el recorrido en segundo plano. Si hay incidencias, cancelación o límites, no habilita una referencia completa. Revisa el recuento antes de continuar.

**Firmar y guardar…** solicita una clave cifrada VaultCheck `.vckey`, su clave pública Ed25519 DER `.der`, un destino `.vcm` fuera del árbol analizado y la contraseña de la clave. Puedes utilizar una identidad existente o crearla antes desde **Identidades**; ver [identidades locales](local-identities.md). La creada durante esta sesión queda preseleccionada para firmar. El acceso directo a estos archivos no equivale a inscripción en un almacén persistente.

Se valida que la clave privada desbloqueada corresponda a la pública seleccionada antes de firmar. El servicio reutiliza EncryptedReferenceSigner. Solo persiste el manifiesto firmado, nunca la contraseña ni la clave privada en claro. La matriz de contraseña se borra al finalizar y el PasswordField se limpia antes de enviar el trabajo; JavaFX y la JVM pueden haber creado cadenas/copias que no permiten garantizar borrado completo de memoria.

La publicación mantiene abiertos los directorios de origen y destino mediante el lector Windows, escribe un staging en el destino, fuerza su contenido y crea un enlace nuevo hacia el nombre final. No reemplaza destinos existentes y falla si el sistema de archivos no permite esa operación. El staging se elimina al finalizar. No promete durabilidad universal ante pérdida de energía ni resistencia frente a procesos del mismo usuario que manipulen el staging. Un fallo de limpieza puede dejar la referencia publicada y un archivo temporal; la interfaz lo comunica sin borrar el destino.

Cancelar antes de publicar impide el guardado. La publicación es el punto de confirmación: una petición posterior no oculta que el archivo ya se guardó. El resumen representa lecturas previas, no una instantánea tomada al pulsar Guardar. Si cambia la carpeta seleccionada, la exportación exige volver a preparar.

Las pruebas del servicio cubren referencia verificable, conservación del origen, contraseña incorrecta, destino existente, destino dentro del origen y cancelación con limpieza de la matriz de contraseña. DesktopCreationSmoke recorre el diálogo real de contraseña de exportación, cancelación y guardado, seguido de verificación del archivo. Las respuestas del selector de archivos están sustituidas; su uso manual sigue pendiente. La generación local ya está conectada; quedan pendientes el catálogo persistente, rotación y recuperación.


