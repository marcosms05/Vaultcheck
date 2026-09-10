package io.vaultcheck.infrastructure.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StreamingFingerprintReaderTest {
    @TempDir Path directory;
    private final StreamingFingerprintReader reader = new StreamingFingerprintReader();

    @Test void matchesKnownDigestAndDoesNotModifySource() throws Exception {
        var file = Files.writeString(directory.resolve("sample"), "abc");
        var result = reader.read(file, () -> false);
        assertEquals(3, result.size());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256());
        assertEquals("abc", Files.readString(file));
    }
    @Test void detectsSameSizeContentChange() throws Exception {
        var file = Files.writeString(directory.resolve("sample"), "abc");
        var first = reader.read(file, () -> false);
        Files.writeString(file, "abd");
        assertNotEquals(first.sha256(), reader.read(file, () -> false).sha256());
    }
    @Test void cancellationDoesNotReturnFingerprint() throws Exception {
        var file = Files.writeString(directory.resolve("sample"), "abc");
        assertThrows(CancellationException.class, () -> reader.read(file, () -> true));
    }
    @Test void refusesDirectories() {
        assertThrows(IOException.class, () -> reader.read(directory, () -> false));
    }
    @Test void handlesEmptyFiles() throws Exception {
        var file = Files.createFile(directory.resolve("empty"));
        var result = reader.read(file, () -> false);
        assertEquals(0, result.size());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result.sha256());
    }
    @Test void readsAcrossBufferBoundaries() throws Exception {
        byte[] content = new byte[200_003];
        new java.util.Random(42).nextBytes(content);
        var file = Files.write(directory.resolve("large"), content);
        var expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        var result = reader.read(file, () -> false);
        assertEquals(content.length, result.size());
        assertEquals(expected, result.sha256());
    }
    @Test void rejectsFileChangedDuringReading() throws Exception {
        var file = Files.writeString(directory.resolve("sample"), "abc");
        assertThrows(IOException.class, () -> reader.read(file, () -> {
            try { Files.writeString(file, "changed"); } catch (IOException e) { throw new RuntimeException(e); }
            return false;
        }));
    }
}
