# io.casehub.ledger.api.model.supplement.ComplianceSupplement

**Package:** `io.casehub.ledger.api.model.supplement`

**Kind:** `class`

Supplement carrying compliance, governance, and GDPR Art.22 decision snapshot fields.

<h2>GDPR Article 22 — Automated Decision-Making</h2>
<p>
Article 22 of the GDPR requires that automated decisions be explainable. Data subjects
have the right to receive "meaningful information about the logic involved" in any
automated decision that significantly affects them. The following fields provide the
structured evidence needed to satisfy this requirement:
<ul>
<li>`.algorithmRef` — identifies which model, rule engine, or algorithm version
produced the decision, enabling reproducibility and audit.</li>
<li>`.confidenceScore` — the producing system's stated confidence (0.0–1.0),
satisfying the requirement to disclose "the significance and envisaged
consequences" of the decision.</li>
<li>`.contestationUri` — where the data subject can request human review
or challenge the decision, satisfying the right to contest under Art.22(3).</li>
<li>`.humanOverrideAvailable` — whether a human review path exists,
satisfying the Art.22(2) safeguard requirement.</li>
<li>`.decisionContext` — full JSON snapshot of observable state at the moment
of the decision, providing the "meaningful information" required by Arts.13–15.</li>
</ul>

<h2>Governance fields</h2>
<p>
`.planRef` and `.rationale` record the policy version and stated basis
for the decision. `.evidence` and `.detail` carry structured evidence
and free-text overflow respectively.

<h2>Usage</h2>

<pre>`JpaComplianceSupplement cs = new JpaComplianceSupplement();
cs.algorithmRef = "classification-model-v3.2";
cs.confidenceScore = 0.91;
cs.contestationUri = "https://example.com/decisions/challenge";
cs.humanOverrideAvailable = true;
cs.decisionContext = "{\"inputs\":{\"riskScore\":42`}";
entry.attach(cs);
}</pre>

## Fields

### `algorithmRef` (`java.lang.String`)

Identifier of the model, rule engine, or algorithm version that produced
the decision. Examples: `"gpt-4o"`, `"risk-classifier-v2.1"`,
`"approval-rules-2026-Q1"`. Required for reproducibility audits.

### `confidenceScore` (`java.lang.Double`)

The producing system's stated confidence in this decision, in the range
0.0 (no confidence) to 1.0 (certainty). Null when not applicable (e.g.
deterministic rule engines). Satisfies the GDPR requirement to disclose
the significance and envisaged consequences of the decision.

### `contestationUri` (`java.lang.String`)

URI where the data subject can request human review or formally challenge
this decision, satisfying the contestation right under GDPR Art.22(3).
Example: `"https://example.com/decisions/{entryId`/challenge"}.

### `decisionContext` (`java.lang.String`)

Full JSON snapshot of observable state at the moment of this decision.
Provides the "meaningful information about the logic involved" required by
GDPR Arts.13–15 and the technical logging required by EU AI Act Art.12.

### `detail` (`java.lang.String`)

Free-text or JSON detail — delegation targets, rejection reasons, etc.

### `evidence` (`java.lang.String`)

Structured evidence supplied by the actor.

### `humanOverrideAvailable` (`java.lang.Boolean`)

Whether a human review path exists for this decision, satisfying the
Art.22(2)(b) safeguard requirement.

### `planRef` (`java.lang.String`)

Reference to the policy or procedure version that governed this action.

### `rationale` (`java.lang.String`)

The actor's stated basis for the decision.

## Constructors

### `public ComplianceSupplement()`
