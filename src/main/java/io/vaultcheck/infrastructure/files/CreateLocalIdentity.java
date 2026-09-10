package io.vaultcheck.infrastructure.files;

import io.vaultcheck.infrastructure.manifest.*;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Creates a new private subdirectory on a fixed local drive; never alters existing ACLs. */
public final class CreateLocalIdentity {
    public record Created(Path directory, Path encryptedKey, Path publicKey, String fingerprint) { }
    public Created create(Path parent, char[] password, BooleanSupplier cancelled) throws Exception {
        PrivateKey privateKey = null;
        try {
            if (password.length < 12 || password.length > 1024) throw new GeneralSecurityException("Invalid password length");
            check(cancelled);
            try (var parentLease = WindowsHandleFingerprintReader.retainDirectory(parent)) {
                var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); privateKey = pair.getPrivate();
                check(cancelled);
                var directory = WindowsPrivateDirectory.create(parent);
                try (var privateLease = WindowsHandleFingerprintReader.retainDirectory(directory)) {
                    // Persistence begins here. Completion reports saved files even if cancellation arrives later.
                    var pub = directory.resolve("public.der");
                    Files.write(pub, pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    WindowsPrivateDirectory.requirePrivateFile(pub);
                    var encrypted = new EncryptedKeyFiles(new EncryptedSigningKeyCodec()).create(directory, privateKey, password);
                    return new Created(directory, encrypted, pub, PinnedSigners.fingerprint(pair.getPublic()));
                }
            }
        } finally {
            Arrays.fill(password, '\0');
            if (privateKey != null) try { privateKey.destroy(); } catch (javax.security.auth.DestroyFailedException unsupported) { /* no cache */ }
        }
    }
    private static void check(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
}
