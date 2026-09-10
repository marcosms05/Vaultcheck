package io.vaultcheck.application;

import io.vaultcheck.domain.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Authenticates first, then compares a bounded inventory. This is not a filesystem snapshot. */
public final class VerifyFolder {
    public enum Status { MATCHED, MODIFIED, ADDED, ABSENT, NOT_VERIFIABLE }
    public record Difference(ManifestPath path, Status status) { }
    private final TrustedSignerSource signers;
    private final SignedReferenceReader references;
    private final FolderInventory inventory;

    public VerifyFolder(TrustedSignerSource signers, SignedReferenceReader references, FolderInventory inventory) {
        this.signers = Objects.requireNonNull(signers);
        this.references = Objects.requireNonNull(references);
        this.inventory = Objects.requireNonNull(inventory);
    }

    public Result verify(Path root, InputStream source, String signer, BooleanSupplier cancelled)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(root); Objects.requireNonNull(source); Objects.requireNonNull(cancelled);
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
        var key = signers.requireTrusted(signer);
        var reference = references.verifyAndRead(source, key);
        var scan = inventory.scan(root, cancelled, entry -> { });
        var current = index(scan.entries());
        var expected = index(reference.entries());
        var differences = new ArrayList<Difference>();
        // A case-colliding name makes the affected entry ambiguous even if one read succeeded.
        var ambiguous = new HashSet<String>();
        for (var issue : scan.issues()) {
            if (issue.reason() == FolderScan.Reason.DUPLICATE_NAME) ambiguous.add(fold(issue.relativePath()));
        }
        var names = new TreeSet<String>();
        names.addAll(expected.keySet()); names.addAll(current.keySet());
        for (var name : names) {
            var before = expected.get(name);
            var now = current.get(name);
            Status status;
            boolean collision = ambiguous.stream().anyMatch(prefix -> name.equals(prefix) || name.startsWith(prefix + "/"));
            if (collision) status = Status.NOT_VERIFIABLE;
            else if (now == null) status = scan.coverage() == FolderScan.Coverage.COMPLETE ? Status.ABSENT : Status.NOT_VERIFIABLE;
            else if (before == null) status = reference.hasOmissions() ? Status.NOT_VERIFIABLE : Status.ADDED;
            else status = before.expected().equals(now.expected()) ? Status.MATCHED : Status.MODIFIED;
            differences.add(new Difference(before == null ? now.path() : before.path(), status));
        }
        var coverage = scan.coverage();
        if (coverage == FolderScan.Coverage.COMPLETE && reference.hasOmissions()) coverage = FolderScan.Coverage.INCOMPLETE;
        return new Result(signer, reference.omittedEntries(), coverage, differences, scan.issues());
    }

    private static String fold(String path) { return path.toUpperCase(Locale.ROOT); }
    private static Map<String, ReferenceEntry> index(List<ReferenceEntry> entries) {
        if (entries.size() > ReferenceManifest.MAX_ENTRIES) throw new IllegalArgumentException("Inventory exceeds limit");
        var index = new HashMap<String, ReferenceEntry>();
        for (var entry : entries) {
            if (index.putIfAbsent(fold(entry.path().value()), entry) != null) throw new IllegalArgumentException("Ambiguous inventory");
        }
        return index;
    }

    public static final class Result {
        private final String signer;
        private final int omissions;
        private final FolderScan.Coverage coverage;
        private final List<Difference> differences;
        private final List<FolderScan.Issue> issues;
        private Result(String signer, int omissions, FolderScan.Coverage coverage,
                       List<Difference> differences, List<FolderScan.Issue> issues) {
            this.signer = signer; this.omissions = omissions; this.coverage = coverage;
            this.differences = List.copyOf(differences); this.issues = List.copyOf(issues);
        }
        public VerifySignedReference.Result.Authentication authentication() {
            return VerifySignedReference.Result.Authentication.VALID_SIGNATURE_TRUSTED_PIN;
        }
        public String signerFingerprint() { return signer; }
        public int referenceOmissions() { return omissions; }
        public FolderScan.Coverage coverage() { return coverage; }
        public List<Difference> differences() { return differences; }
        public List<FolderScan.Issue> issues() { return issues; }
    }
}
