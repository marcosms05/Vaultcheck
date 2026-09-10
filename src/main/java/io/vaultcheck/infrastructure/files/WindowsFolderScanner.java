package io.vaultcheck.infrastructure.files;

import io.vaultcheck.domain.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bounded local pilot. Directories remain retained throughout enumeration and descent. */
public final class WindowsFolderScanner implements io.vaultcheck.application.FolderInventory {
    public record Limits(int files, int visited, int depth) {
        public Limits {
            if (files < 1 || files > ReferenceManifest.MAX_ENTRIES || visited < 1 || visited > 10_000 || depth < 0 || depth > 32) {
                throw new IllegalArgumentException("Unsupported scan limits");
            }
        }
        public static Limits defaults() { return new Limits(1_000, 4_096, 32); }
    }
    private final Limits limits;
    public WindowsFolderScanner() { this(Limits.defaults()); }
    public WindowsFolderScanner(Limits limits) { this.limits = Objects.requireNonNull(limits); }

    public FolderScan scan(Path selectedRoot, BooleanSupplier cancelled, Consumer<ReferenceEntry> progress) {
        Objects.requireNonNull(selectedRoot); Objects.requireNonNull(cancelled); Objects.requireNonNull(progress);
        var state = new State(selectedRoot.toAbsolutePath().normalize(), cancelled, progress);
        try {
            state.checkCancellation();
            try (var root = WindowsHandleFingerprintReader.retainDirectory(state.root)) {
                state.walk(root, 0);
            }
        } catch (CancellationException e) {
            return state.result(FolderScan.Coverage.CANCELLED);
        } catch (LimitReached e) {
            return state.result(FolderScan.Coverage.LIMIT_REACHED);
        } catch (IOException | DirectoryIteratorException e) {
            state.issues.add(new FolderScan.Issue(".", FolderScan.Reason.UNREADABLE));
        }
        return state.result(state.issues.isEmpty() ? FolderScan.Coverage.COMPLETE : FolderScan.Coverage.INCOMPLETE);
    }

    private final class State {
        final Path root;
        final BooleanSupplier cancelled;
        final Consumer<ReferenceEntry> progress;
        final ArrayList<ReferenceEntry> entries = new ArrayList<>();
        final ArrayList<FolderScan.Issue> issues = new ArrayList<>();
        final HashSet<String> names = new HashSet<>();
        final WindowsHandleFingerprintReader reader = new WindowsHandleFingerprintReader();
        int visited;
        State(Path root, BooleanSupplier cancelled, Consumer<ReferenceEntry> progress) {
            this.root = root; this.cancelled = cancelled; this.progress = progress;
        }
        void checkCancellation() {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
        }
        void walk(WindowsHandleFingerprintReader.DirectoryLease directory, int depth) throws IOException {
            try (var stream = directory.entries()) {
                var iterator = stream.iterator();
                while (true) {
                    checkCancellation();
                    if (!iterator.hasNext()) break;
                    if (visited == limits.visited()) throw new LimitReached();
                    Path child = iterator.next(); visited++;
                    String relative = root.relativize(child).toString().replace('\\', '/');
                    ManifestPath path;
                    try { path = new ManifestPath(relative); }
                    catch (IllegalArgumentException badName) { issues.add(new FolderScan.Issue(relative, FolderScan.Reason.UNSUPPORTED_PATH)); continue; }
                    if (!names.add(relative.toUpperCase(Locale.ROOT))) {
                        issues.add(new FolderScan.Issue(relative, FolderScan.Reason.DUPLICATE_NAME)); continue;
                    }
                    BasicFileAttributes attributes;
                    try { attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
                    catch (IOException failure) { issues.add(new FolderScan.Issue(relative, FolderScan.Reason.UNREADABLE)); continue; }
                    if (attributes.isSymbolicLink() || attributes.isOther()) {
                        issues.add(new FolderScan.Issue(relative, FolderScan.Reason.REDIRECTED_OR_SPECIAL)); continue;
                    }
                    if (attributes.isDirectory()) {
                        if (depth == limits.depth()) throw new LimitReached();
                        try (var nested = directory.child(child.getFileName().toString())) { walk(nested, depth + 1); }
                        catch (IOException | DirectoryIteratorException failure) { issues.add(new FolderScan.Issue(relative, FolderScan.Reason.UNREADABLE)); }
                    } else if (attributes.isRegularFile()) {
                        if (entries.size() == limits.files()) throw new LimitReached();
                        ReferenceEntry entry;
                        try { entry = new ReferenceEntry(path, reader.read(root, path, cancelled)); }
                        catch (IOException failure) { issues.add(new FolderScan.Issue(relative, FolderScan.Reason.UNREADABLE)); continue; }
                        // Consumer failures propagate, never becoming a successful scan result.
                        progress.accept(entry);
                        entries.add(entry);
                    } else { issues.add(new FolderScan.Issue(relative, FolderScan.Reason.REDIRECTED_OR_SPECIAL)); }
                }
            }
        }
        FolderScan result(FolderScan.Coverage coverage) { return new FolderScan(coverage, entries, issues); }
    }
    private static final class LimitReached extends RuntimeException {}
}
