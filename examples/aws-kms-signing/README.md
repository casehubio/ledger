# AWS KMS Signing Example

This example demonstrates remote signing of ledger entries via AWS Key Management Service (KMS). The private key never leaves AWS KMS; all signing operations happen via the AWS SDK.

## Features

- Remote signing via AWS KMS SDK
- Public key caching with scheduled refresh
- Actor-to-key mapping via configuration
- Mock KMS client for testing

## Production Requirements

### AWS KMS Configuration

1. Create an asymmetric signing key:
   ```bash
   aws kms create-key \
     --key-usage SIGN_VERIFY \
     --key-spec ECC_NIST_P256 \
     --description "Ledger agent signing key for reviewer"
   ```

2. Create an IAM policy granting sign and public key read permissions:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "kms:Sign",
           "kms:GetPublicKey"
         ],
         "Resource": "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012"
       }
     ]
   }
   ```

3. Attach the policy to your application's IAM role or user.

### Supported Key Specs

- `ECC_NIST_P256` (secp256r1) — recommended
- `ECC_NIST_P384` (secp384r1)
- `ECC_NIST_P521` (secp521r1)

**Note:** RSA keys are NOT supported. The module requires ECDSA signing keys.

### Application Configuration

```properties
casehub.ledger.aws-kms.region=us-east-1
casehub.ledger.aws-kms.refresh-interval=1h

# Map actorId to KMS Key ARN or alias
casehub.ledger.aws-kms.key-mapping."claude:reviewer@v1"=arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012
casehub.ledger.aws-kms.key-mapping."claude:tester@v1"=alias/tester-signing-key
```

### AWS Credentials

The module uses the default AWS SDK credential provider chain:
1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. System properties
3. Web identity token (EKS)
4. EC2 instance profile credentials
5. ECS container credentials

For production deployments, use IAM roles (EC2 instance profile, EKS service account, ECS task role) rather than static credentials.

## Running the Example

```bash
# Run the test (uses mock KMS client, no real AWS credentials required)
mvn test

# To test against a real KMS key:
# 1. Configure AWS credentials (AWS CLI, env vars, or IAM role)
# 2. Update application.properties with your KMS key ARN
# 3. Run the test
```

## How It Works

1. `AwsKmsAgentSigner` (from `casehub-ledger-aws-kms-quarkus`) implements the `AgentSigner` SPI
2. On first sign() call for an actorId, it calls `GetPublicKey` and caches the result
3. For each signature request, it calls `Sign` with the data and `ECDSA_SHA_256` algorithm
4. AWS KMS returns the DER-encoded ECDSA signature
5. The public key cache is periodically refreshed according to the configured interval

## See Also

- [AWS KMS Developer Guide](https://docs.aws.amazon.com/kms/latest/developerguide/)
- [casehub-ledger-aws-kms-quarkus module](../../signing/aws-kms-quarkus/)
