package io.vaultcheck.domain;

import java.util.Objects;

/** Expected bytes, not evidence that a reference is authenticated. */
public record ReferenceEntry(ManifestPath path, FileFingerprint expected) {
    public ReferenceEntry {
        Objects.requireNonNull(path);
        Objects.requireNonNull(expected);
    }
}
