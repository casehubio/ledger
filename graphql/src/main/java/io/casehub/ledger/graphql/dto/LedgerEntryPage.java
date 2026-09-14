package io.casehub.ledger.graphql.dto;

import io.casehub.platform.graphql.PageInfo;
import java.util.List;
import org.eclipse.microprofile.graphql.Type;

@Type("LedgerEntryPage")
public record LedgerEntryPage(List<LedgerEntryType> items, PageInfo pageInfo) {
}
