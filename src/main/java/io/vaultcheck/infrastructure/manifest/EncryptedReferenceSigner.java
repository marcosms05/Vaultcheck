package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.application.TrustedSignerSource;
import io.vaultcheck.domain.ReferenceManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Objects;
import javax.security.auth.DestroyFailedException;

/** Unlocks only for this operation; rejects a valid encrypted key belonging to another identity. */
public final class EncryptedReferenceSigner {
    private final TrustedSignerSource identities;
    private final EncryptedSigningKeyCodec keys;
    private final SignedManifestCodec manifests;

    public EncryptedReferenceSigner(TrustedSignerSource identities, EncryptedSigningKeyCodec keys,
                                    SignedManifestCodec manifests) {
        this.identities = Objects.requireNonNull(identities);
        this.keys = Objects.requireNonNull(keys);
        this.manifests = Objects.requireNonNull(manifests);
    }

    public byte[] sign(ReferenceManifest reference, byte[] encryptedKey, char[] password, String identity)
            throws GeneralSecurityException, IOException {
        Objects.requireNonNull(reference);
        var publicKey = identities.requireTrusted(identity);
        var privateKey = keys.unlock(encryptedKey, password);
        try {
            // Domain-separated local check; never returned, persisted or accepted as a manifest.
            byte[] challenge = "VaultCheck signing identity check v1".getBytes(StandardCharsets.US_ASCII);
            var proof = Signature.getInstance("Ed25519");
            proof.initSign(privateKey);
            proof.update(challenge);
            byte[] signature = proof.sign();
            proof.initVerify(publicKey);
            proof.update(challenge);
            if (!proof.verify(signature)) throw new GeneralSecurityException("Unlocked key does not match selected identity");
            return manifests.sign(reference, privateKey);
        } finally {
            // Provider support varies; no claim of complete JVM zeroization.
            try { privateKey.destroy(); } catch (DestroyFailedException unsupported) { /* no retained key cache */ }
        }
    }
}
