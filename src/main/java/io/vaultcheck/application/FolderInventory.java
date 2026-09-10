package io.vaultcheck.application;

import io.vaultcheck.domain.FolderScan;
import io.vaultcheck.domain.ReferenceEntry;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bounded inventory of files observed during traversal. */
@FunctionalInterface
public interface FolderInventory {
    FolderScan scan(Path root, BooleanSupplier cancelled, Consumer<ReferenceEntry> progress);
}
