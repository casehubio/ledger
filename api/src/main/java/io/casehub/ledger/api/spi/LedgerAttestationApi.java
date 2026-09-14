package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.view.AttestationView;
import io.casehub.ledger.api.view.CreateAttestationRequest;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.UUID;

@McpDomain("ledger/attestations")
public interface LedgerAttestationApi {

    @PlatformQuery("List attestations for a ledger entry, optionally filtered by capability tag")
    List<AttestationView> listAttestations(@PathParam UUID entryId,
                                            String tenancyId, String capabilityTag);

    @PlatformMutation("Create an attestation on a ledger entry")
    AttestationView createAttestation(CreateAttestationRequest request, String tenancyId);
}
