package io.vaultcheck.application;

import io.vaultcheck.domain.FileFingerprint;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

public interface FingerprintReader {
    FileFingerprint read(Path file, BooleanSupplier cancelled) throws IOException;
}
