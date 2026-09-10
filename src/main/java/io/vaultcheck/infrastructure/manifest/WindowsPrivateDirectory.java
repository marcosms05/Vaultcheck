package io.vaultcheck.infrastructure.manifest;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.List;

/** Creates new application storage only. Never changes permissions on an existing user directory. */
public final class WindowsPrivateDirectory {
    private WindowsPrivateDirectory() {}

    public static Path create(Path applicationParent) throws IOException {
        UserPrincipal user = currentUser();
        var rule = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT).build();
        var attribute = new FileAttribute<List<AclEntry>>() {
            @Override public String name() { return "acl:acl"; }
            @Override public List<AclEntry> value() { return List.of(rule); }
        };
        Path directory = Files.createTempDirectory(applicationParent, "vaultcheck-keys-", attribute);
        setCreatedOwner(directory, user);
        // No secrets are written before checking effective ACLs. No weaker-permission fallback.
        requirePrivate(directory);
        return directory;
    }

    static Path createPendingKeyFile(Path directory) throws IOException {
        Path file = Files.createTempFile(directory, ".pending-", ".vckey");
        try {
            setCreatedOwner(file, currentUser());
            requirePrivateFile(file);
            return file;
        } catch (IOException failure) {
            try { Files.deleteIfExists(file); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static Path createPublicKeyFile(Path directory, byte[] encoded) throws IOException {
        requirePrivate(directory);
        Path file = directory.resolve("public.der");
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            setCreatedOwner(file, currentUser());
            requirePrivateFile(file);
            var buffer = java.nio.ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        return file;
    }

    // Used only immediately after exclusive creation. Existing storage is validated, never repaired.
    // Administrative Windows tokens can default new objects to the Administrators group.
    private static void setCreatedOwner(Path path, UserPrincipal user) throws IOException {
        var view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) throw new IOException("Windows ACLs required");
        if (!view.getOwner().equals(user)) view.setOwner(user);
    }

    public static void requirePrivate(Path directory) throws IOException {
        UserPrincipal user = currentUser();
        var attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Storage must be an ordinary private directory");
        }
        requireOwnerOnlyAcl(directory, user);
    }

    public static void requirePrivateFile(Path file) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Stored key must be an ordinary private file");
        }
        requireOwnerOnlyAcl(file, currentUser());
    }

    private static void requireOwnerOnlyAcl(Path path, UserPrincipal user) throws IOException {
        var view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null || !view.getOwner().equals(user)) throw new IOException("Storage is not owned by current user");
        boolean ownerAllowed = false;
        for (var entry : view.getAcl()) {
            if (entry.type() == AclEntryType.ALLOW) {
                if (!entry.principal().equals(user)) throw new IOException("Storage grants access to another principal");
                ownerAllowed = true;
            }
        }
        if (!ownerAllowed) throw new IOException("Storage does not grant owner access");
    }

    private static UserPrincipal currentUser() throws IOException {
        if (!Platform.isWindows()) throw new IOException("Private storage currently requires Windows ACLs");
        char[] name = new char[257];
        var size = new IntByReference(name.length);
        if (!ApiHolder.API.GetUserNameW(name, size)) throw new IOException("Cannot resolve current Windows identity");
        return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(Native.toString(name));
    }

    public interface AccountApi extends StdCallLibrary { boolean GetUserNameW(char[] buffer, IntByReference size); }
    private static final class ApiHolder { private static final AccountApi API = Native.load("advapi32", AccountApi.class); }
}
