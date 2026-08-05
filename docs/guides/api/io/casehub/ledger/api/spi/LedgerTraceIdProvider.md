# io.casehub.ledger.api.spi.LedgerTraceIdProvider

**Package:** `io.casehub.ledger.api.spi`

**Kind:** `interface`

SPI for supplying the current distributed trace ID to the ledger.

<p>
The default implementation (`OtelTraceIdProvider`) reads from the active
OpenTelemetry span context. Replace this bean to integrate with a different
tracing system or to override the trace ID in tests.

## Methods

### `public abstract java.util.Optional<java.lang.String> currentTraceId()`

Returns the current trace ID, or empty if no active trace is present.

#### Returns

the trace ID (W3C 32-char hex format when using OTel), or empty
