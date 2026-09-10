package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.application.TrustedSignerSource;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Immutable in-memory policy snapshot. Entries must be approved outside the imported reference. */
public final class PinnedSigners implements TrustedSignerSource {
    private final Map<String, PublicKey> keys;

    public PinnedSigners(Collection<PublicKey> approvedKeys) throws GeneralSecurityException {
        Objects.requireNonNull(approvedKeys);
        if (approvedKeys.size() > 100) throw new GeneralSecurityException("Too many pinned signers");
        var snapshot = new HashMap<String, PublicKey>();
        for (var key : approvedKeys) {
            var copy = canonicalKey(key);
            snapshot.put(fingerprint(copy), copy);
        }
        keys = Map.copyOf(snapshot);
    }

    public static String fingerprint(PublicKey key) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalKey(key).getEncoded()));
    }

    @Override public PublicKey requireTrusted(String fingerprint) throws GeneralSecurityException {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new GeneralSecurityException("Invalid signer identifier");
        }
        var key = keys.get(fingerprint);
        if (key == null) throw new GeneralSecurityException("Signer is not trusted by this policy");
        return key;
    }

    private static PublicKey canonicalKey(PublicKey key) throws GeneralSecurityException {
        Objects.requireNonNull(key);
        byte[] encoded = key.getEncoded();
        if (encoded == null || encoded.length > 128) throw new GeneralSecurityException("Unsupported public key encoding");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
