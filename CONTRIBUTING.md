# Contribuir

Usamos ramas cortas `feat/descripcion`, `fix/descripcion` y `docs/descripcion` desde `main`. No hay rama permanente develop. Integrar mediante pull request con motivo, cambios y evidencia de validación.

Commits: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `build:`). Cada cambio debe ser revisable y coherente. No incluir credenciales, claves, manifiestos personales ni datos reales.

Ejecutar `./mvnw verify` (o `.\mvnw.cmd verify` en Windows) antes de abrir una PR. Las pruebas de seguridad deben cubrir comportamiento adversario observable, no duplicar la implementación.

Mantener dominio y contratos libres de Spring. No introducir servidor web, elevación de privilegios, telemetría ni escritura sobre el árbol inspeccionado sin revisar requisitos y amenazas.

La publicación del repositorio y configuración de protección de ramas están pendientes; los archivos locales no implican que esas políticas estén activadas en GitHub.
