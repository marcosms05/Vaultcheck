package io.vaultcheck.application;

import io.vaultcheck.domain.ComparisonSummary;
import io.vaultcheck.domain.EntryComparison;
import io.vaultcheck.domain.ReferenceEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Internal comparison stage, not an authenticated manifest-verification endpoint. */
public final class CompareEntries {
    private final RootedFingerprintReader reader;
    // Conservative pilot bound; replace with validated disk-backed indexing for large manifests.
    public static final int MAX_ENTRIES = 10_000;

    public CompareEntries(RootedFingerprintReader reader) { this.reader = Objects.requireNonNull(reader); }

    public ComparisonSummary compare(Path root, Iterable<ReferenceEntry> entries, BooleanSupplier cancelled,
                                     Consumer<EntryComparison> output) {
        Objects.requireNonNull(root);
        Objects.requireNonNull(entries);
        Objects.requireNonNull(cancelled);
        Objects.requireNonNull(output);
        long matched = 0, modified = 0, unavailable = 0;
        var seen = new HashSet<String>();
        var iterator = entries.iterator();
        while (true) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return new ComparisonSummary(ComparisonSummary.Coverage.CANCELLED, matched, modified, unavailable);
            }
            // Parser/iterator/output exceptions deliberately propagate. No successful summary on such failures.
            if (!iterator.hasNext()) break;
            if (seen.size() == MAX_ENTRIES) throw new IllegalArgumentException("Comparison entry limit exceeded");
            var entry = Objects.requireNonNull(iterator.next());
            if (!seen.add(entry.path().value().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate or ambiguous reference path");
            }
            EntryComparison.Status status;
            try {
                var actual = Objects.requireNonNull(reader.read(root, entry.path(), cancelled));
                status = actual.equals(entry.expected()) ? EntryComparison.Status.MATCHED : EntryComparison.Status.MODIFIED;
            } catch (CancellationException e) {
                return new ComparisonSummary(ComparisonSummary.Coverage.CANCELLED, matched, modified, unavailable);
            } catch (IOException e) {
                // A failed open is not proof of absence (permissions, disconnection, sharing conflict, etc.).
                status = EntryComparison.Status.NOT_VERIFIABLE;
            }
            output.accept(new EntryComparison(entry.path(), status));
            switch (status) {
                case MATCHED -> matched++;
                case MODIFIED -> modified++;
                case NOT_VERIFIABLE -> unavailable++;
            }
        }
        var coverage = unavailable > 0 ? ComparisonSummary.Coverage.INCOMPLETE
                : seen.isEmpty() ? ComparisonSummary.Coverage.NO_ENTRIES : ComparisonSummary.Coverage.COMPLETE;
        return new ComparisonSummary(coverage, matched, modified, unavailable);
    }
}
