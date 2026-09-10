package io.vaultcheck;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Compatibility experiment only; not a manifest format or trust store. */
class CryptoSpikeTest {
    @Test void ed25519DetectsTamperingAndWrongIdentity() throws Exception {
        var generator = KeyPairGenerator.getInstance("Ed25519");
        var identity = generator.generateKeyPair();
        byte[] payload = "reference bytes".getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.getPrivate());
        signer.update(payload);
        var signature = signer.sign();
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(identity.getPublic());
        verifier.update(payload);
        assertTrue(verifier.verify(signature));
        verifier.initVerify(identity.getPublic());
        verifier.update("modified bytes".getBytes(StandardCharsets.UTF_8));
        assertFalse(verifier.verify(signature));
        verifier.initVerify(generator.generateKeyPair().getPublic());
        verifier.update(payload);
        assertFalse(verifier.verify(signature));
    }
}
