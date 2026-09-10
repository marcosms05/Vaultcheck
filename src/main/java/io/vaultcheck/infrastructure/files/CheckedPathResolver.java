package io.vaultcheck.infrastructure.files;

import io.vaultcheck.domain.ManifestPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Checks the existing ancestor chain. Does NOT provide race-free handle-based containment. */
public final class CheckedPathResolver {
    private final Path root;

    public CheckedPathResolver(Path selectedRoot) throws IOException {
        root = Objects.requireNonNull(selectedRoot).toAbsolutePath().normalize();
        checkChain(root, true);
    }

    public Path resolveRegularFile(ManifestPath relative) throws IOException {
        Objects.requireNonNull(relative);
        Path candidate = root;
        for (String component : relative.value().split("/")) candidate = candidate.resolve(component);
        if (!candidate.startsWith(root)) throw new IOException("Path is outside selected root");
        checkChain(candidate, false);
        return candidate;
    }

    private static void checkChain(Path path, boolean directoryOnly) throws IOException {
        Path current = path.getRoot();
        checkType(current, true);
        for (int i = 0; i < path.getNameCount(); i++) {
            current = current.resolve(path.getName(i));
            checkType(current, directoryOnly || i < path.getNameCount() - 1);
        }
    }

    private static void checkType(Path path, boolean directory) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()
                || (directory ? !attributes.isDirectory() : !attributes.isRegularFile())) {
            throw new IOException("Unsupported file type or redirected path");
        }
    }
}
