# Paquete portátil de desarrollo para Windows

El paquete actualizado se encuentra en `target/portable-preview/VaultCheck/`; el primero permanece en `target/portable/VaultCheck/`. Abre **VaultCheck.exe** con doble clic. Debes conservar la carpeta completa: `app` contiene la aplicación y `runtime` su Java incluido. Copiar solo el EXE no es suficiente.

Este es un paquete local de desarrollo, no un instalador publicado ni firmado. No modifica el registro ni crea accesos directos mediante instalación. Las identidades que generes se guardan en el destino que tú elijas, fuera del paquete si así lo seleccionas; eliminar el paquete no elimina esas identidades.

## Generar otra copia

Con VaultCheck cerrado, compila el proyecto y ejecuta:

```powershell
.\mvnw.cmd verify
.\scripts\package-portable.ps1 -JdkPath 'C:\ruta\al\jdk' -Destination '.\target\portable-next'
```

El script rechaza un destino que ya contenga VaultCheck, verifica que el JAR es ejecutable y copia únicamente ese JAR al staging. No incluye carpetas de trabajo, pruebas ni claves. La generación actual usa jpackage del JDK 22.0.2 disponible en la torre y todos sus módulos como primera base de compatibilidad. No es todavía un runtime optimizado para tamaño ni el runtime validado para una publicación pública.

## Antes de distribuir públicamente

La iteración comprimida conserva todos los módulos y aplica compresión de recursos, elimina símbolos de depuración, cabeceras y manuales. Incluye java.exe para comprobar los flujos con el mismo runtime distribuido. Ocupa aproximadamente 105,6 MiB, frente a 170 MiB del primer paquete. No se han eliminado módulos por inferencias de dependencias, pues hay carga dinámica de JavaFX y proveedores criptográficos. El runtime de publicación todavía está pendiente de selección. Guía de aceptación en [laptop-test.md](laptop-test.md).

Validación local del 9 de septiembre de 2026: el EXE mostró la ventana «VaultCheck · Prototipo visual» con `JAVA_HOME` vacío y `PATH` limitado a System32. El cierre normal de la ventana terminó ambos procesos del lanzador. La prueba usó una unidad temporal V: y `java.io.tmpdir` dirigido al directorio de pruebas para acomodar el aislamiento del entorno de desarrollo. Esto comprueba arranque y cierre con el runtime incluido, pero no sustituye una prueba en Windows limpio ni repite el flujo funcional completo desde el EXE. La carpeta ocupa aproximadamente 170 MiB; queda pendiente reducir el runtime con pruebas de compatibilidad.

Faltan la validación en Windows limpio sin Java instalado, selección y revisión del runtime de publicación, inventario/licencias de dependencias, icono de aplicación y pruebas de escalado/accesibilidad. La firma de código no está configurada. Un EXE generado no equivale a un instalador, una firma de editor ni una auditoría de seguridad.

El empaquetado no resuelve todavía catálogo persistente, recuperación o rotación de identidades. El flujo actual conserva archivos cifrados y permite seleccionarlos manualmente tras reiniciar. La publicación en GitHub se preparará después de validar estos límites y terminar el acabado acordado.

## Paquete con Java 21

La copia actualizada se genera en target/portable-java21/VaultCheck con Temurin 21.0.12.1+1 (Windows x64). Sustituye el runtime JDK 22 de las primeras pruebas descritas arriba. Incluye LICENSE.txt, LEEME-pruebas.md, dependencies.json y dependency-notices; el runtime conserva su directorio legal. Un inventario de avisos embebidos no completa por sí solo la revisión de licencias: consultar docs/release-preview.md.
