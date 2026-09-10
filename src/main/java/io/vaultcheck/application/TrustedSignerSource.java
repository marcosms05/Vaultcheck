package io.vaultcheck.application;

import java.security.GeneralSecurityException;
import java.security.PublicKey;

/** Returns a key already trusted by local policy; never imports trust from the manifest. */
@FunctionalInterface
public interface TrustedSignerSource {
    PublicKey requireTrusted(String fingerprint) throws GeneralSecurityException;
}
