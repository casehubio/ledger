# GCP Cloud KMS Signing Example

This example demonstrates remote signing of ledger entries via Google Cloud Key Management Service. The private key never leaves GCP Cloud KMS; all signing operations happen via the gRPC API.

## Features

- Remote signing via GCP Cloud KMS gRPC API
- Public key caching with scheduled refresh
- Actor-to-key mapping via configuration
- Mock KMS client for testing

## Production Requirements

### GCP Cloud KMS Configuration

1. Create a key ring (one-time setup per region):
   ```bash
   gcloud kms keyrings create ledger-signing \
     --location us-east1
   ```

2. Create an asymmetric signing key:
   ```bash
   gcloud kms keys create reviewer-signing-key \
     --location us-east1 \
     --keyring ledger-signing \
     --purpose asymmetric-signing \
     --default-algorithm ec-sign-p256-sha256
   ```

3. Grant IAM permissions to your service account:
   ```bash
   gcloud kms keys add-iam-policy-binding reviewer-signing-key \
     --location us-east1 \
     --keyring ledger-signing \
     --member serviceAccount:your-app@your-project.iam.gserviceaccount.com \
     --role roles/cloudkms.signerVerifier
   ```

### Supported Algorithms

- `ec-sign-p256-sha256` (secp256r1, SHA-256) — recommended
- `ec-sign-p384-sha384` (secp384r1, SHA-384)

**Note:** RSA and Ed25519 keys are NOT supported by this module. Use ECDSA keys only.

### Application Configuration

```properties
casehub.ledger.gcp-kms.project-id=your-gcp-project
casehub.ledger.gcp-kms.location-id=us-east1
casehub.ledger.gcp-kms.key-ring-id=ledger-signing
casehub.ledger.gcp-kms.refresh-interval=1h

# Map actorId to CryptoKey name
casehub.ledger.gcp-kms.key-mapping."claude:reviewer@v1"=reviewer-signing-key
casehub.ledger.gcp-kms.key-mapping."claude:tester@v1"=tester-signing-key
```

### GCP Authentication

The module uses the default Google Cloud credential provider chain:
1. Application Default Credentials (ADC) — `GOOGLE_APPLICATION_CREDENTIALS` env var
2. Workload Identity (GKE)
3. Compute Engine metadata service

For production deployments:
- **GKE:** Use Workload Identity to bind Kubernetes service accounts to GCP service accounts
- **Compute Engine / Cloud Run:** Use the instance/service default service account
- **Local development:** Run `gcloud auth application-default login`

## Running the Example

```bash
# Run the test (uses mock KMS client, no real GCP credentials required)
mvn test

# To test against a real Cloud KMS key:
# 1. Configure GCP credentials (gcloud auth or service account JSON)
# 2. Update application.properties with your project/location/keyring/key
# 3. Run the test
```

## How It Works

1. `GcpKmsAgentSigner` (from `casehub-ledger-gcp-kms-quarkus`) implements the `AgentSigner` SPI
2. On first sign() call for an actorId, it calls `GetPublicKey` and caches the PEM-encoded result
3. For each signature request, it:
   - Hashes the data with SHA-256
   - Calls `AsymmetricSign` with the hash and key version name
   - GCP Cloud KMS returns the DER-encoded ECDSA signature
4. The public key cache is periodically refreshed according to the configured interval

## See Also

- [GCP Cloud KMS Documentation](https://cloud.google.com/kms/docs)
- [casehub-ledger-gcp-kms-quarkus module](../../signing/gcp-kms-quarkus/)
