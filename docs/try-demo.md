# Ejecutar la primera demostración

## Demo completa de firma y recuperación de clave

Desde la raíz del proyecto, con el JAR compilado:

```powershell
java -jar .\target\vaultcheck-0.1.0-SNAPSHOT.jar --vaultcheck.signed-demo=true --spring.main.banner-mode=off --logging.level.root=ERROR
```

Genera una identidad y contraseña sintéticas, guarda la clave cifrada en un archivo temporal, la recupera y comprueba que corresponde a la identidad seleccionada. Firma una referencia y muestra cuatro casos:

La referencia se genera automáticamente mediante recorrido de la carpeta sintética. Un recorrido parcial o con incidencias detendría la demo antes de firmar.

1. Archivo coincidente con firma válida y clave aprobada solo para esta demo.
2. Archivo modificado con firma de referencia todavía válida.
3. Recorrido del inventario actual que detecta un archivo añadido y otro ausente respecto a la referencia.
4. Referencia con firma manipulada, rechazada antes de comparar archivos.

Solo utiliza y limpia sus propios archivos temporales. No solicita contraseña, no utiliza identidades reales y no añade claves a una configuración persistente. La escritura de esos fixtures no es el futuro almacén seguro del producto. Un cierre forzado puede dejar los archivos sintéticos.

La tercera comprobación compara la referencia con el inventario actual observado. El recorrido no es una instantánea del sistema de archivos. El lector nativo conserva las limitaciones documentadas. No usar esta demo como sistema de protección de datos propios.

## Demo básica anterior

Ya existe un [prototipo gráfico con datos ficticios](try-desktop.md). Esta demostración permite ver la comparación funcionando en consola con archivos sintéticos. No acepta carpetas personales ni manifiestos externos.

## Desde VS Code

Abre una terminal en la raíz del proyecto, donde está `pom.xml`. En este equipo ya existe el JAR compilado en target:

```powershell
java -jar .\target\vaultcheck-0.1.0-SNAPSHOT.jar --vaultcheck.demo=true --spring.main.banner-mode=off --logging.level.root=ERROR
```

Requiere Windows con Java 21 o posterior y el directorio temporal en una unidad local fija. La validación se realizó con Java 22.0.2. El comando utiliza Java del PATH, no necesita Maven para ejecutar un JAR ya construido.

Si aún no has compilado, o has modificado el código:

```powershell
.\mvnw.cmd verify
```

Para compilar, JAVA_HOME debe apuntar a un JDK válido. No cambies configuraciones globales para probarlo: puedes establecer la variable solo en la terminal. La primera compilación descarga dependencias; ejecutar la demo no requiere red.

## Resultado esperado

```text
VaultCheck | DEMOSTRACION con datos sinteticos
Firma: NO COMPROBADA | Alcance: solo las tres entradas de prueba
coincidente.txt -> COINCIDENTE
modificado.txt -> MODIFICADO
no-disponible.txt -> NO VERIFICABLE
Resultado: 1 coincidente(s), 1 modificado(s), 1 no verificable(s)
Cobertura: INCOMPLETE | No es una verificacion autenticada.
```

La demo crea dos archivos propios en un directorio temporal único. El tercero no existe intencionadamente. Limpia esos archivos al terminar y se cierra. Un cierre forzado podría dejar el directorio temporal; no se realiza limpieza recursiva sobre carpetas del usuario.

El resultado «No verificable» es deliberadamente conservador: en el caso general, un error al abrir no demuestra ausencia. También puede ser falta de permisos o conflicto de acceso. «INCOMPLETE» significa cobertura incompleta, no un error de la demostración.

Si el entorno impide leer los archivos sintéticos o cargar el lector nativo, la demo falla con código distinto de cero; no declara que haya funcionado. No ejecutar como administrador para sortear ese fallo.

Sin `--vaultcheck.demo=true`, el punto de entrada sigue limitándose a iniciar y cerrar Spring. La ventana de demostración se abre con --vaultcheck.ui=true; ver try-desktop.md.


