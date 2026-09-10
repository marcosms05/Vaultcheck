package io.vaultcheck.infrastructure.manifest;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedSigningKeyCodecTest {
    private static KeyPair identity;
    private static final char[] PASSWORD = "synthetic demo passphrase only".toCharArray();
    private final EncryptedSigningKeyCodec codec = new EncryptedSigningKeyCodec();
    @TempDir Path temporary;
    @BeforeAll static void keys() throws Exception { identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }

    @Test void encryptedExportCanBeRestoredAndUsedToSign() throws Exception {
        var encrypted = codec.protect(identity.getPrivate(), PASSWORD);
        var backup = Files.write(temporary.resolve("synthetic.vckey"), encrypted);
        var restored = codec.unlock(Files.readAllBytes(backup), PASSWORD);
        var signature = Signature.getInstance("Ed25519"); signature.initSign(restored); signature.update(new byte[] {1,2,3});
        var bytes = signature.sign();
        signature.initVerify(identity.getPublic()); signature.update(new byte[] {1,2,3});
        assertTrue(signature.verify(bytes));
        assertFalse(Arrays.equals(identity.getPrivate().getEncoded(), encrypted));
    }
    @Test void eachExportUsesFreshSaltAndNonce() throws Exception {
        var one = codec.protect(identity.getPrivate(), PASSWORD);
        var two = codec.protect(identity.getPrivate(), PASSWORD);
        assertFalse(Arrays.equals(Arrays.copyOfRange(one, 9, 25), Arrays.copyOfRange(two, 9, 25)));
        assertFalse(Arrays.equals(Arrays.copyOfRange(one, 25, 37), Arrays.copyOfRange(two, 25, 37)));
    }
    @Test void wrongPasswordAndCiphertextTamperingDoNotReturnAKey() throws Exception {
        var encoded = codec.protect(identity.getPrivate(), PASSWORD);
        var wrong = assertThrows(GeneralSecurityException.class, () -> codec.unlock(encoded, "another synthetic password".toCharArray()));
        encoded[encoded.length - 1] ^= 1;
        var altered = assertThrows(GeneralSecurityException.class, () -> codec.unlock(encoded, PASSWORD));
        assertEquals(wrong.getMessage(), altered.getMessage());
    }
    @Test void headerSaltAndNonceAreAuthenticated() throws Exception {
        var encoded = codec.protect(identity.getPrivate(), PASSWORD);
        for (int offset : new int[] {9, 25}) {
            var altered = encoded.clone(); altered[offset] ^= 1;
            assertThrows(GeneralSecurityException.class, () -> codec.unlock(altered, PASSWORD));
        }
    }
    @Test void malformedLengthsAndKdfCostAreRejected() throws Exception {
        var encoded = codec.protect(identity.getPrivate(), PASSWORD);
        var hugeCost = encoded.clone(); ByteBuffer.wrap(hugeCost).putInt(5, Integer.MAX_VALUE);
        assertThrows(GeneralSecurityException.class, () -> codec.unlock(hugeCost, PASSWORD));
        assertThrows(GeneralSecurityException.class, () -> codec.unlock(Arrays.copyOf(encoded, encoded.length - 1), PASSWORD));
        assertThrows(GeneralSecurityException.class, () -> codec.unlock(Arrays.copyOf(encoded, encoded.length + 1), PASSWORD));
        assertThrows(GeneralSecurityException.class, () -> codec.unlock(new byte[0], PASSWORD));
        assertThrows(GeneralSecurityException.class, () -> codec.unlock(new byte[EncryptedSigningKeyCodec.MAX_CONTAINER_BYTES + 1], PASSWORD));
    }
    @Test void callerOwnedSecretsAndContainerAreNotMutated() throws Exception {
        char[] supplied = PASSWORD.clone();
        var encoded = codec.protect(identity.getPrivate(), supplied); var copy = encoded.clone();
        codec.unlock(encoded, supplied);
        assertArrayEquals(PASSWORD, supplied);
        assertArrayEquals(copy, encoded);
    }
    @Test void unsupportedKeyAndPasswordLengthsAreRejected() throws Exception {
        var other = KeyPairGenerator.getInstance("EC").generateKeyPair().getPrivate();
        assertThrows(GeneralSecurityException.class, () -> codec.protect(other, PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> codec.protect(identity.getPrivate(), new char[11]));
        assertThrows(IllegalArgumentException.class, () -> codec.protect(identity.getPrivate(), new char[1025]));
    }
}
