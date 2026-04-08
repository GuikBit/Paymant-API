package br.com.holding.payments.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a cross-tenant operation that bypasses RLS.
 * These methods run without setting app.current_company_id,
 * and must use a separate database role with BYPASSRLS.
 * All usages are audited.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossTenant {
    String reason() default "";
}
