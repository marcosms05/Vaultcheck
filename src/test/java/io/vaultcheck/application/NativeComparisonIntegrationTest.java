package io.vaultcheck.application;

import io.vaultcheck.domain.*;
import io.vaultcheck.infrastructure.files.WindowsHandleFingerprintReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class NativeComparisonIntegrationTest {
    @TempDir Path root;
    @Test void comparesRealFilesAndKeepsMissingOpenAsIncompleteCoverage() throws Exception {
        Files.writeString(root.resolve("same"), "abc");
        Files.writeString(root.resolve("different"), "abd");
        var expected = new FileFingerprint(3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        var entries = List.of("same", "different", "missing").stream()
                .map(p -> new ReferenceEntry(new ManifestPath(p), expected)).toList();
        var result = new CompareEntries(new WindowsHandleFingerprintReader()).compare(root, entries, () -> false, e -> {});
        assertEquals(new ComparisonSummary(ComparisonSummary.Coverage.INCOMPLETE, 1, 1, 1), result);
        assertEquals("abc", Files.readString(root.resolve("same")));
        assertEquals("abd", Files.readString(root.resolve("different")));
    }

    @Test void verifiesSignedReferenceAndReadsNativeFileThroughTrustedPolicy() throws Exception {
        Files.writeString(root.resolve("same"), "abc");
        var expected = new FileFingerprint(3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        var keys = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var codec = new io.vaultcheck.infrastructure.manifest.SignedManifestCodec();
        var manifest = new ReferenceManifest(1, 0, List.of(new ReferenceEntry(new ManifestPath("same"), expected)));
        var pins = new io.vaultcheck.infrastructure.manifest.PinnedSigners(List.of(keys.getPublic()));
        var id = io.vaultcheck.infrastructure.manifest.PinnedSigners.fingerprint(keys.getPublic());
        var flow = new VerifySignedReference(pins, codec, new WindowsHandleFingerprintReader());
        var result = flow.verify(root, new java.io.ByteArrayInputStream(codec.sign(manifest, keys.getPrivate())), id, () -> false, e -> {});
        assertEquals(VerifySignedReference.Result.Authentication.VALID_SIGNATURE_TRUSTED_PIN, result.authentication());
        assertEquals(1, result.entries().matched());
        assertEquals(ComparisonSummary.Coverage.COMPLETE, result.coverage());
        assertEquals("abc", Files.readString(root.resolve("same")));
    }
}
