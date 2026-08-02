package io.casehub.ledger.runtime.privacy;

/** Pass-through implementation — stores decision context JSON unchanged. */
public class PassThroughContentSanitiser implements ContentSanitiser {

    @Override
    public String sanitise(final String decisionContextJson) {
        return decisionContextJson;
    }
}
