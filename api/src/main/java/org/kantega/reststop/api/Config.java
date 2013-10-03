package org.kantega.reststop.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {

    /**
     * The name of the config property
     */
    String property();

    boolean required() default true;

    String description() default "";

    String defaultValue() default "";

}
