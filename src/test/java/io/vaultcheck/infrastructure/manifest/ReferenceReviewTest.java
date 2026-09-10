package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.domain.*;
import java.io.*;
import java.security.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceReviewTest {
    private final KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    private final ReferenceManifest manifest = new ReferenceManifest(1, 0, List.of());
    ReferenceReviewTest() throws Exception { }
    private byte[] signed() throws Exception { return new SignedManifestCodec().sign(manifest, key.getPrivate()); }
    private ReferenceReview review(byte[] bytes) throws Exception {
        return ReferenceReview.read(new ByteArrayInputStream(bytes), new ByteArrayInputStream(key.getPublic().getEncoded()));
    }
    @Test void approvalRequiresExactFingerprint() throws Exception {
        var review = review(signed());
        assertThrows(GeneralSecurityException.class, () -> review.approve("0".repeat(64)));
        assertThrows(GeneralSecurityException.class, () -> review.approve(null));
        assertEquals(PinnedSigners.fingerprint(key.getPublic()), review.fingerprint());
        var result = review.approve(review.fingerprint()).verify(Path.of("."), (r,c,p) -> new FolderScan(FolderScan.Coverage.COMPLETE, List.of(), List.of()), () -> false);
        assertEquals(FolderScan.Coverage.COMPLETE, result.coverage());
    }
    @Test void frozenBytesSurviveSourceMutation() throws Exception {
        var bytes = signed(); var review = review(bytes); bytes[bytes.length - 1] ^= 1;
        assertDoesNotThrow(() -> review.approve(review.fingerprint()).verify(Path.of("."), (r,c,p) -> new FolderScan(FolderScan.Coverage.COMPLETE, List.of(), List.of()), () -> false));
    }
    @Test void tamperedReferenceCannotBeReviewed() throws Exception {
        var bytes = signed(); bytes[bytes.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> review(bytes));
    }
    @Test void noncanonicalOrOversizedKeysAreRejected() throws Exception {
        var encoded = key.getPublic().getEncoded();
        assertThrows(GeneralSecurityException.class, () -> ReferenceReview.read(new ByteArrayInputStream(signed()), new ByteArrayInputStream(java.util.Arrays.copyOf(encoded, encoded.length + 1))));
        assertThrows(IOException.class, () -> ReferenceReview.read(new ByteArrayInputStream(signed()), new ByteArrayInputStream(new byte[129])));
    }
    @Test void referenceSizeIsBounded() {
        assertThrows(IOException.class, () -> review(new byte[SignedManifestCodec.MAX_PAYLOAD_BYTES + 69]));
    }
}
