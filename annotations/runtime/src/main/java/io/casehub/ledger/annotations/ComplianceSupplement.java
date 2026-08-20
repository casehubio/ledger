package io.casehub.ledger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

@InterceptorBinding
@Inherited
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ComplianceSupplement {
    @Nonbinding String algorithmRef() default "";
    @Nonbinding String contestationUri() default "";
    @Nonbinding boolean humanOverrideAvailable() default false;
    @Nonbinding String planRef() default "";
    @Nonbinding String rationale() default "";
}
