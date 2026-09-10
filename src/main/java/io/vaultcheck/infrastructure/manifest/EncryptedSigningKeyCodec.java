package io.vaultcheck.infrastructure.manifest;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Experimental encrypted container for PKCS#8 Ed25519 keys. No filesystem access or key cache. */
public final class EncryptedSigningKeyCodec {
    private static final int MAGIC = 0x56434b59; // VCKY
    private static final byte VERSION = 1;
    public static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int HEADER_BYTES = 39;
    private static final int MAX_PRIVATE_BYTES = 128;
    public static final int MAX_CONTAINER_BYTES = HEADER_BYTES + MAX_PRIVATE_BYTES + 16;
    private final SecureRandom random = new SecureRandom();

    public byte[] protect(PrivateKey key, char[] password) throws GeneralSecurityException {
        Objects.requireNonNull(key);
        validatePassword(password);
        byte[] clear = key.getEncoded();
        if (clear == null) throw new GeneralSecurityException("Private key is not exportable");
        try {
            parsePrivate(clear);
            byte[] salt = new byte[SALT_BYTES];
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(salt);
            random.nextBytes(nonce);
            byte[] header = ByteBuffer.allocate(HEADER_BYTES).putInt(MAGIC).put(VERSION).putInt(ITERATIONS)
                    .put(salt).put(nonce).putShort((short) (clear.length + 16)).array();
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, clear, password, salt, nonce, header);
            return ByteBuffer.allocate(header.length + encrypted.length).put(header).put(encrypted).array();
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    /** Caller owns the returned private key and must not cache it beyond the signing operation. */
    public PrivateKey unlock(byte[] container, char[] password) throws GeneralSecurityException {
        Objects.requireNonNull(container);
        validatePassword(password);
        if (container.length < HEADER_BYTES + 17 || container.length > MAX_CONTAINER_BYTES) {
            throw new GeneralSecurityException("Invalid encrypted key container");
        }
        // Snapshot caller-owned input before parsing/authenticating it.
        byte[] encoded = container.clone();
        byte[] clear = null;
        try {
            var input = ByteBuffer.wrap(encoded);
            if (input.getInt() != MAGIC || input.get() != VERSION || input.getInt() != ITERATIONS) {
                throw new GeneralSecurityException("Unsupported encrypted key format");
            }
            byte[] salt = new byte[SALT_BYTES]; input.get(salt);
            byte[] nonce = new byte[NONCE_BYTES]; input.get(nonce);
            int ciphertextLength = Short.toUnsignedInt(input.getShort());
            if (ciphertextLength != input.remaining()) throw new GeneralSecurityException("Invalid encrypted key length");
            byte[] header = Arrays.copyOf(encoded, HEADER_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(encoded, HEADER_BYTES, encoded.length);
            try {
                clear = crypt(Cipher.DECRYPT_MODE, ciphertext, password, salt, nonce, header);
            } catch (GeneralSecurityException e) {
                // Wrong password and authentication failure use the same outward-facing error.
                throw new GeneralSecurityException("Unable to unlock key: password incorrect or container altered");
            }
            return parsePrivate(clear);
        } finally {
            if (clear != null) Arrays.fill(clear, (byte) 0);
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static PrivateKey parsePrivate(byte[] encoded) throws GeneralSecurityException {
        if (encoded.length == 0 || encoded.length > MAX_PRIVATE_BYTES) throw new GeneralSecurityException("Unsupported private key size");
        var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        if (!(key instanceof EdECPrivateKey ed) || !ed.getParams().getName().equals("Ed25519")) {
            throw new GeneralSecurityException("Only Ed25519 signing keys are supported");
        }
        return key;
    }

    private static byte[] crypt(int mode, byte[] input, char[] password, byte[] salt, byte[] nonce, byte[] header)
            throws GeneralSecurityException {
        var spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        byte[] derived = null;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(derived, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(header);
            return cipher.doFinal(input);
        } finally {
            spec.clearPassword();
            if (derived != null) Arrays.fill(derived, (byte) 0);
        }
    }

    private static void validatePassword(char[] password) {
        Objects.requireNonNull(password);
        if (password.length < 12 || password.length > 1024) {
            throw new IllegalArgumentException("Passphrase must contain between 12 and 1024 characters");
        }
    }
}
