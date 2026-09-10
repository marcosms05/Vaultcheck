package io.vaultcheck.application;

import io.vaultcheck.domain.*;
import io.vaultcheck.infrastructure.manifest.*;
import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifySignedReferenceTest {
    private static KeyPair signer, other;
    private static String id;
    private final SignedManifestCodec codec = new SignedManifestCodec();
    private final FileFingerprint expected = new FileFingerprint(3, "a".repeat(64));
    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        signer = generator.generateKeyPair(); other = generator.generateKeyPair();
        id = PinnedSigners.fingerprint(signer.getPublic());
    }
    private byte[] manifest(int omitted) throws Exception {
        return codec.sign(new ReferenceManifest(1, omitted, List.of(new ReferenceEntry(new ManifestPath("file"), expected))), signer.getPrivate());
    }
    private VerifySignedReference flow(RootedFingerprintReader reader) throws Exception {
        return new VerifySignedReference(new PinnedSigners(List.of(signer.getPublic())), codec, reader);
    }
    @Test void authenticatesBeforeComparingAndPreservesScope() throws Exception {
        var count = new AtomicInteger();
        var result = flow((r, p, c) -> { count.incrementAndGet(); return expected; })
                .verify(Path.of("."), new ByteArrayInputStream(manifest(0)), id, () -> false, e -> {});
        assertEquals(1, count.get());
        assertEquals(id, result.signerFingerprint());
        assertEquals(VerifySignedReference.Result.Authentication.VALID_SIGNATURE_TRUSTED_PIN, result.authentication());
        assertEquals(ComparisonSummary.Coverage.COMPLETE, result.coverage());
        assertEquals(ComparisonSummary.Scope.SUPPLIED_ENTRIES_ONLY, result.scope());
    }
    @Test void unknownSignerDoesNotReadManifestOrTarget() throws Exception {
        var source = new InputStream() { @Override public int read() { throw new AssertionError("Unexpected source read"); } };
        assertThrows(GeneralSecurityException.class, () -> flow((r,p,c) -> { throw new AssertionError(); })
                .verify(Path.of("."), source, PinnedSigners.fingerprint(other.getPublic()), () -> false, e -> fail()));
    }
    @Test void invalidSignatureDoesNotReadTargetOrPublishEntries() throws Exception {
        var bytes = manifest(0); bytes[bytes.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> flow((r,p,c) -> { throw new AssertionError(); })
                .verify(Path.of("."), new ByteArrayInputStream(bytes), id, () -> false, e -> fail()));
    }
    @Test void omissionsRemainIncompleteEvenWhenEveryComparedFileMatches() throws Exception {
        var result = flow((r,p,c) -> expected).verify(Path.of("."), new ByteArrayInputStream(manifest(4)), id, () -> false, e -> {});
        assertEquals(ComparisonSummary.Coverage.COMPLETE, result.entries().coverage());
        assertEquals(ComparisonSummary.Coverage.INCOMPLETE, result.coverage());
        assertEquals(4, result.referenceOmissions());
    }
    @Test void cancellationTakesPrecedenceOverReferenceOmissions() throws Exception {
        var result = flow((r,p,c) -> { throw new CancellationException(); })
                .verify(Path.of("."), new ByteArrayInputStream(manifest(4)), id, () -> false, e -> fail());
        assertEquals(ComparisonSummary.Coverage.CANCELLED, result.coverage());
    }
    @Test void pinsAreSnapshotsNotALiveMutableCollection() throws Exception {
        var approved = new java.util.ArrayList<PublicKey>(); approved.add(signer.getPublic());
        var pins = new PinnedSigners(approved); approved.clear(); approved.add(other.getPublic());
        assertArrayEquals(signer.getPublic().getEncoded(), pins.requireTrusted(id).getEncoded());
        assertThrows(GeneralSecurityException.class, () -> pins.requireTrusted(PinnedSigners.fingerprint(other.getPublic())));
    }
    @Test void malformedIdentifiersAndUnsupportedKeysAreRejected() throws Exception {
        var pins = new PinnedSigners(List.of());
        assertThrows(GeneralSecurityException.class, () -> pins.requireTrusted("../key"));
        assertThrows(GeneralSecurityException.class, () -> pins.requireTrusted(null));
        var ec = KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic();
        assertThrows(GeneralSecurityException.class, () -> new PinnedSigners(List.of(ec)));
    }
}
