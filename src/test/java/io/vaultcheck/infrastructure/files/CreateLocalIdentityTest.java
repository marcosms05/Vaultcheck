package io.vaultcheck.infrastructure.files;

import io.vaultcheck.infrastructure.manifest.*;
import io.vaultcheck.application.VerifyFolder;
import java.nio.file.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class CreateLocalIdentityTest {
    @TempDir Path temp;
    @Test void createdIdentitySignsAndVerifiesWithPrivatePermissions() throws Exception {
        char[] password = "synthetic-identity-password".toCharArray();
        var identity = new CreateLocalIdentity().create(temp, password, () -> false);
        assertArrayEquals(new char[password.length], password);
        WindowsPrivateDirectory.requirePrivate(identity.directory());
        WindowsPrivateDirectory.requirePrivateFile(identity.encryptedKey());
        WindowsPrivateDirectory.requirePrivateFile(identity.publicKey());
        assertTrue(Files.size(identity.encryptedKey()) <= EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES);
        var root = Files.createDirectory(temp.resolve("source")); Files.writeString(root.resolve("file.txt"), "abc");
        var output = temp.resolve("reference.vcm"); var creator = new CreateSignedReference();
        var fingerprint = creator.export(creator.prepare(root, () -> false), output, identity.encryptedKey(), identity.publicKey(),
                "synthetic-identity-password".toCharArray(), () -> false);
        assertEquals(identity.fingerprint(), fingerprint);
        try (var ref = Files.newInputStream(output); var pub = Files.newInputStream(identity.publicKey())) {
            var review = ReferenceReview.read(ref, pub);
            var result = review.approve(identity.fingerprint()).verify(root, new WindowsFolderScanner(), () -> false);
            assertEquals(VerifyFolder.Status.MATCHED, result.differences().getFirst().status());
        }
    }
    @Test void cancellationDoesNotCreateStorageAndWipesPassword() throws Exception {
        char[] password = "synthetic-identity-password".toCharArray();
        assertThrows(java.util.concurrent.CancellationException.class, () -> new CreateLocalIdentity().create(temp, password, () -> true));
        assertArrayEquals(new char[password.length], password);
        try (var children = Files.list(temp)) { assertEquals(0, children.count()); }
    }
    @Test void invalidPasswordDoesNotCreateStorage() throws Exception {
        char[] password = "short".toCharArray();
        assertThrows(java.security.GeneralSecurityException.class, () -> new CreateLocalIdentity().create(temp, password, () -> false));
        assertArrayEquals(new char[password.length], password);
        try (var children = Files.list(temp)) { assertEquals(0, children.count()); }
    }
}
