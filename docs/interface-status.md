# Estado de interfaz y distribución

## Implementado

- Navegación por Verificar, Crear referencia e Identidades.
- Flujos reales, filtros combinados, búsqueda, estados de trabajo y cancelación cooperativa.
- Inspector contextual, vertical en anchura reducida; contenido desplazable y lateral plegable con Ctrl+B.
- Pruebas JavaFX con escala forzada 100/125/150/200 % y ventana pequeña.
- Logo en la barra lateral, icono de ventana y recurso del ejecutable.

## Pendiente de interfaz

- Acercar la configuración y el resumen a verify-folder.svg: sustituir la composición de paneles desplegables por la jerarquía final acordada.
- Diseñar las vistas Resumen/Archivos/Incidencias sin confundirlas con los filtros actuales.
- Exponer tamaños y hashes de referencia/actual en los resultados reales: el inspector de comparación muestra estado y evidencia de firma, no ambos hashes.
- Revisar rutas largas, textos de error y navegación por teclado en cada diálogo; lector de pantalla y alto contraste todavía no validados.
- Probar escalado real del portátil, cambios entre monitores y selectores nativos de Windows. La prueba forzada de JavaFX no sustituye esta comprobación.

## Pendiente de distribución

- Confirmar CI verde con la corrección de propietario y registrar la prueba del portátil.
- Completar avisos de dependencias que no los incorporan en sus JARs y documentar los términos de marca.
- Activar el canal privado de seguridad y actualizar SECURITY.md.
- Publicar ZIP y SHA-256 como prerelease, vinculados al commit comprobado.
- Decidir posteriormente firma de editor e instalador; la versión portátil no depende de contar con instalador.

Catálogo persistente, recuperación/rotación de identidades, informes, NAS y grandes inventarios son ampliaciones funcionales posteriores, no tareas de acabado visual.
