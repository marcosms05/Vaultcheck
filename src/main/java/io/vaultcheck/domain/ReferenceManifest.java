package io.vaultcheck.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Pilot reference model. Construction does not imply a verified signature or trusted signer. */
public record ReferenceManifest(long createdEpochSecond, int omittedEntries, List<ReferenceEntry> entries) {
    public static final int MAX_ENTRIES = 1_000;
    public ReferenceManifest {
        if (createdEpochSecond < 0 || omittedEntries < 0) throw new IllegalArgumentException("Invalid manifest metadata");
        Objects.requireNonNull(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many manifest entries");
        var seen = new HashSet<String>();
        for (var entry : entries) {
            Objects.requireNonNull(entry);
            if (!seen.add(entry.path().value().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate or ambiguous manifest path");
            }
        }
        entries = entries.stream().sorted(Comparator.comparing(e -> e.path().value())).toList();
    }
    public boolean hasOmissions() { return omittedEntries != 0; }
}
