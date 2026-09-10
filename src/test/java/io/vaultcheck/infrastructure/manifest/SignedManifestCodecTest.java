package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.domain.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignedManifestCodecTest {
    private static KeyPair keys;
    private final SignedManifestCodec codec = new SignedManifestCodec();
    @BeforeAll static void createKeys() throws Exception { keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    private ReferenceEntry entry(String path) { return new ReferenceEntry(new ManifestPath(path), new FileFingerprint(3, "a".repeat(64))); }
    private ReferenceManifest manifest() { return new ReferenceManifest(123456, 2, List.of(entry("z.txt"), entry("niño.txt"))); }
    private ReferenceManifest read(byte[] bytes) throws Exception { return codec.verifyAndRead(new ByteArrayInputStream(bytes), keys.getPublic()); }

    @Test void roundTripRetainsSignedOmissionsAndCanonicalEntries() throws Exception {
        var original = manifest();
        var decoded = read(codec.sign(original, keys.getPrivate()));
        assertEquals(original, decoded);
        assertTrue(decoded.hasOmissions());
    }
    @Test void canonicalOutputDoesNotDependOnInputOrder() throws Exception {
        var reordered = new ReferenceManifest(123456, 2, List.of(entry("niño.txt"), entry("z.txt")));
        assertArrayEquals(codec.sign(manifest(), keys.getPrivate()), codec.sign(reordered, keys.getPrivate()));
    }
    @Test void wrongVerificationKeyIsRejected() throws Exception {
        var bytes = codec.sign(manifest(), keys.getPrivate());
        var wrong = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        assertThrows(GeneralSecurityException.class, () -> codec.verifyAndRead(new ByteArrayInputStream(bytes), wrong));
    }
    @Test void payloadAndSignatureTamperingAreRejected() throws Exception {
        var bytes = codec.sign(manifest(), keys.getPrivate());
        for (int offset : new int[] {4, 12, 17, bytes.length - 1}) {
            var corrupt = bytes.clone();
            corrupt[offset] ^= 1;
            assertThrows(GeneralSecurityException.class, () -> read(corrupt));
        }
    }
    @Test void truncationAndTrailingBytesAreRejected() throws Exception {
        var bytes = codec.sign(manifest(), keys.getPrivate());
        for (int length : new int[] {0, 3, 12, bytes.length - 1}) {
            assertThrows(IOException.class, () -> read(Arrays.copyOf(bytes, length)));
        }
        assertThrows(IOException.class, () -> read(Arrays.copyOf(bytes, bytes.length + 1)));
    }
    @Test void oversizedLengthIsRejectedBeforeReadingPayload() {
        var header = ByteBuffer.allocate(4).putInt(SignedManifestCodec.MAX_PAYLOAD_BYTES + 1).array();
        assertThrows(IOException.class, () -> read(header));
    }
    @Test void validSignatureDoesNotBypassPayloadValidation() throws Exception {
        var envelope = codec.sign(manifest(), keys.getPrivate());
        var body = Arrays.copyOfRange(envelope, 4, envelope.length - 64);
        for (int offset : new int[] {0, 4}) {
            var invalid = body.clone(); invalid[offset] = 0;
            assertThrows(IOException.class, () -> read(resign(invalid)));
        }
        var invalidCount = body.clone(); ByteBuffer.wrap(invalidCount).putInt(17, 1001);
        assertThrows(IOException.class, () -> read(resign(invalidCount)));
        var invalidDate = body.clone(); ByteBuffer.wrap(invalidDate).putLong(5, -1);
        assertThrows(IOException.class, () -> read(resign(invalidDate)));
    }
    @Test void signedTraversalAndMalformedUtf8AreRejected() throws Exception {
        var envelope = codec.sign(new ReferenceManifest(1, 0, List.of(entry("abc"))), keys.getPrivate());
        var body = Arrays.copyOfRange(envelope, 4, envelope.length - 64);
        var traversal = body.clone(); traversal[23] = '.'; traversal[24] = '.'; traversal[25] = '/';
        assertThrows(IOException.class, () -> read(resign(traversal)));
        var utf8 = body.clone(); utf8[23] = (byte) 0xff;
        assertThrows(IOException.class, () -> read(resign(utf8)));
    }
    @Test void modelRejectsDuplicateAliasesAndInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new ReferenceManifest(0, 0, List.of(entry("a"), entry("A"))));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceManifest(0, -1, List.of()));
    }

    @Test void writerRejectsOversizedManifestBeforeSigning() {
        var entries = java.util.stream.IntStream.range(0, 700)
                .mapToObj(i -> entry("file-" + i + "/" + ("a".repeat(200) + "/").repeat(8) + "end")).toList();
        var large = new ReferenceManifest(1, 0, entries);
        assertThrows(IOException.class, () -> codec.sign(large, keys.getPrivate()));
    }

    @Test void validlySignedNegativeSizeAndDuplicatePathsAreRejected() throws Exception {
        var bytes = codec.sign(new ReferenceManifest(1, 0, List.of(entry("abc"), entry("def"))), keys.getPrivate());
        var body = Arrays.copyOfRange(bytes, 4, bytes.length - 64);
        var negativeSize = body.clone(); ByteBuffer.wrap(negativeSize).putLong(26, -1);
        assertThrows(IOException.class, () -> read(resign(negativeSize)));
        var duplicate = body.clone(); duplicate[68] = 'a'; duplicate[69] = 'b'; duplicate[70] = 'c';
        assertThrows(IOException.class, () -> read(resign(duplicate)));
    }

    private byte[] resign(byte[] body) throws Exception {
        var signer = Signature.getInstance("Ed25519"); signer.initSign(keys.getPrivate()); signer.update(body);
        var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes);
        output.writeInt(body.length); output.write(body); output.write(signer.sign());
        return bytes.toByteArray();
    }
}
