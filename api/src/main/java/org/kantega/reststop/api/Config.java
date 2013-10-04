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
    String property() default "";

    boolean required() default true;

    String defaultValue() default "";

}
