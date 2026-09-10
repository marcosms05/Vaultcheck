package io.vaultcheck.infrastructure.files;

import io.vaultcheck.domain.ManifestPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CheckedPathResolverTest {
    @TempDir Path temporary;

    @Test void resolvesExistingRegularFileUnderSelectedRoot() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        var folder = Files.createDirectory(root.resolve("folder"));
        var file = Files.writeString(folder.resolve("file.txt"), "safe");
        var resolver = new CheckedPathResolver(root);
        assertEquals(file.toAbsolutePath(), resolver.resolveRegularFile(new ManifestPath("folder/file.txt")));
    }
    @Test void rejectsMissingFilesAndDirectoriesAsFiles() throws Exception {
        var resolver = new CheckedPathResolver(temporary);
        assertThrows(IOException.class, () -> resolver.resolveRegularFile(new ManifestPath("missing")));
        Files.createDirectory(temporary.resolve("folder"));
        assertThrows(IOException.class, () -> resolver.resolveRegularFile(new ManifestPath("folder")));
    }
    @Test void rejectsFileAsRoot() throws Exception {
        var file = Files.writeString(temporary.resolve("file"), "content");
        assertThrows(IOException.class, () -> new CheckedPathResolver(file));
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void rejectsJunctionInRootAndDescendantAndPreservesTarget() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        var outside = Files.createDirectory(temporary.resolve("outside"));
        var sentinel = Files.writeString(outside.resolve("sentinel.txt"), "unchanged");
        var resolver = new CheckedPathResolver(root);
        var junction = root.resolve("redirect");
        // Only generated test paths; no user-controlled strings or production shell invocation.
        var process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                junction.toString(), outside.toString()).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                fail("Junction fixture creation timed out");
            }
            assertEquals(0, process.exitValue(), "Could not create junction fixture");
            assertThrows(IOException.class, () -> resolver.resolveRegularFile(new ManifestPath("redirect/sentinel.txt")));
            assertThrows(IOException.class, () -> new CheckedPathResolver(junction));
            assertEquals("unchanged", Files.readString(sentinel));
        } finally {
            Files.deleteIfExists(junction);
        }
        assertTrue(Files.exists(sentinel));
    }
}
