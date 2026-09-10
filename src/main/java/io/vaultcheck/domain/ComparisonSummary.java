package io.vaultcheck.domain;

import java.util.Objects;

/** Coverage concerns supplied entries only. No folder enumeration or authentication is implied. */
public record ComparisonSummary(Coverage coverage, long matched, long modified, long notVerifiable) {
    public enum Coverage { COMPLETE, INCOMPLETE, CANCELLED, NO_ENTRIES }
    public enum Scope { SUPPLIED_ENTRIES_ONLY }
    public enum Authentication { NOT_CHECKED }

    public ComparisonSummary {
        Objects.requireNonNull(coverage);
        if (matched < 0 || modified < 0 || notVerifiable < 0) throw new IllegalArgumentException("Negative count");
        if (coverage == Coverage.COMPLETE && (notVerifiable != 0 || (matched == 0 && modified == 0))) {
            throw new IllegalArgumentException("Invalid complete coverage");
        }
        if (coverage == Coverage.INCOMPLETE && notVerifiable == 0) throw new IllegalArgumentException("Missing error count");
        if (coverage == Coverage.NO_ENTRIES && (matched != 0 || modified != 0 || notVerifiable != 0)) {
            throw new IllegalArgumentException("Nonempty result");
        }
    }

    public Scope scope() { return Scope.SUPPLIED_ENTRIES_ONLY; }
    public Authentication authentication() { return Authentication.NOT_CHECKED; }
}
