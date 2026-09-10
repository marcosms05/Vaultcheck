# Probar la ventana de VaultCheck

Desde la carpeta del proyecto, con Java 21 o posterior:

```powershell
.\mvnw.cmd verify
java -jar .\target\vaultcheck-0.1.0-SNAPSHOT.jar --vaultcheck.ui=true
```

El JAR ya compilado permite omitir el primer comando. Validado localmente con JDK 22 en Windows. La primera compilación descarga JavaFX; la ventana utiliza únicamente recursos locales.

Para los datos sintéticos, activa **Ver → Herramientas de demostración**. Pulsa **Mostrar resultados de ejemplo**, busca `memoria`, selecciona una fila y revisa el inspector. **Limpiar vista** restaura el estado vacío. El separador permite ajustar el ancho del inspector. Archivo → Cerrar termina la ventana y cierra el contexto de Spring.

En las herramientas de demostración, pulsa **Ejecutar prueba real** para crear archivos sintéticos temporales, generar una referencia con firma Ed25519 y compararla con el motor nativo. Se muestran dos coincidencias, un modificado, un añadido y un ausente. La identidad es efímera y aprobada exclusivamente para esa ejecución. El inspector distingue estos resultados de los ejemplos visuales.

**Cancelar** solicita la parada cooperativa y mantiene «Cancelando…» hasta terminar el motor y la limpieza. No se permiten dos trabajos simultáneos. Cerrar mientras trabaja permite continuar o cancelar y cerrar al finalizar. Una operación nativa bloqueada puede retrasar la cancelación; no se fuerza su terminación.

Ya permite elegir una carpeta local con **Elegir carpeta** y pulsar **Inventariar carpeta**. Elegirla no inicia la lectura. El inventario muestra tamaño y SHA-256 por archivo, además de incidencias, sin modificar su contenido. Su estado es **Leído · Sin comparar** y la autenticidad permanece **no comprobada**. No se ha seleccionado ni verificado una referencia firmada.

El piloto admite unidades locales fijas de Windows, hasta 1.000 archivos, 4.096 entradas visitadas y 32 niveles. Un límite o incidencia conserva cobertura parcial. NAS y unidades extraíbles no están admitidos. La cancelación conserva las lecturas ya completadas con cobertura cancelada. La ventana no persiste la carpeta elegida.

El panel **Referencia firmada e identidad** permite elegir un archivo `.vcm` del formato experimental VaultCheck y una clave pública Ed25519 en DER SubjectPublicKeyInfo (no PEM ni clave privada). Pulsa **Revisar firma**: una firma válida no aprueba la identidad. **Aprobar identidad…** exige introducir los 64 caracteres minúsculos de la huella SHA-256 confirmada con el propietario por un canal independiente. Después, **Verificar referencia** compara la carpeta elegida.

La aprobación permanece solo en memoria y está ligada a la selección revisada. Cambiar el archivo o la clave la invalida. Se comparan los bytes revisados, aunque el archivo original cambie posteriormente. El producto no puede comprobar cómo obtuviste la huella; copiarla desde la propia importación no aporta confianza independiente. La barra lateral **Crear referencia** permite preparar y exportar con una clave cifrada existente: ver [crear una referencia](create-reference.md). Ya puedes generar una identidad desde **Identidades → Crear identidad cifrada…**; ver [identidades locales](local-identities.md). El catálogo persistente, rotación y recuperación siguen pendientes. Los ejemplos visuales y la prueba sintética siguen disponibles y se identifican por separado. No se registran rutas temporales ni excepciones completas en la interfaz. Si falla la operación o la limpieza, se muestra un error conservador y pueden quedar archivos sintéticos.

JavaFX 21.0.12, fijado en Maven, corresponde a la [actualización oficial de julio de 2026](https://gluonhq.com/news/2026-07-21-july-2026-critical-patch-update/). Se usa el lanzamiento no modular desde la aplicación Java; puede aparecer el aviso JavaFX de clases en módulo sin nombre. El empaquetado modular y el instalador aún no están validados.

La prueba gráfica explícita `DesktopSmoke` comprueba vacío, datos de ejemplo, búsqueda y limpieza, además de seleccionar una fila y renderizar una captura. No sustituye la revisión manual de teclado, lector de pantalla ni escalado. Se ejecuta separadamente de las 137 pruebas del motor porque requiere sesión gráfica. La captura guardada en docs/mockups/desktop-preview.png procede de esa ventana, no de una imagen generada.










## Repetir la validación gráfica

En una sesión interactiva de Windows, desde la raíz del proyecto:

```powershell
.\scripts\test-desktop.ps1
```

Necesita JDK 21 o posterior y JAVA_HOME correcto si está definido. Ejecuta Maven verify y después las cinco comprobaciones gráficas, deteniéndose ante el primer fallo. Abre y cierra ventanas de prueba y utiliza archivos/contraseñas sintéticos. No requiere ejecutar como administrador. La primera compilación puede descargar dependencias.

DesktopCreationSmoke recorre la creación de identidad y la preparación/exportación con diálogos reales de contraseña de JavaFX; sustituye solo las respuestas de los selectores de archivos de Windows. Verifica que cancelar no lance tareas ni exporte, que los PasswordField queden vacíos y que la referencia producida se pueda verificar. Limpiar controles no demuestra el borrado de todas las copias de memoria de la JVM.


La barra lateral cambia entre operaciones y conserva las selecciones de la sesión. El inspector aparece al seleccionar un archivo; **Cerrar** devuelve el espacio a la tabla. Durante un trabajo, la navegación se bloquea hasta terminar o confirmar la cancelación.


## Filtros de resultados

Los filtros Todos, Diferencias, No verificables e Incidencias se combinan con la búsqueda de rutas. Los recuentos de cada categoría corresponden al resultado completo, no a la búsqueda activa. «N de M filas visibles» informa de lo que se está mostrando. Una vista filtrada vacía no declara que no haya diferencias ni que la cobertura sea completa: el resumen permanece visible.

Una incidencia de lectura puede aparecer también en No verificables. Los recuentos no son categorías disjuntas ni deben sumarse para calcular archivos únicos. Diferencias incluye Modificado, Añadido y Ausente, excluyendo incidencias. Al iniciar un nuevo resultado se restablece Todos.
