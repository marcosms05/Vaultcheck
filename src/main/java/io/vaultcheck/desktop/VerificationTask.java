package io.vaultcheck.desktop;

import io.vaultcheck.application.VerifyFolder;
import io.vaultcheck.infrastructure.demo.SyntheticFolderVerification;



/** Cooperative cancellation: terminal Task state is published only after the worker's cleanup returns. */
final class VerificationTask extends CooperativeTask<VerifyFolder.Result> {



    @Override protected VerifyFolder.Result call() throws Exception {
        return new SyntheticFolderVerification().run(this::cancellationRequested);
    }
}

