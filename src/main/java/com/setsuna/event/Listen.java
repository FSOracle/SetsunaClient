package com.setsuna.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event listener. The method must be non-static, return
 * {@code void} and take exactly one parameter that is a subtype of {@link Event}.
 * <p>
 * Handlers with a higher {@link #priority()} are invoked first.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Listen {

    int priority() default Priority.NORMAL;
}
