package io.casehub.ledger.signing.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EcSignatureConverter} — raw R‖S to DER conversion for EC signatures.
 *
 * <p>Azure Key Vault returns raw R‖S bytes (concatenated R and S components). JCA verification
 * requires DER-encoded SEQUENCE { INTEGER r, INTEGER s }. This utility handles the conversion.
 */
class EcSignatureConverterTest {

    /**
     * P-256 round-trip: generate ECDSA signature with JCA → convert DER→raw → convert raw→DER →
     * verify result matches original DER.
     */
    @Test
    void p256RoundTrip() throws Exception {
        // Generate a real P-256 key pair
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        final KeyPair keyPair = gen.generateKeyPair();

        // Sign test data with JCA (produces DER-encoded signature)
        final byte[] testData = "test message".getBytes();
        final Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(testData);
        final byte[] derSignature = signer.sign();

        // Convert DER → raw R‖S (split at midpoint — P-256 = 32-byte components)
        final byte[] rawSignature = derToRaw(derSignature, 32);
        assertThat(rawSignature).hasSize(64);  // 32 bytes R + 32 bytes S

        // Convert raw R‖S → DER using EcSignatureConverter
        final byte[] reconvertedDer = EcSignatureConverter.rawToDer(rawSignature, 32);

        // Verify reconverted DER matches original DER
        // (DER encoding is deterministic for the same R and S values)
        assertThat(reconvertedDer).isEqualTo(derSignature);

        // Also verify the reconverted signature is cryptographically valid
        final Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(testData);
        assertThat(verifier.verify(reconvertedDer)).isTrue();
    }

    /**
     * Leading-zero handling: construct R with high bit set, verify DER pads with 0x00.
     */
    @Test
    void leadingZeroHandling() throws Exception {
        // Construct a raw signature where R has high bit set (requires leading zero in DER)
        final byte[] rawSignature = new byte[64];  // P-256: 32 bytes R + 32 bytes S

        // R: 0xFF000000... (high bit set)
        rawSignature[0] = (byte) 0xFF;
        for (int i = 1; i < 32; i++) {
            rawSignature[i] = 0x00;
        }

        // S: 0x7F000000... (high bit not set)
        rawSignature[32] = (byte) 0x7F;
        for (int i = 33; i < 64; i++) {
            rawSignature[i] = 0x00;
        }

        // Convert to DER
        final byte[] derSignature = EcSignatureConverter.rawToDer(rawSignature, 32);

        // Parse DER to verify structure
        int offset = 0;
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x30);  // SEQUENCE tag
        int seqLength = derSignature[offset++] & 0xFF;

        // INTEGER R
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x02);  // INTEGER tag
        int rLength = derSignature[offset++] & 0xFF;
        // R should have leading 0x00 because high bit was set
        assertThat(rLength).isEqualTo(33);  // 32 bytes + 1 leading zero
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x00);  // Leading zero
        assertThat(derSignature[offset]).isEqualTo((byte) 0xFF);  // First byte of R
        offset += rLength - 1;

        // INTEGER S
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x02);  // INTEGER tag
        int sLength = derSignature[offset++] & 0xFF;
        // S should NOT have leading 0x00 because high bit was not set
        assertThat(sLength).isEqualTo(32);
        assertThat(derSignature[offset]).isEqualTo((byte) 0x7F);  // First byte of S
    }

    /**
     * P-384 round-trip: 48-byte components.
     */
    @Test
    void p384RoundTrip() throws Exception {
        // Generate a real P-384 key pair
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp384r1"));
        final KeyPair keyPair = gen.generateKeyPair();

        // Sign test data with JCA (produces DER-encoded signature)
        final byte[] testData = "test message P-384".getBytes();
        final Signature signer = Signature.getInstance("SHA384withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(testData);
        final byte[] derSignature = signer.sign();

        // Convert DER → raw R‖S (split at midpoint — P-384 = 48-byte components)
        final byte[] rawSignature = derToRaw(derSignature, 48);
        assertThat(rawSignature).hasSize(96);  // 48 bytes R + 48 bytes S

        // Convert raw R‖S → DER using EcSignatureConverter
        final byte[] reconvertedDer = EcSignatureConverter.rawToDer(rawSignature, 48);

        // Verify reconverted DER matches original DER
        assertThat(reconvertedDer).isEqualTo(derSignature);

        // Also verify the reconverted signature is cryptographically valid
        final Signature verifier = Signature.getInstance("SHA384withECDSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(testData);
        assertThat(verifier.verify(reconvertedDer)).isTrue();
    }

    /**
     * P-521 round-trip: 66-byte components, tests long-form DER length encoding.
     *
     * <p>P-521 signatures can exceed 127 bytes total content length (when both R and S have
     * leading zeros), requiring DER long-form length encoding (0x81 <length>).
     */
    @Test
    void p521RoundTrip() throws Exception {
        // Generate a real P-521 key pair
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp521r1"));
        final KeyPair keyPair = gen.generateKeyPair();

        // Sign test data with JCA (produces DER-encoded signature)
        final byte[] testData = "test message P-521".getBytes();
        final Signature signer = Signature.getInstance("SHA512withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(testData);
        final byte[] derSignature = signer.sign();

        // Convert DER → raw R‖S (split at midpoint — P-521 = 66-byte components)
        final byte[] rawSignature = derToRaw(derSignature, 66);
        assertThat(rawSignature).hasSize(132);  // 66 bytes R + 66 bytes S

        // Convert raw R‖S → DER using EcSignatureConverter
        final byte[] reconvertedDer = EcSignatureConverter.rawToDer(rawSignature, 66);

        // Verify the reconverted signature is cryptographically valid
        // (DER encoding may differ slightly due to leading zero stripping, but signature must verify)
        final Signature verifier = Signature.getInstance("SHA512withECDSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(testData);
        assertThat(verifier.verify(reconvertedDer)).isTrue();
    }

    /**
     * Explicit test for DER long-form length encoding when content length > 127.
     *
     * <p>Constructs a raw signature where both R and S have leading zeros (to maximize
     * DER encoded size), forcing content length > 127 bytes to trigger long-form encoding.
     */
    @Test
    void longFormLengthEncoding() throws Exception {
        // Construct P-521 raw signature with both R and S having high bit set
        // → DER will add leading zeros → total content length > 127
        final byte[] rawSignature = new byte[132];  // P-521: 66 bytes R + 66 bytes S

        // R: 0xFF followed by 65 bytes of 0xFF
        for (int i = 0; i < 66; i++) {
            rawSignature[i] = (byte) 0xFF;
        }

        // S: 0xFF followed by 65 bytes of 0xFF
        for (int i = 66; i < 132; i++) {
            rawSignature[i] = (byte) 0xFF;
        }

        // Convert to DER
        final byte[] derSignature = EcSignatureConverter.rawToDer(rawSignature, 66);

        // Parse DER to verify long-form length encoding was used
        int offset = 0;
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x30);  // SEQUENCE tag

        // Check for long-form length (0x81 means "1 byte of length follows")
        final int lengthByte = derSignature[offset++] & 0xFF;
        assertThat(lengthByte).isEqualTo(0x81);  // Long-form indicator

        // Read actual content length
        final int contentLength = derSignature[offset++] & 0xFF;
        // Each INTEGER: tag (1) + length (1) + value (67 for 0xFF with leading zero)
        // Total: (1 + 1 + 67) + (1 + 1 + 67) = 138 bytes
        assertThat(contentLength).isEqualTo(138);

        // Verify R INTEGER structure
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x02);  // INTEGER tag
        int rLength = derSignature[offset++] & 0xFF;
        assertThat(rLength).isEqualTo(67);  // 66 bytes + 1 leading zero
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x00);  // Leading zero
        assertThat(derSignature[offset]).isEqualTo((byte) 0xFF);  // First byte of R
        offset += rLength - 1;

        // Verify S INTEGER structure
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x02);  // INTEGER tag
        int sLength = derSignature[offset++] & 0xFF;
        assertThat(sLength).isEqualTo(67);  // 66 bytes + 1 leading zero
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x00);  // Leading zero
        assertThat(derSignature[offset]).isEqualTo((byte) 0xFF);  // First byte of S
    }

    /**
     * Helper: converts DER-encoded ECDSA signature to raw R‖S bytes.
     *
     * <p>This is the inverse of {@link EcSignatureConverter#rawToDer} — used for testing only.
     * Extracts R and S from DER SEQUENCE, strips leading zeros, pads to component size.
     */
    private byte[] derToRaw(final byte[] derSignature, final int componentSize) throws Exception {
        // Parse DER SEQUENCE { INTEGER r, INTEGER s }
        // DER structure:
        //   0x30 <total-length> 0x02 <r-length> <r-bytes> 0x02 <s-length> <s-bytes>
        // Length can be short-form (1 byte) or long-form (0x81 <length-byte>)

        int offset = 0;
        // Skip SEQUENCE tag
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x30);
        // Skip SEQUENCE length (short-form or long-form)
        int lengthByte = derSignature[offset++] & 0xFF;
        if (lengthByte == 0x81) {
            // Long-form: skip the actual length byte
            offset++;
        }
        // else: short-form, already skipped

        // Extract R
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x02);  // INTEGER tag
        int rLength = derSignature[offset++] & 0xFF;
        byte[] r = new byte[rLength];
        System.arraycopy(derSignature, offset, r, 0, rLength);
        offset += rLength;

        // Extract S
        assertThat(derSignature[offset++]).isEqualTo((byte) 0x02);  // INTEGER tag
        int sLength = derSignature[offset++] & 0xFF;
        byte[] s = new byte[sLength];
        System.arraycopy(derSignature, offset, s, 0, sLength);

        // Strip leading zero bytes (DER padding for high bit set)
        r = stripLeadingZeros(r);
        s = stripLeadingZeros(s);

        // Pad to component size (R and S must be exactly componentSize bytes)
        final byte[] raw = new byte[componentSize * 2];
        System.arraycopy(r, 0, raw, componentSize - r.length, r.length);
        System.arraycopy(s, 0, raw, componentSize * 2 - s.length, s.length);

        return raw;
    }

    private byte[] stripLeadingZeros(byte[] bytes) {
        int firstNonZero = 0;
        while (firstNonZero < bytes.length && bytes[firstNonZero] == 0) {
            firstNonZero++;
        }
        if (firstNonZero == 0) return bytes;
        final byte[] stripped = new byte[bytes.length - firstNonZero];
        System.arraycopy(bytes, firstNonZero, stripped, 0, stripped.length);
        return stripped;
    }
}
