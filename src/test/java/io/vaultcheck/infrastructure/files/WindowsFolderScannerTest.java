package io.vaultcheck.infrastructure.files;

import io.vaultcheck.domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsFolderScannerTest {
    @TempDir Path temporary;
    private final WindowsFolderScanner scanner = new WindowsFolderScanner();

    @Test void nestedFilesBecomeReferenceEntries() throws Exception {
        Files.createDirectories(temporary.resolve("folder"));
        Files.writeString(temporary.resolve("folder/a.txt"), "abc");
        Files.writeString(temporary.resolve("b.txt"), "abd");
        var result = scanner.scan(temporary, () -> false, e -> {});
        assertEquals(FolderScan.Coverage.COMPLETE, result.coverage());
        var manifest = result.completeReference(1);
        assertEquals(2, manifest.entries().size());
        assertEquals("b.txt", manifest.entries().getFirst().path().value());
        var nested = manifest.entries().get(1);
        assertEquals("folder/a.txt", nested.path().value());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", nested.expected().sha256());
    }
    @Test void emptyFolderIsACompleteEmptyInventory() {
        var result = scanner.scan(temporary, () -> false, e -> fail());
        assertEquals(FolderScan.Coverage.COMPLETE, result.coverage());
        assertTrue(result.entries().isEmpty());
    }
    @Test void cancellationRetainsOnlyFinishedEntriesAndReleasesDirectories() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        Files.writeString(root.resolve("a"), "abc"); Files.writeString(root.resolve("b"), "abc");
        var stop = new AtomicBoolean();
        var result = scanner.scan(root, stop::get, e -> stop.set(true));
        assertEquals(FolderScan.Coverage.CANCELLED, result.coverage());
        assertEquals(1, result.entries().size());
        assertThrows(IllegalStateException.class, () -> result.completeReference(1));
        Files.move(root, temporary.resolve("released"));
    }
    @Test void limitsCannotBePresentedAsComplete() throws Exception {
        Files.writeString(temporary.resolve("a"), "a"); Files.writeString(temporary.resolve("b"), "b");
        var result = new WindowsFolderScanner(new WindowsFolderScanner.Limits(1, 10, 2)).scan(temporary, () -> false, e -> {});
        assertEquals(FolderScan.Coverage.LIMIT_REACHED, result.coverage());
        assertEquals(1, result.entries().size());
        assertThrows(IllegalStateException.class, () -> result.completeReference(1));
    }
    @Test void depthLimitAndVisitedLimitAreEnforced() throws Exception {
        Files.createDirectory(temporary.resolve("nested"));
        var shallow = new WindowsFolderScanner(new WindowsFolderScanner.Limits(10, 10, 0)).scan(temporary, () -> false, e -> {});
        assertEquals(FolderScan.Coverage.LIMIT_REACHED, shallow.coverage());
        Files.createDirectory(temporary.resolve("second"));
        var bounded = new WindowsFolderScanner(new WindowsFolderScanner.Limits(10, 1, 2)).scan(temporary, () -> false, e -> {});
        assertEquals(FolderScan.Coverage.LIMIT_REACHED, bounded.coverage());
    }
    @Test void busyFileIsAnExplicitIssueWhileOtherFilesAreRead() throws Exception {
        var busy = Files.writeString(temporary.resolve("busy"), "abc");
        Files.writeString(temporary.resolve("readable"), "abc");
        try (var writer = Files.newByteChannel(busy, StandardOpenOption.WRITE)) {
            var result = scanner.scan(temporary, () -> false, e -> {});
            assertEquals(FolderScan.Coverage.INCOMPLETE, result.coverage());
            assertEquals(1, result.entries().size());
            assertEquals("busy", result.issues().getFirst().relativePath());
            assertThrows(IllegalStateException.class, () -> result.completeReference(1));
        }
    }
    @Test void progressFailurePropagatesAndReleasesHandles() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        Files.writeString(root.resolve("a"), "abc");
        assertThrows(IllegalStateException.class, () -> scanner.scan(root, () -> false, e -> { throw new IllegalStateException(); }));
        Files.move(root, temporary.resolve("released"));
    }
    @Test void junctionIsReportedWithoutEnteringItsTarget() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        var outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("secret"), "outside");
        var link = root.resolve("redirect");
        var process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J", link.toString(), outside.toString())
                .redirectErrorStream(true).start();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Fixture timed out"); }
            assertEquals(0, process.exitValue());
            var result = scanner.scan(root, () -> false, e -> fail("Must not read redirected content"));
            assertEquals(FolderScan.Coverage.INCOMPLETE, result.coverage());
            assertTrue(result.entries().isEmpty());
            assertEquals(1, result.issues().size());
        } finally { Files.deleteIfExists(link); }
    }
}
