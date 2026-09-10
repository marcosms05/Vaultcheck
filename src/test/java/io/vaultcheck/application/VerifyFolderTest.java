package io.vaultcheck.application;

import io.vaultcheck.domain.*;
import io.vaultcheck.infrastructure.manifest.*;
import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifyFolderTest {
    private final KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    private final SignedManifestCodec codec = new SignedManifestCodec();
    private final String id = PinnedSigners.fingerprint(key.getPublic());
    VerifyFolderTest() throws Exception { }
    private ReferenceEntry entry(String name, char hash) {
        return new ReferenceEntry(new ManifestPath(name), new FileFingerprint(3, String.valueOf(hash).repeat(64)));
    }
    private byte[] signed(int omissions) throws Exception {
        return codec.sign(new ReferenceManifest(1, omissions, List.of(entry("same", 'a'), entry("changed", 'a'), entry("gone", 'a'))), key.getPrivate());
    }
    private VerifyFolder flow(FolderInventory inventory) throws Exception {
        return new VerifyFolder(new PinnedSigners(List.of(key.getPublic())), codec, inventory);
    }
    private VerifyFolder.Result run(FolderScan scan, int omissions) throws Exception {
        return flow((r,c,p) -> scan).verify(Path.of("."), new ByteArrayInputStream(signed(omissions)), id, () -> false);
    }
    private VerifyFolder.Status status(VerifyFolder.Result result, String name) {
        return result.differences().stream().filter(d -> d.path().value().equals(name)).findFirst().orElseThrow().status();
    }
    @Test void completeInventoryIdentifiesContentChangesAdditionsAndAbsences() throws Exception {
        var result = run(new FolderScan(FolderScan.Coverage.COMPLETE,
                List.of(entry("same", 'a'), entry("changed", 'b'), entry("new", 'a')), List.of()), 0);
        assertEquals(FolderScan.Coverage.COMPLETE, result.coverage());
        assertEquals(VerifyFolder.Status.MATCHED, status(result, "same"));
        assertEquals(VerifyFolder.Status.MODIFIED, status(result, "changed"));
        assertEquals(VerifyFolder.Status.ADDED, status(result, "new"));
        assertEquals(VerifyFolder.Status.ABSENT, status(result, "gone"));
        assertThrows(UnsupportedOperationException.class, () -> result.differences().clear());
    }
    @Test void partialCancelledAndLimitedScansNeverAssertAbsence() throws Exception {
        for (var coverage : List.of(FolderScan.Coverage.INCOMPLETE, FolderScan.Coverage.CANCELLED, FolderScan.Coverage.LIMIT_REACHED)) {
            var result = run(new FolderScan(coverage, List.of(), List.of()), 0);
            assertEquals(coverage, result.coverage());
            assertTrue(result.differences().stream().allMatch(d -> d.status() == VerifyFolder.Status.NOT_VERIFIABLE));
        }
    }
    @Test void omittedReferenceEntriesPreventClaimingAddition() throws Exception {
        var result = run(new FolderScan(FolderScan.Coverage.COMPLETE, List.of(entry("new", 'a')), List.of()), 1);
        assertEquals(FolderScan.Coverage.INCOMPLETE, result.coverage());
        assertEquals(VerifyFolder.Status.NOT_VERIFIABLE, status(result, "new"));
        assertEquals(VerifyFolder.Status.ABSENT, status(result, "gone"));
    }
    @Test void invalidSignaturePreventsInventoryRead() throws Exception {
        var bytes = signed(0); bytes[bytes.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> flow((r,c,p) -> { throw new AssertionError("Scanned before authentication"); })
                .verify(Path.of("."), new ByteArrayInputStream(bytes), id, () -> false));
    }
    @Test void unknownPinPreventsSourceAndInventoryRead() throws Exception {
        var source = new InputStream() { public int read() { throw new AssertionError(); } };
        assertThrows(GeneralSecurityException.class, () -> flow((r,c,p) -> { throw new AssertionError(); })
                .verify(Path.of("."), source, "0".repeat(64), () -> false));
    }
    @Test void ambiguousDirectoryMakesDescendantsUnverifiable() throws Exception {
        var scan = new FolderScan(FolderScan.Coverage.INCOMPLETE, List.of(entry("dir/file", 'a')),
                List.of(new FolderScan.Issue("DIR", FolderScan.Reason.DUPLICATE_NAME)));
        assertEquals(VerifyFolder.Status.NOT_VERIFIABLE, status(run(scan, 0), "dir/file"));
    }
    @Test void duplicateAdapterEntriesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> run(new FolderScan(FolderScan.Coverage.COMPLETE,
                List.of(entry("same", 'a'), entry("SAME", 'b')), List.of()), 0));
    }
}

