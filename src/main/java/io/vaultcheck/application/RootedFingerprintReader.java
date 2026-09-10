package io.vaultcheck.application;

import io.vaultcheck.domain.FileFingerprint;
import io.vaultcheck.domain.ManifestPath;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Read beneath an explicit root; implementations must state their provider guarantees. */
@FunctionalInterface
public interface RootedFingerprintReader {
    FileFingerprint read(Path root, ManifestPath relative, BooleanSupplier cancelled) throws IOException;
}
