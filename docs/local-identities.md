# Identidades locales

Abre **Identidades → Crear identidad cifrada…**, elige un directorio local y escribe dos veces una contraseña de 12–1024 caracteres. VaultCheck crea una subcarpeta nueva `vaultcheck-keys-*`; no modifica los permisos del directorio elegido ni reemplaza archivos.

La subcarpeta tiene ACL para el usuario actual. Contiene `public.der` y una clave privada cifrada con nombre UUID `.vckey`. Se reutiliza el contenedor autenticado AES-GCM/PBKDF2 existente y la publicación sin reemplazo de EncryptedKeyFiles. No se guarda una clave privada sin cifrar. La huella SHA-256 se muestra al finalizar.

La identidad recién creada queda seleccionada para firmar en esta sesión. Ve a **Crear referencia**, prepara una carpeta, pulsa **Firmar y guardar…** y vuelve a introducir la contraseña. La ventana no conserva la contraseña entre operaciones. Tras reiniciar, podrás seleccionar los archivos `.vckey` y `.der` manualmente al firmar; aún no hay catálogo persistente de identidades, rotación ni recuperación de contraseña.

Conserva la carpeta y la contraseña. Para que otra persona verifique, comparte solo `public.der` y confirma la huella por un canal independiente. La nueva identidad no se incorpora automáticamente a la confianza de referencias importadas.

Cancelar antes del guardado evita crear archivos. Una vez iniciada la persistencia, se completa y se informa del resultado aunque llegue una petición de cancelación. Un fallo puede dejar una carpeta parcial; no se borra automáticamente una clave que pudiera haberse publicado. La pareja pública/cifrada no constituye una transacción atómica. Mantener handles de antecesores reduce sustituciones de directorios, pero no ofrece aislamiento frente a un administrador ni frente a procesos maliciosos del mismo usuario.

Los campos de contraseña se limpian y las matrices char[] propias se sobrescriben; la JVM y JavaFX pueden conservar copias no controlables. No se promete borrado completo de memoria. El destino debe ser una unidad local fija compatible con ACL y enlaces; NAS y unidades extraíbles no están soportados.

Pruebas: creación con ACL, borrado de la matriz de contraseña, firma/exportación y verificación de un archivo real sintético con esa identidad; cancelación previa y contraseña corta sin creación de archivos. DesktopCreationSmoke valida los diálogos reales de contraseña: cancelación sin trabajos, limpieza de campos, creación y firma. Sustituye las respuestas de los selectores del sistema; la selección nativa manual, accesibilidad y escalado siguen pendientes.

