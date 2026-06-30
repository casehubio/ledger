package io.casehub.ledger.signing.azure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Converts EC raw R‖S signatures to DER-encoded format.
 *
 * <p>Azure Key Vault returns EC signatures as raw concatenated R and S components
 * (e.g., 64 bytes for P-256: 32-byte R + 32-byte S). JCA verification requires
 * DER-encoded ASN.1 {@code SEQUENCE { INTEGER r, INTEGER s }}.
 *
 * <p>This utility performs the conversion with proper leading-zero handling for
 * integers with the high bit set (per ASN.1 INTEGER encoding rules).
 *
 * <p><strong>Component sizes by curve:</strong>
 * <ul>
 *   <li>P-256 (secp256r1): 32 bytes</li>
 *   <li>P-384 (secp384r1): 48 bytes</li>
 *   <li>P-521 (secp521r1): 66 bytes</li>
 * </ul>
 *
 * <p>Pure Java — no BouncyCastle dependency.
 */
public final class EcSignatureConverter {

    private EcSignatureConverter() {
        // Utility class
    }

    /**
     * Converts raw R‖S bytes to DER-encoded ECDSA signature.
     *
     * @param rawSignature concatenated R and S components (length = 2 × componentSize)
     * @param componentSize size in bytes of R and S (e.g., 32 for P-256, 48 for P-384)
     * @return DER-encoded {@code SEQUENCE { INTEGER r, INTEGER s }}
     * @throws IllegalArgumentException if rawSignature length ≠ 2 × componentSize
     */
    public static byte[] rawToDer(final byte[] rawSignature, final int componentSize) {
        if (rawSignature.length != componentSize * 2) {
            throw new IllegalArgumentException(
                    "Raw signature length must be 2 × componentSize (" + (componentSize * 2)
                            + "), got " + rawSignature.length);
        }

        // Split R and S at midpoint
        final byte[] r = new byte[componentSize];
        final byte[] s = new byte[componentSize];
        System.arraycopy(rawSignature, 0, r, 0, componentSize);
        System.arraycopy(rawSignature, componentSize, s, 0, componentSize);

        // Encode as DER SEQUENCE { INTEGER r, INTEGER s }
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] rDer = encodeInteger(r);
            final byte[] sDer = encodeInteger(s);

            // SEQUENCE tag
            out.write(0x30);
            // SEQUENCE length (sum of R and S DER encodings)
            final int contentLen = rDer.length + sDer.length;
            if (contentLen > 127) {
                // Long-form DER length encoding: 1 byte for length-of-length, then length
                out.write(0x81); // long-form: 1 length byte follows
                out.write(contentLen);
            } else {
                // Short-form: length fits in 1 byte
                out.write(contentLen);
            }
            // R INTEGER
            out.write(rDer);
            // S INTEGER
            out.write(sDer);

            return out.toByteArray();
        } catch (final IOException e) {
            // ByteArrayOutputStream doesn't throw IOException in practice
            throw new RuntimeException("Failed to encode DER signature", e);
        }
    }

    /**
     * Encodes a raw unsigned integer as DER INTEGER.
     *
     * <p>ASN.1 INTEGER encoding rules: if the high bit (0x80) is set, prepend a 0x00 byte
     * so the value is not interpreted as negative.
     *
     * @param value raw unsigned integer bytes (big-endian)
     * @return DER INTEGER: tag (0x02) + length + value (with leading zero if needed)
     */
    private static byte[] encodeInteger(final byte[] value) throws IOException {
        // BigInteger handles leading-zero padding for ASN.1 INTEGER encoding
        final BigInteger bigInt = new BigInteger(1, value);  // Unsigned interpretation
        byte[] encoded = bigInt.toByteArray();

        // BigInteger.toByteArray() already adds leading zero if high bit is set
        // Build DER INTEGER: tag + length + value
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x02);  // INTEGER tag
        out.write(encoded.length);
        out.write(encoded);
        return out.toByteArray();
    }
}
