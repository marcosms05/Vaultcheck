package io.vaultcheck.infrastructure.files;

import io.vaultcheck.domain.ManifestPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsHandleFingerprintReaderTest {
    @TempDir Path temporary;
    private final WindowsHandleFingerprintReader reader = new WindowsHandleFingerprintReader();

    @Test void readsUsingNativeHandle() throws Exception {
        Files.writeString(temporary.resolve("sample"), "abc");
        var result = reader.read(temporary, new ManifestPath("sample"), () -> false);
        assertEquals(3, result.size());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256());
        assertEquals(52, new WindowsHandleFingerprintReader.Info().size());
    }

    @Test void blocksParentRenameAndFileWritesWhileReadingThenReleasesHandles() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        var file = Files.writeString(root.resolve("sample"), "abc");
        var moved = temporary.resolve("moved");
        var observedLock = new AtomicBoolean();
        var observedFileLock = new AtomicBoolean();
        reader.read(root, new ManifestPath("sample"), () -> {
            // The callback runs before opening too. Wait until the directory is retained.
            try {
                Files.move(root, moved);
                Files.move(moved, root);
            } catch (IOException locked) {
                observedLock.set(true);
            }
            try (var writer = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                // Opening only: never modify fixture bytes while checking sharing behavior.
            } catch (IOException locked) {
                observedFileLock.set(true);
            }
            return false;
        });
        assertTrue(observedLock.get(), "Parent must be retained during the read");
        assertTrue(observedFileLock.get(), "File must reject new writers during the read");
        Files.move(root, moved);
        Files.move(moved, root);
        Files.writeString(file, "after");
        assertEquals("after", Files.readString(file));
    }

    @Test void refusesFileAlreadyOpenForWriting() throws Exception {
        var file = Files.writeString(temporary.resolve("sample"), "abc");
        try (var writer = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
            assertThrows(IOException.class, () -> reader.read(temporary, new ManifestPath("sample"), () -> false));
        }
        assertEquals(3, reader.read(temporary, new ManifestPath("sample"), () -> false).size());
    }

    @Test void rejectsHardLinksAndReleasesDirectoryHandlesOnFailure() throws Exception {
        var file = Files.writeString(temporary.resolve("sample"), "abc");
        var alias = Files.createLink(temporary.resolve("alias"), file);
        assertThrows(IOException.class, () -> reader.read(temporary, new ManifestPath("alias"), () -> false));
        Files.delete(alias);
        Files.delete(file);
    }

    @Test void cancellationReturnsNoResult() throws Exception {
        Files.writeString(temporary.resolve("sample"), "abc");
        assertThrows(CancellationException.class, () -> reader.read(temporary, new ManifestPath("sample"), () -> true));
        Files.delete(temporary.resolve("sample"));
    }

    @Test void cancellationAfterFileOpenReleasesEveryHandle() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        var file = Files.writeString(root.resolve("sample"), "abc");
        assertThrows(CancellationException.class, () -> reader.read(root, new ManifestPath("sample"), () -> {
            try (var writer = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                return false;
            } catch (IOException locked) {
                return true;
            }
        }));
        Files.writeString(file, "released");
        Files.move(root, temporary.resolve("moved"));
    }

    @Test void nativeReaderRejectsJunctionWithoutReadingTarget() throws Exception {
        var root = Files.createDirectory(temporary.resolve("root"));
        var outside = Files.createDirectory(temporary.resolve("outside"));
        var sentinel = Files.writeString(outside.resolve("sample"), "outside");
        var junction = root.resolve("redirect");
        var process = new ProcessBuilder("cmd.exe", "/d", "/c", "mklink", "/J",
                junction.toString(), outside.toString()).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                fail("Junction creation timed out");
            }
            assertEquals(0, process.exitValue());
            assertThrows(IOException.class, () -> reader.read(root, new ManifestPath("redirect/sample"), () -> false));
            assertThrows(IOException.class, () -> reader.read(junction, new ManifestPath("sample"), () -> false));
            assertEquals("outside", Files.readString(sentinel));
        } finally {
            Files.deleteIfExists(junction);
        }
        Files.move(root, temporary.resolve("released-root"));
    }

    @Test void hashesMultipleBlocksAndEmptyFiles() throws Exception {
        byte[] bytes = new byte[150_001];
        new java.util.Random(73).nextBytes(bytes);
        Files.write(temporary.resolve("sample"), bytes);
        var expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals(expected, reader.read(temporary, new ManifestPath("sample"), () -> false).sha256());
        Files.write(temporary.resolve("sample"), new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                reader.read(temporary, new ManifestPath("sample"), () -> false).sha256());
    }

    @Test void rejectsNetworkRootsBeforeOpeningFiles() {
        assertThrows(IOException.class, () -> reader.read(Path.of("\\\\invalid-server\\share"), new ManifestPath("file"), () -> false));
    }
}
