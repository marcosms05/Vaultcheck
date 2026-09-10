package io.vaultcheck.infrastructure.manifest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Objects;
import java.util.UUID;

/** Publication primitive for an application-controlled directory, NOT a directory-security bootstrap.
 * Only ciphertext reaches disk. Never accepts a filename or replaces an existing published file.
 */
public final class EncryptedKeyFiles {
    private final EncryptedSigningKeyCodec codec;

    public EncryptedKeyFiles(EncryptedSigningKeyCodec codec) { this.codec = Objects.requireNonNull(codec); }

    public Path create(Path applicationDirectory, PrivateKey key, char[] password)
            throws IOException, GeneralSecurityException {
        byte[] ciphertext = codec.protect(key, password);
        Path directory = checkedDirectory(applicationDirectory);
        Path published = directory.resolve(UUID.randomUUID() + ".vckey");
        Path staged = WindowsPrivateDirectory.createPendingKeyFile(directory);
        boolean linked = false;
        try {
            WindowsPrivateDirectory.requirePrivateFile(staged);
            try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var buffer = ByteBuffer.wrap(ciphertext);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Unlike ATOMIC_MOVE (whose replacement semantics vary), a new hard link cannot overwrite.
            // Both names are in the same directory/filesystem. Unsupported providers fail without fallback.
            Files.createLink(published, staged);
            linked = true;
            return published;
        } finally {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanup) {
                if (linked) throw new IOException("Encrypted key published but staging cleanup failed", cleanup);
                throw cleanup;
            }
        }
    }

    public PrivateKey unlock(Path applicationDirectory, String filename, char[] password)
            throws IOException, GeneralSecurityException {
        if (filename == null || !filename.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.vckey")) {
            throw new IOException("Invalid stored key identifier");
        }
        Path file = checkedDirectory(applicationDirectory).resolve(filename);
        WindowsPrivateDirectory.requirePrivateFile(file);
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()
                || attributes.size() > EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES) {
            throw new IOException("Unsupported encrypted key file");
        }
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] ciphertext = input.readNBytes(EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES + 1);
            return codec.unlock(ciphertext, password);
        }
    }

    private static Path checkedDirectory(Path directory) throws IOException {
        Objects.requireNonNull(directory);
        Path root = directory.toAbsolutePath().normalize();
        WindowsPrivateDirectory.requirePrivate(root);
        var attributes = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Invalid application storage directory");
        }
        return root;
    }
}
