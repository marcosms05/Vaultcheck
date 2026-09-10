# Fase 4: hitos

1. Base validada localmente: Maven verify, arranque del JAR sin HTTP, lector por bloques, pruebas criptográficas y documentación. Falta ejecución en JDK 21/CI remoto.
2. Frontera de archivos en curso: gramática Windows y rechazo de junction local probados. Piloto nativo con bloqueo de renombrado/escritura y liberación tras cancelación probado. Pendientes revisión de otros mecanismos de sustitución/reparse, I/O bloqueada y recorrido con cobertura explícita. Prueba de NAS requiere una carpeta de ensayo proporcionada por el usuario; nunca desconectar una red real automáticamente.
3. Referencias: codec firmado, confianza en memoria, recuperación cifrada y correspondencia privada/pública probados. Primitivas locales de almacenamiento de ciphertext con ACL/publicación sin reemplazo probadas. Pendientes detalle de omisiones, índice y gestión persistente de identidades, manejo de restos y escritura de referencias. Solo se utilizan claves sintéticas.
4. Casos de uso: recorrido local acotado, generación desde recorrido completo y demo firmada disponibles. Comparación autenticada del inventario actual disponible: añadidos, ausentes y estados no verificables cuando falta cobertura. Pendientes informes, referencias parciales en el flujo de usuario y grandes conjuntos. Ver docs/folder-verification.md. Ver docs/try-demo.md.
5. Apartado gráfico iniciado: maqueta estática en docs/mockups/verify-folder.svg, con datos ficticios. Ventana JavaFX con logo, estados vacío/demostración, búsqueda e inspector implementada. Comprobación real de datos sintéticos en segundo plano y cancelación cooperativa implementadas, con cierre tras finalizar la limpieza. Selector de carpeta e inventario local de solo lectura conectados, sin declarar autenticidad. Panel de selección .vcm/clave pública DER, revisión de firma, aprobación explícita de huella y verificación conectados. Preparación y exportación con claves cifradas existentes conectadas. Generación local de identidades cifradas conectada, con selección de la nueva identidad para firmar en la sesión. Flujo de creación y exportación probado con diálogos de contraseña reales y selectores de sistema sustituidos. Pendientes selección nativa manual, catálogo persistente y recuperación; afinar la composición siguiendo verify-folder.svg. Validar escalado, accesibilidad y memoria. Ver docs/try-desktop.md. No requiere terminar NAS, informes o instalador para comenzar.
6. Distribución: primer app-image portátil generado mediante jpackage, con EXE y runtime incluido. Ver docs/portable-windows.md. Pendientes runtime final, optimización de tamaño, icono, instalador por usuario, pruebas en Windows limpio sin Java y verificación de desinstalación. Firma de código depende de un certificado de publicación; no inventar identidad.

Fases 5 a 7: publicación, mantenimiento de dependencias e incidentes, retirada con exportación y eliminación explícita de claves. No se consideran completadas por generar un JAR.










