package io.vaultcheck.infrastructure.manifest;

import io.vaultcheck.domain.FileFingerprint;
import io.vaultcheck.domain.ManifestPath;
import io.vaultcheck.domain.ReferenceEntry;
import io.vaultcheck.domain.ReferenceManifest;
import io.vaultcheck.application.SignedReferenceReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;

/** Experimental binary format v1. Uses standard Ed25519, never Java object deserialization.
 * The verification key must come from outside the file. This class does not establish trust in it.
 */
public final class SignedManifestCodec implements SignedReferenceReader {
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int MAGIC = 0x56434d46; // VCMF: domain separation from other signed content
    private static final int VERSION = 1;
    private static final int SIGNATURE_BYTES = 64;
    private static final int MAX_PATH_BYTES = 16_384;

    public byte[] sign(ReferenceManifest manifest, PrivateKey signingKey) throws IOException, GeneralSecurityException {
        Objects.requireNonNull(manifest);
        Objects.requireNonNull(signingKey);
        var payloadBytes = new ByteArrayOutputStream();
        var payload = new DataOutputStream(payloadBytes);
        payload.writeInt(MAGIC);
        payload.writeByte(VERSION);
        payload.writeLong(manifest.createdEpochSecond());
        payload.writeInt(manifest.omittedEntries());
        payload.writeInt(manifest.entries().size());
        for (var entry : manifest.entries()) {
            byte[] path = entry.path().value().getBytes(StandardCharsets.UTF_8);
            if (path.length > MAX_PATH_BYTES || payloadBytes.size() + 2L + path.length + 8 + 32 > MAX_PAYLOAD_BYTES) {
                throw new IOException("Manifest exceeds pilot size limit");
            }
            payload.writeShort(path.length);
            payload.write(path);
            payload.writeLong(entry.expected().size());
            payload.write(HexFormat.of().parseHex(entry.expected().sha256()));
        }
        byte[] body = payloadBytes.toByteArray();
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKey);
        signer.update(body);
        byte[] signature = signer.sign();
        if (signature.length != SIGNATURE_BYTES) throw new GeneralSecurityException("Unexpected signature length");
        var envelope = new ByteArrayOutputStream();
        var output = new DataOutputStream(envelope);
        output.writeInt(body.length);
        output.write(body);
        output.write(signature);
        return envelope.toByteArray();
    }

    /** Returns only after signature and content validation. Does not close the caller's stream.
     * A valid signature means signed by supplied key, NOT that supplied key is trusted.
     */
    @Override public ReferenceManifest verifyAndRead(InputStream source, PublicKey verificationKey)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(source);
        Objects.requireNonNull(verificationKey);
        var input = new DataInputStream(source);
        int length = input.readInt();
        if (length < 21 || length > MAX_PAYLOAD_BYTES) throw new IOException("Invalid manifest length");
        byte[] body = new byte[length];
        input.readFully(body);
        byte[] signature = new byte[SIGNATURE_BYTES];
        input.readFully(signature);
        if (input.read() != -1) throw new IOException("Trailing envelope data");
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(verificationKey);
        verifier.update(body);
        if (!verifier.verify(signature)) throw new GeneralSecurityException("Invalid manifest signature");
        return decodeVerifiedPayload(body);
    }

    private ReferenceManifest decodeVerifiedPayload(byte[] body) throws IOException {
        var input = new DataInputStream(new ByteArrayInputStream(body));
        if (input.readInt() != MAGIC || input.readUnsignedByte() != VERSION) throw new IOException("Unsupported manifest format");
        long created = input.readLong();
        int omitted = input.readInt();
        int count = input.readInt();
        if (count < 0 || count > ReferenceManifest.MAX_ENTRIES) throw new IOException("Invalid entry count");
        var entries = new ArrayList<ReferenceEntry>(count);
        String previous = null;
        try {
            for (int i = 0; i < count; i++) {
                int pathLength = input.readUnsignedShort();
                if (pathLength == 0 || pathLength > MAX_PATH_BYTES) throw new IOException("Invalid path length");
                byte[] pathBytes = new byte[pathLength];
                input.readFully(pathBytes);
                String path = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(pathBytes)).toString();
                var relative = new ManifestPath(path);
                if (previous != null && previous.compareTo(path) >= 0) throw new IOException("Noncanonical path order");
                previous = path;
                long size = input.readLong();
                byte[] hash = new byte[32];
                input.readFully(hash);
                entries.add(new ReferenceEntry(relative, new FileFingerprint(size, HexFormat.of().formatHex(hash))));
            }
            if (input.read() != -1) throw new IOException("Trailing payload data");
            return new ReferenceManifest(created, omitted, entries);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid manifest content", e);
        }
    }
}
