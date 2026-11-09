package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.view.AttestationView;
import io.casehub.ledger.api.view.CreateAttestationRequest;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@McpDomain(value = "ledger/attestations", app = "ledger", summary = "Peer attestation lifecycle — create and query attestations")
@ApplicationScoped
public class DefaultLedgerAttestationApi {

    private final io.casehub.ledger.core.api.LedgerAttestationApiCore core;

    @Inject
    DefaultLedgerAttestationApi(LedgerEntryRepository repository) {
        this.core = new io.casehub.ledger.core.api.LedgerAttestationApiCore(repository);
    }

    @PlatformQuery("List attestations for a ledger entry. Filter by capabilityTag to scope results")
    public List<AttestationView> listAttestations(@PathParam UUID entryId,
                                                  @ContextParam("tenancyId") String tenancyId,
                                                  Optional<String> capabilityTag) {
        return core.listAttestations(entryId, tenancyId, capabilityTag.orElse(null));
    }

    @PlatformMutation("Create an attestation on a ledger entry. verdict: SOUND, FLAGGED, ENDORSED, CHALLENGED. attestorType: HUMAN, AGENT, SYSTEM")
    public AttestationView createAttestation(CreateAttestationRequest request,
                                             @ContextParam("tenancyId") String tenancyId) {
        return core.createAttestation(request, tenancyId);
    }

    static AttestationView toView(LedgerAttestation a) {
        return io.casehub.ledger.core.api.LedgerAttestationApiCore.toView(a);
    }
}
