package io.vaultcheck.domain;

import java.util.Objects;

public record EntryComparison(ManifestPath path, Status status) {
    public enum Status { MATCHED, MODIFIED, NOT_VERIFIABLE }
    public EntryComparison {
        Objects.requireNonNull(path);
        Objects.requireNonNull(status);
    }
}
