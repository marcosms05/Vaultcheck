# VaultCheck — Diseño de interfaz

Estado: JavaFX con logo, verificación real, creación de referencias e identidades cifradas, filtros combinados, búsqueda e inspector. Este documento conserva la dirección visual; no todos sus detalles están implementados (pestañas, adaptación a ventanas pequeñas, informes y catálogo persistente pendientes). Ver docs/try-desktop.md.

## Intención

Una herramienta oscura, tranquila y precisa. La información debe poder revisarse sin esfuerzo: qué carpeta se analiza, contra qué referencia, qué se ha comprobado y qué queda sin verificar. La jerarquía visual debe transmitir esos hechos sin exagerar las garantías.

La implementación prevista sigue siendo JavaFX con motor Java/Spring Boot, en Windows. El diseño no introduce servidor HTTP, servicios externos ni nuevas funciones de producto.

## Referencias y adaptación

| Referencia aportada | Qué adoptamos |
|---|---|
| Captura de Codex | Barra lateral serena, selección con fondo suave, controles discretos, separación de paneles y radios moderados |
| Captura de Postman | Organización del espacio de trabajo, configuración arriba, resultados debajo y detalles contextuales a la derecha |
| clickhouse-DESIGN.md | Superficies oscuras, tipografía clara, escala de espaciado y acento amarillo lima usado con moderación |

El documento adjunto es material de referencia, no instrucciones operativas del proyecto. Sus indicaciones de consultar una web o preservar todos los tamaños se interpretan en su contexto original. Aquí adaptamos el estilo a una utilidad de escritorio, sin titulares de marketing, precios, bandas promocionales ni dependencia del sitio de origen.

Las capturas son referencias visuales; no se incorporarán al repositorio público, pues contienen información personal. No copiar logotipos, marcas ni contenido privado. La inspiración de estilo Apple se traduce en orden, proporción y detalles; conservar los controles de ventana y convenciones de Windows.

## Composición

Ventana de referencia: 1440 × 900 unidades lógicas. Validar también 1024 × 700 y escalado de Windows al 125 %, 150 % y 200 %.

1. Barra de título de Windows y menú compacto: Archivo, Ver, Ayuda. Conservar mover, maximizar, cerrar y navegación del sistema.
2. Barra lateral de 224–256 unidades: marca VaultCheck, Verificar carpeta, Crear referencia y, al pie, Ajustes. Referencias recientes solo cuando exista persistencia real; no simular historial funcional.
3. Área principal flexible: título de la operación y configuración de carpeta/referencia, con una acción primaria alineada a la derecha.
4. Resultados debajo de la configuración: resumen breve, filtros y tabla. Debe ocupar la mayor parte del espacio durante la revisión.
5. Inspector contextual de 280–320 unidades, cerrado inicialmente; muestra detalles de la fila seleccionada. Se puede cerrar y no contiene navegación global.

En anchuras reducidas el inspector pasa a ser un panel superpuesto cerrable y la barra lateral puede plegarse. La configuración se reorganiza en vertical antes de truncar botones. Las tablas pueden desplazarse horizontalmente; las acciones principales no deben quedar fuera de pantalla.

Tomamos las pestañas de Postman para vistas de una misma operación: Resumen, Archivos e Incidencias. No habrá múltiples trabajos concurrentes en pestañas en el primer MVP. Menos paneles visibles cuando aún no hay resultados.

## Flujos

### Verificar carpeta

- Encabezado: «Verificar carpeta» y explicación breve de la comparación contra una referencia.
- Campo Carpeta, con ruta de solo lectura y botón «Elegir carpeta».
- Campo Referencia, con archivo seleccionado y botón «Elegir referencia».
- Estado de referencia: autenticidad y confianza visibles antes de iniciar.
- Acción «Verificar», habilitada únicamente cuando los requisitos de la operación se cumplan. Explicar junto al campo lo que falta; no depender de un tooltip en un botón deshabilitado.
- Durante lectura: progreso, archivos y bytes procesados, incidencias y «Cancelar». No permitir un segundo inicio.
- Resultado: cobertura, diferencias y confianza en zonas separadas; tabla y «Exportar informe».

No inventar porcentaje ni tiempo restante mientras el total sea desconocido. Usar indicador indeterminado y contadores observables. No ofrecer pausa hasta que exista soporte real.

### Crear referencia

- Selección de carpeta y destino externo al árbol analizado.
- Identidad firmante con acceso a sus detalles.
- Resumen de lectura y omisiones antes de firmar/guardar.
- Si falta acceso a la clave privada, solicitar desbloqueo en el momento de firmar.
- Una referencia parcial debe conservar la etiqueta «Incompleta» aunque su firma sea válida.

Los controles de firma se incorporarán cuando exista su implementación. No simular autenticidad o claves reales en el prototipo; cualquier dato de muestra se etiqueta como demostración.

## Tokens visuales

Nombres conceptuales compartidos entre documentación y futura hoja de estilos JavaFX. No implican uso de CSS de navegador.

| Token | Valor inicial | Uso |
|---|---|---|
| canvas | #121212 | Fondo principal |
| sidebar | #1A1A1A | Navegación lateral |
| surface | #1A1A1A | Campos y grupos |
| elevated | #242424 | Menús e inspector |
| selected | #303030 | Selección de navegación o fila |
| divider | #2A2A2A | Separación decorativa |
| control-border | #777777 | Límites de controles que necesitan identificación |
| text-primary | #FFFFFF | Títulos y contenido principal |
| text-body | #CCCCCC | Texto secundario legible |
| text-muted | #A3A3A3 | Metadatos |
| accent | #FAFF69 | Acción principal y foco |
| accent-hover | #F0F55F | Hover de acción principal |
| accent-pressed | #E6EB52 | Pulsación |
| on-accent | #0A0A0A | Texto e icono sobre acento |
| success | #22C55E | Coincidencia o comprobación correcta, con texto |
| warning | #F59E0B | Cobertura incompleta o atención, con texto |
| error | #EF4444 | Fallo o referencia alterada, con texto |

Los colores semánticos se usan inicialmente como icono/borde junto a texto claro, no como texto pequeño sobre cualquier superficie. Comprobar contraste por combinación real durante el prototipo. Los divisores sutiles no son el único límite visible de un control.

No colorear grandes paneles en amarillo ni usarlo para indicar «seguro». Una acción primaria destacada por área de trabajo. Sin gradientes decorativos, resplandores ni grandes sombras; elevación mínima en menús superpuestos.

## Tipografía y ritmo

- Fuente de interfaz: Segoe UI para la primera versión Windows. Inter puede evaluarse si se empaqueta localmente con su licencia; no descargar fuentes al ejecutar.
- Monoespaciada: Consolas para hashes y detalles técnicos. Nombres y rutas habituales mantienen la fuente de interfaz para facilitar lectura.
- Título principal: 22–24; título de sección: 16–18; texto y controles: 14; metadatos: 12–13 unidades lógicas.
- Pesos 400 y 600; no convertir todos los encabezados en mayúsculas. Alineación izquierda salvo cantidades numéricas.
- Escala de espaciado: 4, 8, 12, 16, 24 y 32. Separación principal 24; interior de paneles 16–24; icono/texto 8.
- Altura de campos y botones: 36–40; filas: 36–40, sin forzar alturas que recorten texto con escalado.
- Radios: controles 8, selección lateral 8, paneles 12. Cápsulas solo para etiquetas pequeñas. Evitar una tarjeta redondeada por cada dato.

## Detalles de interacción

| Componente | Comportamiento |
|---|---|
| Botón principal | Texto oscuro sobre lima; hover y pulsación con cambio de tono, sin cambiar tamaño |
| Botón secundario | Fondo neutro, texto claro; hover visible y borde cuando sea necesario |
| Botón de icono | Área de interacción mínima 32 × 32; icono 16–18; tooltip y nombre accesible |
| Selección lateral | Fondo suave, etiqueta clara e indicador de selección accesible |
| Foco | Anillo visible de 2 unidades, separado del control; nunca depender solo del hover |
| Campo erróneo | Mensaje junto al campo e icono; conservar la selección válida del usuario |
| Fila | Hover discreto, selección persistente y acceso al inspector con teclado |
| Menú | Etiquetas concretas, atajos visibles cuando existan; acciones frecuentes fuera del menú de puntos |

Animaciones de 100–150 ms para hover/apertura, sin desplazamientos ornamentales; respetar reducción de movimiento. Iconos lineales consistentes, sin emojis como iconografía principal. Comprobar licencia antes de incorporar una biblioteca de iconos.

## Resultados y seguridad visibles

Tres dimensiones independientes, siempre expresadas en texto:

| Dimensión | Ejemplos |
|---|---|
| Referencia | Firma válida · identidad confiable; identidad desconocida; firma inválida |
| Cobertura | Completa; incompleta; cancelada |
| Comparación | Coincidente; modificado; nuevo; ausente; no verificable |

«No se encontraron diferencias» debe ir acompañado de «en los archivos comprobados» cuando la cobertura sea parcial. Nunca mostrar «Todo seguro» ni un porcentaje de seguridad. No inferir malware a partir de una diferencia.

Una firma inválida bloquea la verificación autenticada. Una identidad desconocida abre revisión explícita de huella; no confiar automáticamente en la clave importada. Revisar identidad y volver a verificar son acciones diferentes.

Cancelar muestra «Cancelando…» hasta confirmación del motor; después muestra cobertura cancelada. Cerrar durante una operación requiere una decisión concreta de continuar o cancelar y cerrar. No mostrar confirmaciones para navegación rutinaria o selección de archivos.

Tabla principal: nombre/ruta relativa, estado y tamaño; detalles de hashes en el inspector. Filtros con recuentos por estado y búsqueda local. Los nombres de archivo se presentan como texto literal; no interpretarlos como HTML, enlaces o marcado. La ruta completa puede consultarse y copiarse explícitamente; no publicar rutas personales en diagnósticos por defecto.

Virtualizar filas para grandes volúmenes y agrupar actualizaciones de progreso. Ninguna lectura de disco, hash o firma en el hilo gráfico. El hilo de interfaz debe permanecer disponible para cancelar y navegar.

## Accesibilidad y comprobación del prototipo

- Orden de foco coherente, operación completa por teclado y retorno de foco al cerrar menús/inspector.
- Etiquetas accesibles para campos e iconos; no depender de placeholder, color o tooltip.
- Objetivos de contraste: 4,5:1 para texto normal y 3:1 para indicadores y límites de controles relevantes, verificados con los colores finales.
- Comprobar zoom/escalado, textos largos, rutas UNC largas y anuncios de estado sin inundar al lector de pantalla.
- Respetar alto contraste y reducción de movimiento del sistema; validar soporte efectivo en JavaFX.
- Probar vacío, preparado, en curso, cancelando, cancelado, completo, incompleto, error y referencia no confiable.

## Próximo entregable visual

Prototipo de «Verificar carpeta» con estado inicial y resultados de demostración, manteniendo la misma composición. Debe permitir revisar hover, foco, selección, inspector y tamaños antes de conectar operaciones reales. La protección del acceso a archivos y la gestión de claves conservan sus hitos de seguridad pendientes.



## Iteración tras la prueba manual

La ventana separa las operaciones mediante navegación lateral, sitúa las demos en el menú Ver y abre el inspector bajo selección. La maqueta verify-folder.svg sigue siendo el objetivo. No se consideran completadas las pestañas, filtros, escalado ni accesibilidad.

