package io.vaultcheck.desktop;
import io.vaultcheck.domain.FolderScan;
import io.vaultcheck.infrastructure.files.WindowsFolderScanner;
import java.nio.file.Path;
import java.util.Objects;
/** Read-only inventory, without reference authentication or signer enrollment. */
final class FolderScanTask extends CooperativeTask<FolderScan> {
    private final Path root;
    FolderScanTask(Path root) { this.root = Objects.requireNonNull(root); }
    @Override protected FolderScan call() {
        return new WindowsFolderScanner().scan(root, this::cancellationRequested, entry -> { });
    }
}
