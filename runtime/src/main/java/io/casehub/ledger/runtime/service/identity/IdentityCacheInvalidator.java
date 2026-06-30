package io.casehub.ledger.runtime.service.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.platform.identity.ScimActorDIDProvider;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Bridges ledger key-rotation events to platform identity cache invalidation.
 *
 * <p>When {@code ScimActorDIDProvider} is active, this observer ensures its cache
 * is evicted on key rotation without creating a backwards dependency from the
 * platform module to ledger events.
 *
 * <p>ScimActorDIDProvider delegates to ScimAgentLookup, which extends
 * AbstractCachingIdentityProvider. The invalidate() method on ScimActorDIDProvider
 * propagates to the underlying lookup cache.
 */
@ApplicationScoped
public class IdentityCacheInvalidator {

    @Inject
    ActorDIDProvider actorDIDProvider;

    void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        if (actorDIDProvider instanceof ScimActorDIDProvider scim) {
            scim.invalidate(event.actorId());
        }
    }
}
