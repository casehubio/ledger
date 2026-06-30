# Azure Key Vault Signing Example

This example demonstrates remote signing of ledger entries via Azure Key Vault. The private key never leaves Azure Key Vault; all signing operations happen via the Azure SDK.

## Features

- Remote signing via Azure Key Vault SDK
- Public key caching with scheduled refresh
- Actor-to-key mapping via configuration
- Mock CryptographyClient for testing

## Production Requirements

### Azure Key Vault Configuration

1. Create a Key Vault (if you don't have one):
   ```bash
   az keyvault create \
     --name your-vault \
     --resource-group your-resource-group \
     --location eastus
   ```

2. Create an EC signing key:
   ```bash
   az keyvault key create \
     --vault-name your-vault \
     --name reviewer-signing-key \
     --kty EC \
     --curve P-256 \
     --ops sign verify
   ```

3. Grant your application's managed identity the "Key Vault Crypto User" role:
   ```bash
   az role assignment create \
     --role "Key Vault Crypto User" \
     --assignee <managed-identity-principal-id> \
     --scope /subscriptions/<subscription-id>/resourceGroups/<resource-group>/providers/Microsoft.KeyVault/vaults/<vault-name>/keys/<key-name>
   ```

### Supported Key Types

- `EC` with curve `P-256` (secp256r1) — recommended
- `EC` with curve `P-384` (secp384r1)
- `EC` with curve `P-521` (secp521r1)

**Note:** RSA keys are NOT supported. The module requires EC keys only.

### Application Configuration

```properties
casehub.ledger.azure-keyvault.vault-url=https://your-vault.vault.azure.net/
casehub.ledger.azure-keyvault.refresh-interval=1h

# Map actorId to Key Vault key name
casehub.ledger.azure-keyvault.key-mapping."claude:reviewer@v1"=reviewer-signing-key
casehub.ledger.azure-keyvault.key-mapping."claude:tester@v1"=tester-signing-key
```

### Azure Authentication

The module uses the default Azure credential provider chain:
1. Environment variables (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_CLIENT_SECRET`)
2. Managed Identity (Azure VMs, App Service, Container Instances, AKS)
3. Azure CLI (`az login`)
4. IntelliJ / VS Code Azure plugins

For production deployments:
- **Azure VMs / App Service / Container Instances:** Use system-assigned or user-assigned managed identity
- **AKS:** Use Azure AD Workload Identity (bind Kubernetes service account to Azure managed identity)
- **Local development:** Run `az login`

## Running the Example

```bash
# Run the test (uses mock CryptographyClient, no real Azure credentials required)
mvn test

# To test against a real Key Vault:
# 1. Configure Azure credentials (az login or managed identity)
# 2. Update application.properties with your vault URL and key names
# 3. Run the test
```

## How It Works

1. `AzureKeyVaultAgentSigner` (from `casehub-ledger-azure-keyvault-quarkus`) implements the `AgentSigner` SPI
2. On first sign() call for an actorId, it calls `getKey()` to fetch the JsonWebKey and extracts the public key
3. For each signature request, it:
   - Hashes the data with SHA-256
   - Calls `sign(ES256, hash)` with the digest
   - Azure Key Vault returns the DER-encoded ECDSA signature
4. The public key cache is periodically refreshed according to the configured interval

## See Also

- [Azure Key Vault Documentation](https://learn.microsoft.com/en-us/azure/key-vault/)
- [casehub-ledger-azure-keyvault-quarkus module](../../signing/azure-keyvault-quarkus/)
