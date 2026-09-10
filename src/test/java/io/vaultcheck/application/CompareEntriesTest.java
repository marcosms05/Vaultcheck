package io.vaultcheck.application;

import io.vaultcheck.domain.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompareEntriesTest {
    private static final FileFingerprint EXPECTED = new FileFingerprint(3, "a".repeat(64));
    private static final Path ROOT = Path.of(".");
    private ReferenceEntry entry(String path) { return new ReferenceEntry(new ManifestPath(path), EXPECTED); }

    @Test void separatesContentDifferencesFromCoverageAndAuthentication() {
        var output = new ArrayList<EntryComparison>();
        var useCase = new CompareEntries((root, path, cancel) -> switch (path.value()) {
            case "match" -> EXPECTED;
            case "changed" -> new FileFingerprint(3, "b".repeat(64));
            default -> throw new IOException("private system details must not escape");
        });
        var result = useCase.compare(ROOT, List.of(entry("match"), entry("changed"), entry("unreadable")), () -> false, output::add);
        assertEquals(ComparisonSummary.Coverage.INCOMPLETE, result.coverage());
        assertEquals(1, result.matched());
        assertEquals(1, result.modified());
        assertEquals(1, result.notVerifiable());
        assertEquals(EntryComparison.Status.NOT_VERIFIABLE, output.get(2).status());
        assertEquals(ComparisonSummary.Authentication.NOT_CHECKED, result.authentication());
        assertEquals(ComparisonSummary.Scope.SUPPLIED_ENTRIES_ONLY, result.scope());
    }

    @Test void completeCoverageCanContainDifferences() {
        var useCase = new CompareEntries((r, p, c) -> new FileFingerprint(4, "a".repeat(64)));
        var result = useCase.compare(ROOT, List.of(entry("file")), () -> false, e -> {});
        assertEquals(ComparisonSummary.Coverage.COMPLETE, result.coverage());
        assertEquals(1, result.modified());
    }

    @Test void emptyInputDoesNotClaimSuccessfulVerification() {
        var result = new CompareEntries((r, p, c) -> { throw new AssertionError(); })
                .compare(ROOT, List.of(), () -> false, e -> {});
        assertEquals(ComparisonSummary.Coverage.NO_ENTRIES, result.coverage());
    }

    @Test void cancellationPreservesOnlyPublishedResults() {
        var stop = new AtomicBoolean();
        var result = new CompareEntries((r, p, c) -> EXPECTED)
                .compare(ROOT, List.of(entry("first"), entry("second")), stop::get, e -> stop.set(true));
        assertEquals(ComparisonSummary.Coverage.CANCELLED, result.coverage());
        assertEquals(1, result.matched());
    }

    @Test void readerCancellationIsNotAnUnreadableFile() {
        var result = new CompareEntries((r, p, c) -> { throw new CancellationException(); })
                .compare(ROOT, List.of(entry("file")), () -> false, e -> fail("No partial file result"));
        assertEquals(ComparisonSummary.Coverage.CANCELLED, result.coverage());
        assertEquals(0, result.notVerifiable());
    }

    @Test void outputFailureAndInputFailureNeverProduceCompleteSummary() {
        var useCase = new CompareEntries((r, p, c) -> EXPECTED);
        assertThrows(IllegalStateException.class, () -> useCase.compare(ROOT, List.of(entry("file")), () -> false,
                e -> { throw new IllegalStateException("output failed"); }));
        Iterable<ReferenceEntry> broken = () -> { throw new IllegalStateException("input failed"); };
        assertThrows(IllegalStateException.class, () -> useCase.compare(ROOT, broken, () -> false, e -> {}));
    }

    @Test void rejectsDuplicateNamesAndCaseAliases() {
        var useCase = new CompareEntries((r, p, c) -> EXPECTED);
        for (String alias : List.of("file", "FILE")) {
            assertThrows(IllegalArgumentException.class, () -> useCase.compare(ROOT,
                    List.of(entry("file"), entry(alias)), () -> false, e -> {}));
        }
    }

    @Test void inputLimitStopsWithoutReturningSuccess() {
        Iterable<ReferenceEntry> entries = () -> IntStream.rangeClosed(0, CompareEntries.MAX_ENTRIES)
                .mapToObj(i -> entry("file-" + i)).iterator();
        assertThrows(IllegalArgumentException.class, () -> new CompareEntries((r, p, c) -> EXPECTED)
                .compare(ROOT, entries, () -> false, e -> {}));
    }
}
