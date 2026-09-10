package io.vaultcheck.infrastructure.demo;

import io.vaultcheck.application.VerifySignedReference;
import io.vaultcheck.domain.*;
import io.vaultcheck.infrastructure.files.WindowsHandleFingerprintReader;
import io.vaultcheck.infrastructure.files.WindowsFolderScanner;
import io.vaultcheck.infrastructure.manifest.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** End-to-end demo only: ephemeral trusted identity, synthetic password and disposable data. */
@Component
@ConditionalOnProperty(name = "vaultcheck.signed-demo", havingValue = "true")
public final class SignedDemoRunner implements CommandLineRunner {
    @Override public void run(String... args) throws Exception {
        byte[] passwordSeed = new byte[32]; new SecureRandom().nextBytes(passwordSeed);
        char[] password = HexFormat.of().formatHex(passwordSeed).toCharArray();
        Arrays.fill(passwordSeed, (byte) 0);
        var temporary = Files.createTempDirectory("vaultcheck-signed-demo-");
        var data = temporary.resolve("data");
        var sample = data.resolve("sample.txt");
        var added = data.resolve("added.txt");
        var keyFile = temporary.resolve("synthetic.vckey");
        var referenceFile = temporary.resolve("synthetic.vcm");
        try {
            Files.createDirectory(data);
            Files.writeString(sample, "abc");
            var identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var keys = new EncryptedSigningKeyCodec();
            Files.write(keyFile, keys.protect(identity.getPrivate(), password));
            var policies = new PinnedSigners(List.of(identity.getPublic()));
            String id = PinnedSigners.fingerprint(identity.getPublic());
            var codec = new SignedManifestCodec();
            var scan = new WindowsFolderScanner().scan(data, () -> false, entry -> {});
            var manifest = scan.completeReference(java.time.Instant.now().getEpochSecond());
            byte[] encrypted;
            try (var input = Files.newInputStream(keyFile)) {
                encrypted = input.readNBytes(EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES + 1);
            }
            byte[] signed = new EncryptedReferenceSigner(policies, keys, codec).sign(manifest, encrypted, password, id);
            Files.write(referenceFile, signed);
            var verify = new VerifySignedReference(policies, codec, new WindowsHandleFingerprintReader());
            System.out.println("VaultCheck | DEMO FIRMADA - identidad efimera, solo datos sinteticos");
            System.out.println("Clave cifrada recuperada y vinculada a la identidad de prueba.");
            System.out.println("Referencia generada automaticamente mediante recorrido de la carpeta de prueba.");
            try (var input = Files.newInputStream(referenceFile)) {
                var first = verify.verify(data, input, id, () -> false, e -> {});
                if (first.entries().matched() != 1 || first.coverage() != ComparisonSummary.Coverage.COMPLETE) {
                    throw new IllegalStateException("Unexpected baseline result");
                }
                System.out.println("1. Firma valida + identidad aprobada en esta demo: archivo COINCIDENTE.");
            }
            Files.writeString(sample, "abd");
            try (var input = Files.newInputStream(referenceFile)) {
                var changed = verify.verify(data, input, id, () -> false, e -> {});
                if (changed.entries().modified() != 1 || changed.coverage() != ComparisonSummary.Coverage.COMPLETE) {
                    throw new IllegalStateException("Content modification was not detected");
                }
                System.out.println("2. Archivo cambiado: MODIFICADO. La firma de la referencia sigue siendo valida.");
            }
            Files.delete(sample);
            Files.writeString(added, "new synthetic file");
            var folder = new io.vaultcheck.application.VerifyFolder(policies, codec, new WindowsFolderScanner());
            var inventory = folder.verify(data, new ByteArrayInputStream(signed), id, () -> false);
            if (inventory.coverage() != FolderScan.Coverage.COMPLETE || inventory.differences().size() != 2
                    || inventory.differences().stream().noneMatch(d -> d.status() == io.vaultcheck.application.VerifyFolder.Status.ADDED)
                    || inventory.differences().stream().noneMatch(d -> d.status() == io.vaultcheck.application.VerifyFolder.Status.ABSENT)) {
                throw new IllegalStateException("Folder addition or absence was not detected");
            }
            System.out.println("3. Inventario actual: added.txt ANADIDO y sample.txt AUSENTE respecto a la referencia.");
            signed[signed.length - 1] ^= 1;
            try {
                verify.verify(data, new ByteArrayInputStream(signed), id, () -> false, e -> {
                    throw new IllegalStateException("Invalid reference must not publish file results");
                });
                throw new IllegalStateException("Tampered signature was accepted");
            } catch (GeneralSecurityException expectedFailure) {
                System.out.println("4. Firma manipulada: referencia RECHAZADA antes de comparar archivos.");
            }
            System.out.println("Alcance: archivos observados durante el recorrido; no es una instantanea ni certifica seguridad.");
        } finally {
            Arrays.fill(password, '\0');
            // Exact synthetic paths only, no recursive traversal or user-provided destination.
            try { Files.deleteIfExists(added); Files.deleteIfExists(sample); }
            finally { try { Files.deleteIfExists(data); }
                finally { try { Files.deleteIfExists(keyFile); }
                    finally { try { Files.deleteIfExists(referenceFile); }
                        finally { Files.deleteIfExists(temporary); }
                    }
                }
            }
        }
    }
}
