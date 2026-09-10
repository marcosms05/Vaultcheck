package io.vaultcheck.desktop;
import javafx.concurrent.Task;
import java.util.concurrent.atomic.AtomicBoolean;
/** Cancellation is acknowledged only after call() returns and releases resources. */
abstract class CooperativeTask<T> extends Task<T> {
    private final AtomicBoolean cancellation = new AtomicBoolean();
    final void requestCancellation() { cancellation.set(true); }
    final boolean cancellationRequested() { return cancellation.get(); }
}
