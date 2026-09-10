package io.vaultcheck.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ManifestPathTest {
    @ParameterizedTest
    @ValueSource(strings = {"", ".", "..", "../secret", "a/../b", "/absolute", "a//b", "a/",
            "C:/secret", "C:secret", "\\\\server\\share", "\\\\?\\C:\\file", "a\\b", "a:stream",
            "CON", "nul.txt", "COM1.txt", "LPT9", "COM¹", "LPT².txt", "CONIN$", "AUX .txt",
            "file.", "file ", "a/?", "a/*", "a/<", "a/>", "a/|", "a/\"", "a\nfile", "a\u0000b"})
    void rejectsUnsafeOrAmbiguousNames(String value) {
        assertThrows(IllegalArgumentException.class, () -> new ManifestPath(value));
    }
    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "Fotos/vacaciones 2026/niño.jpg", "a/.gitignore", "COM10.txt", "emoji/😀.txt"})
    void preservesValidNames(String value) { assertEquals(value, new ManifestPath(value).value()); }

    @Test void boundsInputBeforeFilesystemAccess() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestPath(null));
        assertThrows(IllegalArgumentException.class, () -> new ManifestPath("x".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> new ManifestPath("a/".repeat(128) + "b"));
        assertThrows(IllegalArgumentException.class, () -> new ManifestPath("a".repeat(4097)));
        assertThrows(IllegalArgumentException.class, () -> new ManifestPath("bad\uD800"));
    }
}
