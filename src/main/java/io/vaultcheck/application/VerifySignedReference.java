package io.vaultcheck.application;

import io.vaultcheck.domain.ComparisonSummary;
import io.vaultcheck.domain.EntryComparison;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Authenticates the reference before any target-file read. Does not enumerate the target folder. */
public final class VerifySignedReference {
    private final TrustedSignerSource trustedSigners;
    private final SignedReferenceReader references;
    private final CompareEntries comparison;

    public VerifySignedReference(TrustedSignerSource trustedSigners, SignedReferenceReader references,
                                 RootedFingerprintReader files) {
        this.trustedSigners = Objects.requireNonNull(trustedSigners);
        this.references = Objects.requireNonNull(references);
        comparison = new CompareEntries(files);
    }

    public Result verify(Path root, InputStream manifest, String signerFingerprint, BooleanSupplier cancelled,
                         Consumer<EntryComparison> output) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(root);
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(cancelled);
        Objects.requireNonNull(output);
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
        var key = trustedSigners.requireTrusted(signerFingerprint);
        var reference = references.verifyAndRead(manifest, key);
        var compared = comparison.compare(root, reference.entries(), cancelled, output);
        return new Result(signerFingerprint, reference.omittedEntries(), compared);
    }

    /** Only this orchestration can construct this result; it carries the scope of the checked reference. */
    public static final class Result {
        public enum Authentication { VALID_SIGNATURE_TRUSTED_PIN }
        private final String signerFingerprint;
        private final int referenceOmissions;
        private final ComparisonSummary compared;
        private Result(String signerFingerprint, int referenceOmissions, ComparisonSummary compared) {
            this.signerFingerprint = signerFingerprint;
            this.referenceOmissions = referenceOmissions;
            this.compared = compared;
        }
        public String signerFingerprint() { return signerFingerprint; }
        public Authentication authentication() { return Authentication.VALID_SIGNATURE_TRUSTED_PIN; }
        public int referenceOmissions() { return referenceOmissions; }
        public ComparisonSummary entries() { return compared; }
        public ComparisonSummary.Scope scope() { return ComparisonSummary.Scope.SUPPLIED_ENTRIES_ONLY; }
        public ComparisonSummary.Coverage coverage() {
            if (compared.coverage() == ComparisonSummary.Coverage.CANCELLED) return compared.coverage();
            return referenceOmissions > 0 ? ComparisonSummary.Coverage.INCOMPLETE : compared.coverage();
        }
    }
}
