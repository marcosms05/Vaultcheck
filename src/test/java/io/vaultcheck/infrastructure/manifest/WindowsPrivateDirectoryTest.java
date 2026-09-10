package io.vaultcheck.infrastructure.manifest;

import java.nio.file.*;
import java.nio.file.attribute.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsPrivateDirectoryTest {
    @TempDir Path parent;

    @Test void creationDoesNotChangeParentSecurity() throws Exception {
        var view = Files.getFileAttributeView(parent, AclFileAttributeView.class);
        var owner = view.getOwner(); var acl = view.getAcl();
        var directory = WindowsPrivateDirectory.create(parent);
        WindowsPrivateDirectory.requirePrivate(directory);
        assertEquals(owner, view.getOwner()); assertEquals(acl, view.getAcl());
    }

    @Test void publicKeyCreationRetainsPrivatePermissionsAndNeverReplaces() throws Exception {
        var directory = WindowsPrivateDirectory.create(parent);
        var file = WindowsPrivateDirectory.createPublicKeyFile(directory, new byte[]{1, 2, 3});
        WindowsPrivateDirectory.requirePrivateFile(file);
        assertThrows(FileAlreadyExistsException.class,
                () -> WindowsPrivateDirectory.createPublicKeyFile(directory, new byte[]{9}));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }
}
