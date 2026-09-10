package io.vaultcheck.infrastructure.manifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class EncryptedKeyFilesTest {
    @TempDir Path root;
    private final EncryptedKeyFiles files = new EncryptedKeyFiles(new EncryptedSigningKeyCodec());
    private final char[] password = "synthetic storage passphrase".toCharArray();
    @BeforeEach void privateStorage() throws Exception { root = WindowsPrivateDirectory.create(root); }

    @Test void publishesRecoverableCiphertextWithoutStagingRemnants() throws Exception {
        var identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var stored = files.create(root, identity.getPrivate(), password);
        var recovered = files.unlock(root, stored.getFileName().toString(), password);
        var proof = Signature.getInstance("Ed25519"); proof.initSign(recovered); proof.update((byte) 7);
        var signed = proof.sign(); proof.initVerify(identity.getPublic()); proof.update((byte) 7);
        assertTrue(proof.verify(signed));
        var acl = Files.getFileAttributeView(stored, java.nio.file.attribute.AclFileAttributeView.class);
        for (var entry : acl.getAcl()) {
            if (entry.type() == java.nio.file.attribute.AclEntryType.ALLOW) {
                assertEquals(acl.getOwner(), entry.principal(), "Key file must not grant another principal access");
            }
        }
        try (var entries = Files.list(root)) { assertEquals(1, entries.count()); }
    }

    @Test void twoExportsKeepExistingCiphertextIntact() throws Exception {
        var identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var first = files.create(root, identity.getPrivate(), password);
        var bytes = Files.readAllBytes(first);
        var second = files.create(root, identity.getPrivate(), password);
        assertNotEquals(first, second);
        assertArrayEquals(bytes, Files.readAllBytes(first));
    }

    @Test void truncatedPublishedFileNeverUnlocks() throws Exception {
        String id = UUID.randomUUID() + ".vckey";
        Files.write(root.resolve(id), new byte[] {1, 2, 3});
        assertThrows(GeneralSecurityException.class, () -> files.unlock(root, id, password));
    }

    @Test void arbitraryNamesDirectoriesAndOversizedFilesAreRejected() throws Exception {
        assertThrows(IOException.class, () -> files.unlock(root, "../outside.vckey", password));
        String id = UUID.randomUUID() + ".vckey";
        var directory = Files.createDirectory(root.resolve(id));
        assertThrows(IOException.class, () -> files.unlock(root, id, password));
        Files.delete(directory);
        Files.write(root.resolve(id), new byte[EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES + 1]);
        assertThrows(IOException.class, () -> files.unlock(root, id, password));
    }

    @Test void failedEncryptionDoesNotPublishAnyFile() throws Exception {
        var identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThrows(IllegalArgumentException.class, () -> files.create(root, identity.getPrivate(), new char[1]));
        try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
    }

    @Test void broadenedDirectoryPermissionsAreRejectedBeforePublication() throws Exception {
        var view = Files.getFileAttributeView(root, java.nio.file.attribute.AclFileAttributeView.class);
        var original = view.getAcl();
        var inherited = Files.getFileAttributeView(root.getParent(), java.nio.file.attribute.AclFileAttributeView.class).getAcl();
        var other = inherited.stream().filter(e -> e.type() == java.nio.file.attribute.AclEntryType.ALLOW
                && !e.principal().equals(viewOwner(view))).findFirst().orElseThrow();
        var broadened = new java.util.ArrayList<>(original); broadened.add(other);
        try {
            view.setAcl(broadened);
            assertThrows(IOException.class, () -> WindowsPrivateDirectory.requirePrivate(root));
            var identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            assertThrows(IOException.class, () -> files.create(root, identity.getPrivate(), password));
            try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
        } finally { view.setAcl(original); }
    }

    @Test void interruptedStagingNameCannotBeImportedAsPublishedKey() throws Exception {
        Files.write(root.resolve(".pending-interrupted.vckey"), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> files.unlock(root, ".pending-interrupted.vckey", password));
    }

    private static java.nio.file.attribute.UserPrincipal viewOwner(java.nio.file.attribute.AclFileAttributeView view) {
        try { return view.getOwner(); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
