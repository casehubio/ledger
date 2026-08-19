package io.casehub.ledger.examples.audit;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.ledger.annotations.ActorId;
import io.casehub.ledger.annotations.Audited;
import io.casehub.ledger.annotations.SubjectId;
import io.casehub.ledger.annotations.TenancyId;
import io.casehub.ledger.api.model.LedgerEntryType;

/**
 * Document management service with annotation-driven audit trail.
 *
 * <p>Every method that changes document state is annotated with {@code @Audited}.
 * The ledger interceptor transparently records an immutable audit entry for each
 * successful operation — no manual entry construction needed.
 *
 * <p>This is the <strong>recommended approach</strong> for most consumers.
 * For consumers who need typed, queryable domain columns on ledger entries,
 * see the order-processing example (domain subclass pattern).
 */
@ApplicationScoped
public class DocumentService {

    /**
     * Upload a document — records an EVENT audit entry.
     */
    @Audited(actorRole = "Author")
    public Document upload(@SubjectId UUID documentId,
                           @ActorId String authorId,
                           String title, String content) {
        return new Document(documentId, title, content, "DRAFT");
    }

    /**
     * Submit a document for review — records a COMMAND audit entry.
     * COMMAND is used because this is a requested action, not an observation.
     */
    @Audited(entryType = LedgerEntryType.COMMAND, actorRole = "Author")
    public Document submitForReview(@SubjectId UUID documentId,
                                    @ActorId String authorId) {
        return new Document(documentId, null, null, "IN_REVIEW");
    }

    /**
     * Approve a document — records an EVENT with the reviewer's identity.
     */
    @Audited(actorRole = "Reviewer")
    public Document approve(@SubjectId UUID documentId,
                            @ActorId String reviewerId) {
        return new Document(documentId, null, null, "APPROVED");
    }

    /**
     * Reject a document — records an EVENT. auditFailures is true because
     * failed rejections (e.g. document already approved) should also be recorded.
     */
    @Audited(actorRole = "Reviewer", auditFailures = true)
    public Document reject(@SubjectId UUID documentId,
                           @ActorId String reviewerId,
                           String reason) {
        return new Document(documentId, null, null, "REJECTED");
    }

    /**
     * Archive a document from a scheduled job — uses @TenancyId because
     * there is no HTTP request context (no CurrentPrincipal).
     */
    @Audited(actorRole = "System")
    public void archive(@SubjectId UUID documentId,
                        @ActorId String systemActorId,
                        @TenancyId String tenancyId) {
        // archive logic
    }

    public record Document(UUID id, String title, String content, String status) {}
}
