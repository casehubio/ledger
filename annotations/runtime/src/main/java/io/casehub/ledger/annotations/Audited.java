package io.casehub.ledger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import io.casehub.ledger.api.model.LedgerEntryType;

@InterceptorBinding
@Inherited
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    @Nonbinding String actorRole() default "";
    @Nonbinding LedgerEntryType entryType() default LedgerEntryType.EVENT;
    @Nonbinding boolean auditFailures() default false;
}
