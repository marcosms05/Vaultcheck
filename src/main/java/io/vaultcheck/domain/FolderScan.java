package io.vaultcheck.domain;

import java.util.List;
import java.util.Objects;

/** Inventory of a bounded scan, not a point-in-time snapshot or authenticated reference. */
public record FolderScan(Coverage coverage, List<ReferenceEntry> entries, List<Issue> issues) {
    public enum Coverage { COMPLETE, INCOMPLETE, CANCELLED, LIMIT_REACHED }
    public enum Reason { UNSUPPORTED_PATH, REDIRECTED_OR_SPECIAL, UNREADABLE, DUPLICATE_NAME }
    public record Issue(String relativePath, Reason reason) {
        public Issue { Objects.requireNonNull(relativePath); Objects.requireNonNull(reason); }
    }
    public FolderScan {
        Objects.requireNonNull(coverage);
        entries = List.copyOf(entries);
        issues = List.copyOf(issues);
        if (coverage == Coverage.COMPLETE && !issues.isEmpty()) throw new IllegalArgumentException("Complete scan has issues");
    }
    public ReferenceManifest completeReference(long createdEpochSecond) {
        if (coverage != Coverage.COMPLETE) throw new IllegalStateException("Partial scan cannot become a complete reference");
        return new ReferenceManifest(createdEpochSecond, 0, entries);
    }
}
