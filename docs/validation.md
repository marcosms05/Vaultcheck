# Validación — 2026-09-08

## Resultado actual

Maven verify: **BUILD SUCCESS**. 137 pruebas, cero fallos, cero errores, cero omisiones. Windows, JDK 22.0.2, bytecode objetivo Java 21, Maven 3.9.11, Spring Boot 4.1.1 y JNA 5.19.1.

| Grupo | Casos |
|---|---:|
| Arranque Spring sin web | 1 |
| Ed25519, alteración y clave incorrecta | 1 |
| Gramática de rutas y límites | 38 |
| Resolución y rechazo de junction | 4 |
| Lectura, cancelación, cambios y buffers | 7 |
| Lectura nativa, compartición y liberación de handles | 9 |
| Comparación, cobertura, cancelación, duplicados y límites | 8 |
| Comparación integrada con archivos reales y lector nativo | 2 |
| Codec firmado, formato y entradas adversarias | 11 |
| Flujo autenticado, política de claves y omisiones | 7 |
| Exportación cifrada, recuperación y manipulación de claves | 7 |
| Firma desde clave cifrada y correspondencia de identidad | 4 |
| Almacenamiento de ciphertext, ACL, staging y recuperación | 7 |
| Recorrido de carpetas, límites, cancelación e incidencias | 8 |
| Inventario autenticado, ausencias conservadoras y ambigüedad | 7 |
| Prueba sintética real y cancelación durante preparación | 3 |
| Revisión, confirmación de huella, copia de bytes y límites | 5 |
| Creación y exportación, contraseña, destino y cancelación | 5 |
| Identidad local, permisos y ciclo de firma/verificación | 3 |

Se ejecutó el JAR con `--vaultcheck.demo=true`: salida de un coincidente, un modificado y un no verificable, cobertura INCOMPLETE y firma NO COMPROBADA; código de salida 0. La demo comprueba internamente esos recuentos y elimina sus archivos propios. Sin interfaz aún. Ver [cómo ejecutarlo](try-demo.md) y [límites del piloto](windows-handle-reader.md).

## Comprobaciones iniciales

Demo firmada actual ejecutada desde JAR con código 0. El manifiesto se genera recorriendo la carpeta sintética; después firma, verifica, detecta modificación de contenido, confirma un archivo añadido y otro ausente mediante el recorrido nativo, y rechaza la firma alterada. No se usaron carpetas del usuario.

La nueva demo `--vaultcheck.signed-demo=true` también se ejecutó desde el JAR con código 0: recuperó la clave cifrada, comprobó su identidad, detectó coincidencia y modificación del archivo y rechazó una firma alterada. La identidad y la contraseña fueron sintéticas, sin persistencia fuera del directorio temporal de la demo.

- Descarga HTTPS de Maven 3.9.11 con verificación SHA-512 del archivo distribuido por Maven Central.
- Generación del Maven Wrapper 3.3.4, distribución Maven 3.9.11: BUILD SUCCESS.
- Compilación directa del dominio, contrato y lector con javac 22.0.2, sin dependencias externas.
- Ejecución de cuatro comprobaciones directas: SHA-256 conocido de `abc`, detección de cambio del mismo tamaño, cancelación y conservación del contenido. Todas superadas. Datos sintéticos en work/.

## Bloqueo anterior, resuelto

El aislamiento impedía a WindowsPath.toRealPath consultar directorios antecesores del espacio de trabajo. Se confirmó con una prueba mínima y el código del JDK instalado. Se creó una unidad temporal V: mediante subst apuntando al espacio de trabajo. Maven utilizó rutas V: para proyecto y caché. Surefire recibió `-DargLine=-Djava.io.tmpdir=V:/work/test-temp` para la JVM de pruebas. La unidad se retiró mediante finally después de cada ejecución. No se cambiaron permisos ni la configuración del proyecto para este entorno.

JAVA_HOME del equipo apuntaba a un JDK 11 antiguo; se corrigió únicamente en procesos de prueba, sin modificar ajustes persistentes. PowerShell/curl fallaron en TLS; la descarga se realizó con urllib y verificación de certificados activa.

El CI está preparado pero no ejecutado remotamente. Pendientes: ejecución real en JDK 21, apertura libre de carreras, parser completo, almacén de confianza y custodia/recuperación de claves, NAS, otros reparse points, JavaFX, EXE y medición del proceso. La prueba Ed25519 usa claves efímeras; no certifica custodia segura.

## Siguiente comprobación

Ejecutar `.\mvnw.cmd verify` en un entorno Windows con JDK 21. La unidad temporal fue una solución para este entorno aislado, no un requisito de VaultCheck.




## Primer prototipo JavaFX

Compilación con JavaFX 21.0.12 y 137 pruebas del motor superadas, sin fallos ni omisiones. Prueba gráfica independiente DesktopSmoke satisfactoria: vacío, carga de cinco ejemplos, filtro, selección, captura de escena y limpieza. La captura se revisó y se corrigió el contraste del placeholder del buscador, volviendo a ejecutar la prueba gráfica. El logo conserva los PNG incrustados del SVG aportado; no hay parser SVG ni descargas de marca en ejecución.

El entorno aislado emite avisos de caché JavaFX no escribible en C:\.openjfx, con carga alternativa temporal funcional, y aviso de módulo sin nombre. No se modificaron permisos del equipo. Pendientes escalado, teclado completo, accesibilidad y conexión de la interfaz a operaciones reales.

Arranque adicional del JAR con `--vaultcheck.ui=true` comprobado mediante javaw: ventana «VaultCheck · Prototipo visual» detectada y cierre normal con código 0. El primer intento del lanzador oculto no expuso una ventana al verificador; la ejecución visible confirmó el flujo real.

## Trabajo gráfico en segundo plano

La ventana utiliza un único executor y `VerificationTask`. Su cancelación es una petición atómica consultada por el motor; no se usa `Task.cancel()`, que podría publicar una terminación antes de liberar recursos. La interfaz solo habilita un nuevo trabajo tras el evento terminal del Task. El servicio elimina únicamente sus cinco nombres conocidos y su carpeta temporal; intenta todas las eliminaciones y comunica fallos. Un fallo de limpieza impide publicar éxito o cancelación limpia.

La prueba real utiliza una identidad Ed25519 efímera sin custodia persistente. No sustituye el flujo de desbloqueo del almacén de claves. El cierre normal durante la tarea requiere decisión y espera; un cierre forzado del proceso puede dejar datos sintéticos. La cancelación no interrumpe una llamada nativa bloqueada.



Validación de esta conexión: Maven verify, 137 pruebas, cero fallos/errores/omisiones. DesktopWorkerSmoke comprobó controles ocupados, bucle gráfico disponible, cancelación confirmada, reinicio y cinco resultados autenticados. DesktopSmoke volvió a comprobar búsqueda, selección y limpieza. El cierre con diálogo y la I/O bloqueada siguen pendientes de una prueba gráfica específica.

## Selector de carpeta e inventario

La selección nativa solo conserva una ruta en memoria. Un botón separado inicia `FolderScanTask`, con el mismo control de exclusión y cancelación cooperativa de la demo. La interfaz muestra lecturas e incidencias y no interpreta rutas como marcado. Los resultados no se califican de coincidentes sin referencia autenticada. La copia íntegra del hash se muestra en el inspector; no se realizan escrituras en la carpeta elegida ni se persiste su ruta.

La prueba gráfica DesktopFolderSmoke sustituye únicamente el diálogo de selección por una carpeta temporal sintética, ejecuta los manejadores reales, espera al motor, comprueba SHA-256 de `abc`, alcance explícito y conservación del contenido. El diálogo nativo interactivo y el cierre durante I/O bloqueada requieren revisión manual; la prueba no los simula como validados.

Resultado de este hito: Maven verify satisfactorio, 137 pruebas sin fallos/errores/omisiones. DesktopFolderSmoke, DesktopWorkerSmoke y DesktopSmoke completados. Captura actualizada y revisada tras incorporar el selector.



## Referencia y aprobación explícita

Maven verify: 137 pruebas, cero fallos/errores/omisiones. Cinco pruebas nuevas verifican aprobación exacta, copia estable frente a mutación, firma alterada, DER canónico y límites de importación. Se repitieron las tres pruebas gráficas existentes de inventario, trabajo/cancelación y presentación. En ese hito aún no se probaban los manejadores del nuevo panel; la comprobación posterior se detalla a continuación. Los diálogos nativos siguen pendientes de revisión manual. Ver desktop-reference-review.md.


## 2026-09-09 — Flujo de confianza en JavaFX

Se añadió DesktopReferenceSmoke, separado de las pruebas sin interfaz. Sustituye únicamente las respuestas de los diálogos y ejecuta el flujo real: importa, rechaza una huella incorrecta, aprueba la correcta, compara los bytes revisados pese a una mutación del archivo original, invalida confianza al reseleccionar la clave y rechaza la firma manipulada. No utiliza claves privadas persistentes ni carpetas del usuario. La prueba no certifica que una huella introducida por el usuario proceda realmente de un canal independiente.

Resultado: Maven verify satisfactorio, 137 pruebas sin fallos, errores ni omisiones. DesktopReferenceSmoke completado con código 0. JAR reconstruido con la separación de diálogos y los nombres accesibles.



## 2026-09-09 — Creación y publicación

Maven verify: 137 pruebas sin fallos, errores ni omisiones. Cinco pruebas nuevas cubren exportación verificable con borrado del char[], no reemplazo, rechazo de destino interno, contraseña incorrecta y cancelación previa a publicación. La interfaz permite preparar y firmar con una clave cifrada existente; aún falta creación de identidades y prueba específica de los nuevos diálogos. Ver create-reference.md.

Las cuatro comprobaciones gráficas existentes (referencia, inventario, trabajador y presentación) también terminaron con código 0. Captura actualizada y revisada: la navegación muestra Crear referencia y el panel plegable mantiene el espacio de resultados.



## 2026-09-09 — Identidades locales cifradas

Maven verify: 137 pruebas sin fallos, errores ni omisiones. Se comprobó creación con ACL, cifrado, firma de una referencia y verificación con la misma identidad, además de cancelación previa y contraseña no válida sin crear archivos. Se sobrescriben las matrices propias de contraseña. Las pruebas utilizan carpetas y contraseñas sintéticas. Los diálogos nuevos de identidad y exportación siguen pendientes de una prueba gráfica específica; los servicios sí se ejecutaron de extremo a extremo. Ver local-identities.md.

## 2026-09-09 — Creación desde controles y diálogos

DesktopCreationSmoke completado con código 0. Usa diálogos reales de contraseña de JavaFX y sustituye únicamente las respuestas de los selectores de archivos/carpetas del sistema. Comprueba cancelación sin iniciar trabajos, limpieza de PasswordField, creación de identidad, preparación, exportación y verificación nativa del resultado. Se corrigió el arnés de prueba para abrir modales desde Platform.runLater, fuera del pulso de animación; no fue un fallo del flujo normal de usuario.

Maven verify mantiene 137 pruebas, cero fallos/errores/omisiones. El script scripts/test-desktop.ps1 permite repetir la compilación y cinco comprobaciones gráficas. Su sintaxis PowerShell se validó; en este entorno las comprobaciones se ejecutaron con la unidad temporal V: documentada, no mediante el wrapper del script.

## 2026-09-09 — Validación manual comunicada por el usuario

El usuario abrió la aplicación y confirmó: archivo sin cambios → Coincidente; edición de los colores de una imagen → Modificado; cambio de nombre → Añadido para la ruta nueva y Ausente para la anterior. Es evidencia del recorrido manual de comparación, no una auditoría completa de seguridad ni una prueba de todos los errores posibles.

## Primera aproximación visual a la maqueta

Navegación por operación con selección lateral: solo se muestra el panel correspondiente a Verificar, Crear referencia o Identidades. Las herramientas sintéticas se ofrecen mediante Ver → Herramientas de demostración. El inspector empieza cerrado, se abre al seleccionar una fila y puede cerrarse devolviendo el foco a la tabla. Se mantiene la referencia visual docs/mockups/verify-folder.svg; aún faltan pestañas/filtros de resultados, afinado de tamaños y comprobaciones de escalado y accesibilidad.

Validación de la iteración visual: 137 pruebas y las cinco comprobaciones gráficas superadas. DesktopSmoke comprueba además inspector cerrado inicialmente y abierto al seleccionar. Se revisó la captura real actualizada. Windows bloqueó el primer empaquetado porque la aplicación estaba abierta; tras el cierre confirmado por el usuario, se repitió el empaquetado sin repetir las pruebas ya superadas.

## Filtros combinados de resultados

Todos, Diferencias, No verificables e Incidencias pueden combinarse con búsqueda. Los recuentos representan el resultado completo; el contador visible muestra el subconjunto. La cobertura y la autenticidad no cambian por filtrar. Incidencias puede solaparse con No verificables; son filas de información, no un total de archivos únicos.

DesktopSmoke comprueba el filtro de tres diferencias de la muestra, búsqueda combinada, recuento global constante, subconjunto vacío y vuelta a Todos. Al obtener filas se pliega la configuración de referencia para dejar espacio a resultados. Maven ejecutó 137 pruebas sin errores; el primer empaquetado encontró de nuevo el JAR bloqueado por Windows.

Filtros validados: 137 pruebas y cinco comprobaciones gráficas satisfactorias. Tras cerrar la aplicación, empaquetado correcto del JAR. Se desactivó la animación de plegado de la referencia para liberar inmediatamente espacio de resultados; se volvió a comprobar y capturar la vista. Los filtros no alteran ni ocultan el resumen global de cobertura.

## Primer paquete portátil de Windows

Generado con scripts/package-portable.ps1 a partir del JAR ejecutable validado. Arranque local comprobado sin JAVA_HOME y con PATH limitado a System32; ventana identificada en el proceso secundario del lanzador y cierre normal confirmado sin procesos restantes. La detección inicial sobre el PID padre no encontraba la ventana; no era evidencia de fallo de arranque. Se retiró la unidad temporal V: utilizada para la prueba. Condiciones y límites en docs/portable-windows.md. Tamaño aproximado: 170 MiB con todos los módulos del JDK 22.0.2. Pendiente prueba funcional completa desde EXE y Windows limpio antes de publicación.

## Afinado visual y runtime comprimido

Maven verify satisfactorio después de ajustar tabla, estados con texto y punto de color, filtros junto a búsqueda y jerarquía de resultados. Las cinco comprobaciones gráficas (DesktopSmoke, DesktopWorkerSmoke, DesktopFolderSmoke, DesktopReferenceSmoke y DesktopCreationSmoke) se ejecutaron con java.exe del runtime comprimido: todas superadas, incluidos los diálogos de contraseña y los flujos criptográficos/nativos. Estas pruebas usan el classpath compilado de pruebas; no sustituyen el recorrido manual del JAR empaquetado a través del EXE. La compresión conserva todos los módulos y reduce la carpeta de unos 170 a 105,6 MiB. La prueba en el portátil queda pendiente del usuario, siguiendo docs/laptop-test.md.

## Java 21 y adaptación de ventanas

Validación del 9–10 de septiembre de 2026: Maven verify y cinco recorridos gráficos superados con Temurin 21.0.12.1+1 para Windows x64. Descarga oficial contrastada con SHA-256 f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e. Se comprobaron escalas JavaFX forzadas al 100 %, 125 %, 150 % y 200 %, además de contenido desplazable en ventana pequeña e inspector vertical cuando falta anchura. Esto no cambia el escalado del sistema y no sustituye pruebas de DPI por monitor ni selectores nativos en el portátil.

La barra lateral puede ocultarse con Ver → Mostrar barra lateral o Ctrl+B. La ventana inicial se limita al área útil de la pantalla y el contenido conserva desplazamiento vertical. README actualizado a las capacidades reales. El paquete incorpora inventario de JARs y avisos embebidos; las dependencias sin aviso embebido siguen requiriendo revisión antes de distribuir el binario públicamente.

## Corrección de propiedad en Windows administrativo

El primer CI público ejecutó 137 pruebas y produjo ocho errores «Storage is not owned by current user». Los fallos estaban en la creación del almacén privado. Windows puede asignar como propietario predeterminado un grupo del token administrativo. VaultCheck ahora fija el usuario propietario únicamente tras crear exclusivamente una carpeta/archivo nuevo y antes de persistir sus datos. Mantiene la validación estricta y no repara permisos de almacenes existentes. Las referencias a rutas siguen teniendo los límites de concurrencia documentados; este cambio no los elimina.

Validación local con Temurin 21: 139 pruebas satisfactorias. Nuevas comprobaciones: el padre conserva propietario/ACL y la creación de clave pública no sobrescribe contenido existente. Falta confirmar el resultado en el runner remoto administrativo. Las acciones de checkout y setup-java se actualizaron a versiones basadas en Node 24, fijadas por SHA de commit. El resumen de JUnit se publica en el resumen del trabajo.

Fuente del comportamiento del propietario: https://learn.microsoft.com/en-us/windows/win32/secauthz/owner-of-a-new-object
