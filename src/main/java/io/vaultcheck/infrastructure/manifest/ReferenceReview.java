package io.vaultcheck.infrastructure.manifest;
import io.vaultcheck.application.FolderInventory;
import io.vaultcheck.application.VerifyFolder;

import io.vaultcheck.infrastructure.manifest.PinnedSigners;
import io.vaultcheck.infrastructure.manifest.SignedManifestCodec;
import io.vaultcheck.domain.ReferenceManifest;
import java.io.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Immutable reviewed bytes. Signature validation does not itself approve the imported identity. */
public final class ReferenceReview {
    private final byte[] envelope;
    private final PublicKey key;
    private final ReferenceManifest manifest;
    private final String fingerprint;
    private ReferenceReview(byte[] envelope, PublicKey key, ReferenceManifest manifest) throws GeneralSecurityException {
        this.envelope = envelope; this.key = key; this.manifest = manifest;
        fingerprint = PinnedSigners.fingerprint(key);
    }
    public static ReferenceReview read(InputStream reference, InputStream publicKey) throws IOException, GeneralSecurityException {
        byte[] encoded = bounded(publicKey, 128);
        var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        if (!Arrays.equals(encoded, key.getEncoded())) throw new GeneralSecurityException("Noncanonical public key");
        byte[] bytes = bounded(reference, SignedManifestCodec.MAX_PAYLOAD_BYTES + 68);
        var parsed = new SignedManifestCodec().verifyAndRead(new ByteArrayInputStream(bytes), key);
        return new ReferenceReview(bytes, key, parsed);
    }
    private static byte[] bounded(InputStream input, int maximum) throws IOException {
        byte[] data = input.readNBytes(maximum + 1);
        if (data.length > maximum) throw new IOException("Input exceeds pilot limit");
        return data;
    }
    public String fingerprint() { return fingerprint; }
    public int entries() { return manifest.entries().size(); }
    public int omissions() { return manifest.omittedEntries(); }
    public Approved approve(String independentlyConfirmedFingerprint) throws GeneralSecurityException {
        if (!fingerprint.equals(independentlyConfirmedFingerprint)) throw new GeneralSecurityException("Fingerprint confirmation mismatch");
        return new Approved(this);
    }
    public static final class Approved {
        private final ReferenceReview review;
        private Approved(ReferenceReview review) { this.review = review; }
        public VerifyFolder.Result verify(java.nio.file.Path root, FolderInventory inventory,
                                          java.util.function.BooleanSupplier cancelled) throws IOException, GeneralSecurityException {
            return new VerifyFolder(new PinnedSigners(List.of(review.key)), new SignedManifestCodec(), inventory)
                    .verify(root, new ByteArrayInputStream(review.envelope), review.fingerprint, cancelled);
        }
    }
}

