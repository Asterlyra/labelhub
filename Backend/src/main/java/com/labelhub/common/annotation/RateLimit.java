package com.labelhub.common.annotation;

import com.labelhub.common.aspect.RateLimitAspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level rate limiting based on Redis sliding windows.
 *
 * @see RateLimitAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    enum Dimension {
        GLOBAL,
        IP,
        USER
    }

    Dimension[] dimensions() default {Dimension.GLOBAL};

    long count();

    long interval() default 1;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    String fallback() default "";

    enum TimeUnit {
        MILLISECONDS,
        SECONDS,
        MINUTES,
        HOURS,
        DAYS
    }
}
