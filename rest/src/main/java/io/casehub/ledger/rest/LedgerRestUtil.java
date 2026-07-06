package io.casehub.ledger.rest;

import io.casehub.platform.api.identity.TenancyConstants;

final class LedgerRestUtil {

    private LedgerRestUtil() {
    }

    static String requireTenancyId(final String tenancyId) {
        return tenancyId != null ? tenancyId : TenancyConstants.DEFAULT_TENANT_ID;
    }
}
