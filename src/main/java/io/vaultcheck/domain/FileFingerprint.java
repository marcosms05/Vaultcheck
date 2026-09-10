package io.vaultcheck.domain;

public record FileFingerprint(long size, String sha256) {
    public FileFingerprint {
        if (size < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid fingerprint");
        }
    }
}
