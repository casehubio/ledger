package io.casehub.ledger.signing.gcp;

import java.io.IOException;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.PublicKey;

/**
 * Production wrapper for {@link KeyManagementServiceClient}.
 *
 * <p>Delegates all calls to the real GCP SDK. Auth uses Application Default Credentials.
 */
final class DefaultGcpKmsClientWrapper implements GcpKmsClientWrapper {

    private final KeyManagementServiceClient client;

    DefaultGcpKmsClientWrapper() {
        try {
            this.client = KeyManagementServiceClient.create();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create GCP KMS client", e);
        }
    }

    @Override
    public PublicKey getPublicKey(final String versionName) {
        return client.getPublicKey(CryptoKeyVersionName.parse(versionName));
    }

    @Override
    public CryptoKeyVersion getCryptoKeyVersion(final String versionName) {
        return client.getCryptoKeyVersion(CryptoKeyVersionName.parse(versionName));
    }

    @Override
    public AsymmetricSignResponse asymmetricSign(final AsymmetricSignRequest request) {
        return client.asymmetricSign(request);
    }
}
