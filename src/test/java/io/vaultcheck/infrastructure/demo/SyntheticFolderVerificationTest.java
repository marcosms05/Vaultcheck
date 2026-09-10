package io.vaultcheck.infrastructure.demo;

import io.vaultcheck.application.VerifyFolder;
import io.vaultcheck.domain.FolderScan;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class SyntheticFolderVerificationTest {
    @Test void realSignedComparisonDetectsExpectedChanges() throws Exception {
        var result = new SyntheticFolderVerification().run(() -> false);
        assertEquals(FolderScan.Coverage.COMPLETE, result.coverage());
        assertEquals(5, result.differences().size());
        assertEquals(2, result.differences().stream().filter(d -> d.status() == VerifyFolder.Status.MATCHED).count());
        for (var state : java.util.List.of(VerifyFolder.Status.MODIFIED, VerifyFolder.Status.ADDED, VerifyFolder.Status.ABSENT))
            assertEquals(1, result.differences().stream().filter(d -> d.status() == state).count());
    }
    @Test void cancellationBeforeCreationStopsRun() {
        assertThrows(CancellationException.class, () -> new SyntheticFolderVerification().run(() -> true));
    }
    @Test void cancellationDuringPreparationStopsRun() {
        var calls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> new SyntheticFolderVerification().run(() -> calls.incrementAndGet() >= 3));
    }
}
