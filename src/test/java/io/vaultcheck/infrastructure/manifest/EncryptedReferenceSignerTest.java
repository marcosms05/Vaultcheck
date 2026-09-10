package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.domain.*;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedReferenceSignerTest {
    private static KeyPair approved, other;
    private static String id;
    private static byte[] encrypted, otherEncrypted;
    private static final char[] PASSWORD = "synthetic integration passphrase".toCharArray();
    private final SignedManifestCodec manifests = new SignedManifestCodec();
    private final ReferenceManifest reference = new ReferenceManifest(1, 0, List.of(
            new ReferenceEntry(new ManifestPath("sample"), new FileFingerprint(3, "a".repeat(64)))));

    @BeforeAll static void identities() throws Exception {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        approved = generator.generateKeyPair(); other = generator.generateKeyPair();
        id = PinnedSigners.fingerprint(approved.getPublic());
        var codec = new EncryptedSigningKeyCodec();
        encrypted = codec.protect(approved.getPrivate(), PASSWORD);
        otherEncrypted = codec.protect(other.getPrivate(), PASSWORD);
    }
    private EncryptedReferenceSigner signer() throws Exception {
        return new EncryptedReferenceSigner(new PinnedSigners(List.of(approved.getPublic())),
                new EncryptedSigningKeyCodec(), manifests);
    }
    @Test void restoredKeySignsReferenceForSelectedIdentity() throws Exception {
        byte[] result = signer().sign(reference, encrypted, PASSWORD, id);
        assertEquals(reference, manifests.verifyAndRead(new ByteArrayInputStream(result), approved.getPublic()));
        assertArrayEquals(PASSWORD, "synthetic integration passphrase".toCharArray());
    }
    @Test void validEncryptedKeyOfAnotherIdentityCannotSignSelectedReference() throws Exception {
        assertThrows(GeneralSecurityException.class, () -> signer().sign(reference, otherEncrypted, PASSWORD, id));
    }
    @Test void unknownIdentityIsRejectedBeforeUnlocking() throws Exception {
        // Null encrypted inputs would fail if unlock were reached first.
        assertThrows(GeneralSecurityException.class, () -> signer().sign(reference, null, null, PinnedSigners.fingerprint(other.getPublic())));
    }
    @Test void wrongPasswordCannotProduceSignedReference() throws Exception {
        assertThrows(GeneralSecurityException.class, () -> signer().sign(reference, encrypted, "another synthetic passphrase".toCharArray(), id));
    }
}
