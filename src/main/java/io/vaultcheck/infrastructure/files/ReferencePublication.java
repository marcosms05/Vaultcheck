package io.vaultcheck.infrastructure.files;

import io.vaultcheck.domain.ManifestPath;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Local no-replacement publication, retaining destination directory handles. */
public final class ReferencePublication {
    public void publish(Path sourceRoot, Path destination, byte[] bytes) throws IOException {
        if (bytes.length > 1_048_644) throw new IOException("Reference exceeds size limit");
        try (var sourceLease = WindowsHandleFingerprintReader.retainDirectory(sourceRoot)) {
        Path source = sourceRoot.toRealPath();
        Path target = destination.toAbsolutePath().normalize();
        new ManifestPath(target.getFileName().toString());
        Path parent = target.getParent().toRealPath();
        if (parent.startsWith(source)) throw new IOException("Reference destination must be outside the source tree");
        target = parent.resolve(target.getFileName());
        try (var retained = WindowsHandleFingerprintReader.retainDirectory(parent)) {
            Path staging = Files.createTempFile(parent, ".vaultcheck-reference-", ".pending");
            boolean published = false;
            try {
                try (var output = FileChannel.open(staging, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    var buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) output.write(buffer);
                    output.force(true);
                }
                Files.createLink(target, staging); // Existing names, including links, are never replaced.
                published = true;
            } finally {
                try { Files.deleteIfExists(staging); }
                catch (IOException error) {
                    throw new IOException(published ? "Reference published but staging cleanup failed" : "Reference staging cleanup failed", error);
                }
            }
        }
        }
    }
}

