# Revisión de referencia e identidad en escritorio

La importación acepta el formato VCMF v1 experimental y claves públicas Ed25519 en DER SubjectPublicKeyInfo. Se limita la clave a 128 bytes y el sobre a 1 MiB más 68 bytes. Se rechaza DER no canónico y se comprueba la firma antes de ofrecer la aprobación. No se importa la clave privada ni se ejecuta contenido del archivo.

`ReferenceReview` conserva una copia de los bytes y una clave reconstruida. La capacidad `Approved` solo se obtiene tras confirmar la huella exacta; no existe confianza automática al leer una clave. La interfaz pide contrastar la huella mediante un canal independiente. La aplicación no puede verificar la procedencia real de esa confirmación.

Cambiar una selección descarta revisión, aprobación y resultados previos. La aprobación no se guarda en disco. La comparación posterior vuelve a validar la firma de los mismos bytes y utiliza `VerifyFolder`; una referencia con omisiones conserva cobertura incompleta. La identidad aprobada sigue sin certificar ausencia de malware.

Lectura, parser y firma se ejecutan en el trabajador único. Los selectores y las acciones se bloquean mientras trabaja. Cancelar espera al cierre de recursos, con descartado de resultados cuando se solicita durante la verificación.

Los archivos importados se abren en modo lectura con NOFOLLOW en el componente final, comprobación de archivo regular y límites de bytes. Esto no garantiza aislamiento de antecesores frente a sustitución concurrente ni elimina I/O bloqueada. La firma y la aprobación se ligan a lo efectivamente leído; no a la persistencia del nombre del archivo. El lector nativo del árbol objetivo mantiene su política más restrictiva.

El panel está plegado inicialmente para conservar el espacio de resultados. Es una integración funcional provisional; la composición final seguirá DESIGN.md y verify-folder.svg. Aún no existe creación/exportación de referencias desde la ventana ni un almacén persistente de identidades.

## Validación de las transiciones

DesktopReferenceSmoke utiliza los botones y manejadores reales de JavaFX, la firma Ed25519 y el lector nativo sobre archivos sintéticos. Solo sustituye las respuestas de los selectores y la entrada de la huella: no automatiza los diálogos del sistema.

Comprueba ausencia de confianza automática, rechazo de huella incorrecta, aprobación explícita, verificación contra los bytes revisados después de modificar el archivo original, retirada de aprobación al reseleccionar la clave y bloqueo al revisar una firma manipulada. Los campos de referencia y clave tienen nombres accesibles explícitos.

## Huellas utilizables y confianza local de sesión

La revisión y la creación muestran la huella pública en un campo de solo lectura seleccionable con botón Copiar huella. Copiar es una acción explícita de portapapeles y nunca aprueba la identidad. Para identidades externas se pega una huella esperada obtenida por un canal independiente; guardar y cargar un TXT junto a la clave importada no prueba su origen.

Si VaultCheck ha generado una identidad en esta sesión, conserva su huella en memoria. Tras verificar la firma, «Usar mi identidad de esta sesión» solo se habilita si la huella coincide exactamente con la generada. El usuario sigue aprobando explícitamente y la selección de nuevos archivos invalida la revisión. La huella no se persiste entre reinicios ni se obtiene automáticamente de la clave importada. Esta vía elimina la transcripción para el flujo local sin convertir claves externas en confiables.

El tema de la barra nativa se solicita mediante DWM, conserva los controles de Windows, no modifica el registro ni ventanas de otros procesos y no aplica colores personalizados si detecta alto contraste. En Windows sin soporte conserva el marco del sistema. La documentación oficial de los atributos está en https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute.
