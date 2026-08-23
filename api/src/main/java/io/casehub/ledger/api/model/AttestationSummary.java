package io.casehub.ledger.api.model;

import java.util.Map;

public record AttestationSummary(
    Map<AttestationVerdict, Long> verdictCounts,
    long totalAttestations,
    double meanConfidence,
    double minConfidence,
    double maxConfidence
) {
    public static final AttestationSummary EMPTY =
            new AttestationSummary(Map.of(), 0, 0.0, 0.0, 0.0);
}
