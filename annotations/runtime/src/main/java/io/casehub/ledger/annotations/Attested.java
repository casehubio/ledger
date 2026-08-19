package io.casehub.ledger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.casehub.ledger.api.model.AttestationVerdict;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Attested {
    AttestationVerdict verdict() default AttestationVerdict.SOUND;
    double confidence() default -1.0;
    String capabilityTag();
}
