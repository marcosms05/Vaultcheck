package io.vaultcheck.application;

import io.vaultcheck.domain.ReferenceManifest;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

@FunctionalInterface
public interface SignedReferenceReader {
    ReferenceManifest verifyAndRead(InputStream source, PublicKey key) throws IOException, GeneralSecurityException;
}
