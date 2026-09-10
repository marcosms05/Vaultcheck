package io.vaultcheck.infrastructure.manifest;

import java.nio.file.*;
import java.security.*;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class CreateSignedReferenceTest {
    @TempDir Path temp;
    private Path data, encrypted, pub;
    private final CreateSignedReference service = new CreateSignedReference();
    @BeforeEach void setup() throws Exception {
        data = Files.createDirectory(temp.resolve("data")); Files.writeString(data.resolve("file.txt"), "abc");
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        encrypted = temp.resolve("key.vckey"); pub = temp.resolve("public.der");
        char[] password = password();
        try { Files.write(encrypted, new EncryptedSigningKeyCodec().protect(pair.getPrivate(), password)); }
        finally { Arrays.fill(password, '\0'); }
        Files.write(pub, pair.getPublic().getEncoded());
    }
    private char[] password() { return "synthetic-test-password".toCharArray(); }
    @Test void exportsVerifiableReferenceAndWipesPassword() throws Exception {
        var prepared = service.prepare(data, () -> false); assertEquals(1, prepared.entries());
        var target = temp.resolve("reference.vcm"); var password = password();
        var identity = service.export(prepared, target, encrypted, pub, password, () -> false);
        assertArrayEquals(new char[password.length], password);
        try (var ref = Files.newInputStream(target); var key = Files.newInputStream(pub)) {
            var review = ReferenceReview.read(ref, key); assertEquals(identity, review.fingerprint()); assertEquals(1, review.entries());
        }
        assertEquals("abc", Files.readString(data.resolve("file.txt")));
        try (var files = Files.list(temp)) { assertFalse(files.anyMatch(p -> p.getFileName().toString().endsWith(".pending"))); }
    }
    @Test void existingDestinationIsNeverReplaced() throws Exception {
        var target = temp.resolve("reference.vcm"); Files.writeString(target, "keep");
        assertThrows(java.io.IOException.class, () -> service.export(service.prepare(data, () -> false), target, encrypted, pub, password(), () -> false));
        assertEquals("keep", Files.readString(target));
    }
    @Test void rejectsDestinationInsideSource() throws Exception {
        var target = data.resolve("reference.vcm");
        assertThrows(java.io.IOException.class, () -> service.export(service.prepare(data, () -> false), target, encrypted, pub, password(), () -> false));
        assertFalse(Files.exists(target));
    }
    @Test void wrongPasswordCannotPublish() throws Exception {
        var target = temp.resolve("reference.vcm");
        assertThrows(GeneralSecurityException.class, () -> service.export(service.prepare(data, () -> false), target, encrypted, pub, "wrong-test-password".toCharArray(), () -> false));
        assertFalse(Files.exists(target));
    }
    @Test void cancellationBeforeExportWipesPasswordAndDoesNotPublish() throws Exception {
        var target = temp.resolve("reference.vcm"); var password = password(); var prepared = service.prepare(data, () -> false);
        assertThrows(java.util.concurrent.CancellationException.class, () -> service.export(prepared, target, encrypted, pub, password, () -> true));
        assertArrayEquals(new char[password.length], password); assertFalse(Files.exists(target));
    }
}
