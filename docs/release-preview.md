# VaultCheck 0.1.0 · Borrador de versión preliminar

Aplicación local para Windows x64 que crea referencias firmadas y compara archivos sin modificar su contenido. Incluye runtime Java; no requiere instalación de Java ni administrador.

## Incluido

- Identidades Ed25519 cifradas con contraseña, con aprobación explícita de huella al verificar.
- Creación de referencias de recorridos completos y comparación con estados e incidencias explícitos.
- Filtros, búsqueda, inspector adaptable y desplazamiento en ventanas pequeñas.
- Paquete portátil comprimido con inventario SHA-256 de dependencias, avisos embebidos y licencias del runtime.

## Límites de esta versión

Solo discos locales fijos de Windows x64. Límite de 1.000 archivos, 4.096 visitas y 32 niveles. Sin NAS, catálogo persistente, recuperación de contraseña ni informes exportables. El recorrido no es una instantánea ni un análisis de malware. El EXE no está firmado por un editor.

## Comprobaciones antes de publicar

- [ ] Destino GitHub confirmado y acceso autenticado disponible.
- [ ] Revisar cambios, exclusión de datos privados e inventario de dependencias.
- [ ] Completar avisos de dependencias que no los incorporen en su JAR y licencia del logo.
- [ ] Activar avisos privados de seguridad y enlazarlos en SECURITY.md.
- [ ] Ejecutar CI en GitHub con JDK 21.
- [ ] Registrar prueba del portátil y escalado real de Windows.
- [ ] Adjuntar ZIP y SHA-256 al mismo commit/tag verificado, como prerelease.

Este archivo es un borrador: no implica que exista un repositorio remoto, una release ni las protecciones de rama descritas en CONTRIBUTING.md.
