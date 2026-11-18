package io.casehub.ledger.api.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single relevance factor contributed by any module on the classpath.
 *
 * <p>Each factor computes one dimension of attestation relevance (org proximity,
 * capability overlap, collaboration recency, etc.). The ledger's
 * {@code RelevanceComposer} aggregates all discovered factors, computes the
 * product of their weights (floored at a configurable minimum to prevent
 * compounding from driving weights to near-zero), and feeds the raw weights
 * into per-pass mass-preserving normalisation inside {@code TrustScoreComputer}.
 *
 * <p>With no factor beans on the classpath, all assessments are
 * {@link RelevanceAssessment#NEUTRAL} — backward-compatible.
 *
 * <p><b>Accuracy vs independence:</b> shared operational context makes
 * attestors' judgments more informed (accuracy), but also more correlated
 * (less independence). The default org-proximity factor is accuracy-biased.
 * An independence-biased factor can be contributed through this same SPI.
 *
 * <p><b>Not retrieval relevance.</b> This is attestation-relevance for trust
 * scoring. Distinct from RAG retrieval relevance (neocortex) and semantic
 * routing relevance (engine-ai).
 *
 * <p>Implementations must be CDI beans ({@code @ApplicationScoped}).
 */
public interface AttestationRelevanceFactor {

    RelevanceAssessment assess(AttestationRelevanceContext ctx);

    default Map<UUID, RelevanceAssessment> assessBatch(List<AttestationRelevanceContext> ctxs) {
        Map<UUID, RelevanceAssessment> result = new LinkedHashMap<>();
        for (AttestationRelevanceContext ctx : ctxs) {
            result.put(ctx.attestationId(), assess(ctx));
        }
        return result;
    }

    /** Short identifier for diagnostics, e.g. {@code "org-proximity"}. */
    String name();
}
