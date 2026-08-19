package io.casehub.ledger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ComplianceSupplement {
    String algorithmRef() default "";
    String contestationUri() default "";
    boolean humanOverrideAvailable() default false;
    String planRef() default "";
    String rationale() default "";
}
