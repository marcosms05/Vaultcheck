package io.vaultcheck.infrastructure.files;

import io.vaultcheck.application.FingerprintReader;
import io.vaultcheck.domain.FileFingerprint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Bounded memory reader. Metadata checks are not filesystem snapshot guarantees. */
public final class StreamingFingerprintReader implements FingerprintReader {
    @Override
    public FileFingerprint read(Path file, BooleanSupplier cancelled) throws IOException {
        var before = attributes(file);
        if (!before.isRegularFile() || before.isSymbolicLink() || before.isOther()) {
            throw new IOException("Only regular files are supported");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        long count = 0;
        try (SeekableByteChannel channel = Files.newByteChannel(file,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.allocate(64 * 1024);
            while (true) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Verification cancelled");
                }
                int read = channel.read(buffer);
                if (read < 0) break;
                count += read;
                if (count > before.size()) throw new IOException("File grew during reading");
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        var after = attributes(file);
        if (!after.isRegularFile() || after.isOther() || count != before.size()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("File changed during reading");
        }
        return new FileFingerprint(count, HexFormat.of().formatHex(digest.digest()));
    }

    private BasicFileAttributes attributes(Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
}
