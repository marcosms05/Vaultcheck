package io.vaultcheck.infrastructure.demo;

import io.vaultcheck.application.VerifyFolder;
import io.vaultcheck.domain.FolderScan;
import io.vaultcheck.infrastructure.files.WindowsFolderScanner;
import io.vaultcheck.infrastructure.manifest.PinnedSigners;
import io.vaultcheck.infrastructure.manifest.SignedManifestCodec;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Real authenticated comparison using only disposable files and an ephemeral demo identity. */
public final class SyntheticFolderVerification {
    public VerifyFolder.Result run(BooleanSupplier cancelled) throws Exception {
        check(cancelled);
        var folder = Files.createTempDirectory("vaultcheck-ui-demo-");
        var names = List.of("notas.txt", "memoria.txt", "borrador.txt", "inventario.csv", "nuevo.txt");
        try {
            for (int i = 0; i < 4; i++) {
                check(cancelled);
                Files.writeString(folder.resolve(names.get(i)), "Synthetic VaultCheck fixture " + i);
            }
            var scanner = new WindowsFolderScanner();
            var scan = scanner.scan(folder, cancelled, entry -> { });
            check(cancelled);
            if (scan.coverage() != FolderScan.Coverage.COMPLETE) throw new java.io.IOException("Demo reference scan incomplete");
            var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var codec = new SignedManifestCodec();
            var signed = codec.sign(scan.completeReference(Instant.now().getEpochSecond()), key.getPrivate());
            check(cancelled);
            Files.writeString(folder.resolve("memoria.txt"), "Changed synthetic contents");
            Files.delete(folder.resolve("borrador.txt"));
            Files.writeString(folder.resolve("nuevo.txt"), "Added synthetic file");
            var pins = new PinnedSigners(List.of(key.getPublic()));
            return new VerifyFolder(pins, codec, scanner).verify(folder, new ByteArrayInputStream(signed),
                    PinnedSigners.fingerprint(key.getPublic()), cancelled);
        } finally {
            // Exact generated paths only; try every cleanup even if one deletion fails.
            java.io.IOException cleanup = null;
            for (var name : names) {
                try { Files.deleteIfExists(folder.resolve(name)); }
                catch (java.io.IOException e) { if (cleanup == null) cleanup = e; else cleanup.addSuppressed(e); }
            }
            try { Files.delete(folder); }
            catch (java.io.IOException e) { if (cleanup == null) cleanup = e; else cleanup.addSuppressed(e); }
            if (cleanup != null) throw cleanup;
        }
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
}
