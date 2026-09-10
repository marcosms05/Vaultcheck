# ADR-009 — Exportación cifrada de claves de firma

Estado: codec interno probado con claves sintéticas. No almacén de usuario listo para producción. No se han creado claves reales del usuario ni solicitado contraseñas.

## Primitivas y formato

EncryptedSigningKeyCodec protege una clave privada Ed25519 representada en PKCS#8. Utiliza PBKDF2-HMAC-SHA256 con 600 000 iteraciones y salida de 256 bits, seguido de AES-256-GCM con etiqueta de 128 bits. Cada exportación genera sal de 16 bytes y nonce de 12 bytes mediante SecureRandom.

Las primitivas son del JDK; no hay algoritmos criptográficos propios. El contenedor exterior VCKY es experimental y específico de VaultCheck: NO es un archivo PKCS#12 ni EncryptedPrivateKeyInfo estándar interoperable. PKCS#8 describe únicamente la representación privada cifrada en su interior. Revisar interoperabilidad antes de estabilizar el formato público.

Cabecera big-endian de 39 bytes: magic VCKY (4), versión 1 (1), iteraciones (4), sal (16), nonce (12), longitud cifrada (2). A continuación ciphertext y etiqueta GCM. Toda la cabecera se autentica como AAD.

Máximo 128 bytes de PKCS#8 y 183 bytes de contenedor completo. El desbloqueo verifica límites, versión, coste exacto y longitud antes de derivar la clave. No acepta valores arbitrarios de iteraciones de un archivo importado. Una futura revisión de parámetros requerirá una nueva versión explícita.

## Contraseña y desbloqueo

API mediante char[], sin conversión a String ni registro de secretos. El llamante conserva la propiedad del array y debe borrarlo al finalizar; el codec no modifica memoria ajena. El piloto acepta 12–1024 unidades UTF-16 sin truncamiento. Esta longitud no garantiza entropía: la futura interfaz debe orientar hacia frases de contraseña robustas y permitir gestores de contraseñas.

Contraseña incorrecta y fallo de autenticación del cifrado producen el mismo mensaje. Errores estructurales pueden identificarse antes de derivar. La clave no se devuelve hasta que GCM autentica el contenido y el parser valida Ed25519.

Se borran arrays temporales de texto claro y clave derivada, y se limpia PBEKeySpec. Esto reduce exposición, pero NO garantiza borrado total en una JVM: proveedores, PKCS8EncodedKeySpec, GC y el objeto PrivateKey pueden conservar copias. El llamante debe limitar la vida del objeto desbloqueado a la firma; no hay caché ni servicio de desbloqueo permanente en este componente.

## Recuperación

Una prueba guarda solo el contenedor cifrado en un archivo temporal propio, lo lee de nuevo, desbloquea la clave y firma datos cuya firma valida la clave pública original. Es evidencia de recuperación criptográfica, no una función de backup implementada para el usuario.

Perder contraseña o copia cifrada impide recuperar la clave privada. Conservar la clave pública permite seguir verificando referencias antiguas. No introducir puerta trasera ni recuperación por contraseña maestra.

## Decisión de KDF

Se usa PBKDF2 por disponibilidad en el JDK y para mantener este piloto sin nuevas dependencias. El coste se apoya en la guía de OWASP para PBKDF2; esa guía trata almacenamiento de contraseñas y no constituye una auditoría de este contenedor. No se afirma cumplimiento FIPS. Evaluar Argon2id y medir latencia en hardware objetivo antes de fijar la distribución final.

## Pendiente antes de uso real

- Escritura atómica del contenedor cifrado, permisos locales, rechazo de enlaces en el destino y gestión de reemplazos/cierres inesperados.
- Gestión de altas, copia de seguridad, cambio de contraseña, rotación y revocación de identidades.
- Integrar almacenamiento seguro: EncryptedReferenceSigner ya comprueba la correspondencia privada/pública antes de firmar, pero todavía no guarda referencias reales.
- Interfaz de contraseña fuera de CLI, argumentos, historial o logs; derivación fuera del hilo gráfico.
- Revisión independiente del contenedor y de la carga de claves.
- Protección frente a equipo/proceso comprometido y borrado seguro de SSD no están garantizados.

## Firma con la clave recuperada

EncryptedReferenceSigner exige una identidad admitida por la política antes de desbloquear. Después prueba la correspondencia de la clave privada con esa clave pública mediante una firma Ed25519 interna, separada de los mensajes de manifiesto. Solo si coincide firma la referencia. No publica la prueba interna ni un manifiesto firmado por una identidad distinta.

La clave desbloqueada se mantiene únicamente como variable de la operación. Se intenta destroy() al terminar, pero su disponibilidad depende del proveedor; no se garantiza borrado total en la JVM. Contraseña y contenedor siguen perteneciendo al llamante. Sin caché de claves ni contraseñas.

Se prueba correspondencia correcta, clave cifrada válida de otra identidad, identidad no aprobada antes del desbloqueo y contraseña incorrecta. La demo firmada ejercita también escritura/lectura de fixtures cifrados y verificación de archivos reales.

## Fuentes de criptografía

- [OWASP: Cryptographic Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): modos autenticados como GCM.
- [OWASP: Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): coste de PBKDF2-HMAC-SHA256 y alternativas.
- [OWASP: Key Management](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html): ciclo de vida y protección de claves.
