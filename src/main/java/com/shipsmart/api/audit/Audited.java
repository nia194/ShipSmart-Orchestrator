package com.shipsmart.api.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as producing an audit-log entry after successful return.
 * Handled by {@link AuditAspect} — runs on a dedicated async executor so audit
 * writes never block the request thread.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String entity();
}
