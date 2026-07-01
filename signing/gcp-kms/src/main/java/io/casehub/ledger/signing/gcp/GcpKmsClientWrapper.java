package io.casehub.ledger.signing.gcp;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.PublicKey;

/**
 * Thin interface wrapper around {@link com.google.cloud.kms.v1.KeyManagementServiceClient}.
 *
 * <p>{@code KeyManagementServiceClient} is a concrete class (not an interface), so it cannot
 * be mocked directly with Mockito. This wrapper allows testing without WireMock complexity.
 *
 * <p>Production code uses {@link DefaultGcpKmsClientWrapper} (delegates to real SDK).
 * Tests inject a Mockito mock of this interface.
 */
public interface GcpKmsClientWrapper {

    /**
     * Gets the public key for a CryptoKeyVersion.
     *
     * @param versionName CryptoKeyVersion resource name
     * @return the public key
     */
    PublicKey getPublicKey(String versionName);

    /**
     * Signs a digest via GCP Cloud KMS.
     *
     * @param request the sign request
     * @return the sign response
     */
    AsymmetricSignResponse asymmetricSign(AsymmetricSignRequest request);
}
