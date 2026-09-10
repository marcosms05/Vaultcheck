package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.domain.*;
import io.vaultcheck.infrastructure.files.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Prepare first; unlock an existing encrypted identity only when the user exports. */
public final class CreateSignedReference {
    public static final class Prepared {
        private final Path root;
        private final ReferenceManifest manifest;
        private Prepared(Path root, ReferenceManifest manifest) { this.root = root; this.manifest = manifest; }
        public int entries() { return manifest.entries().size(); }
    }
    public Prepared prepare(Path root, BooleanSupplier cancelled) throws Exception {
        check(cancelled);
        Path selected = root.toAbsolutePath().normalize();
        var scan = new WindowsFolderScanner().scan(selected, cancelled, entry -> {});
        check(cancelled);
        return new Prepared(selected, scan.completeReference(Instant.now().getEpochSecond()));
    }
    public String export(Prepared prepared, Path destination, Path encryptedKey, Path publicKey,
                         char[] password, BooleanSupplier cancelled) throws Exception {
        Objects.requireNonNull(prepared);
        try {
            check(cancelled);
            byte[] pub = read(publicKey, 128);
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(pub));
            if (!Arrays.equals(pub, key.getEncoded())) throw new GeneralSecurityException("Noncanonical public key");
            String identity = PinnedSigners.fingerprint(key);
            var signer = new EncryptedReferenceSigner(new PinnedSigners(List.of(key)), new EncryptedSigningKeyCodec(), new SignedManifestCodec());
            byte[] bytes = signer.sign(prepared.manifest, read(encryptedKey, EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES), password, identity);
            check(cancelled);
            new ReferencePublication().publish(prepared.root, destination, bytes);
            // Publication is the commit point: cancellation after this point must not hide the saved file.
            return identity;
        } finally { Arrays.fill(password, '\0'); }
    }
    private static byte[] read(Path file, int maximum) throws java.io.IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new java.io.IOException("Ordinary file required");
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) throw new java.io.IOException("Input exceeds limit");
            return bytes;
        }
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
}

