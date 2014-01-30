package org.kantega.reststop.helloworld.jaxrs;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

/**
 *
 */
public class ValidationMessageFeature implements Feature {

    public static final String BV_SEND_ERROR_IN_RESPONSE
            = "jersey.config.beanValidation.enableOutputValidationErrorEntity.server";

    @Override
    public boolean configure(FeatureContext context) {
        context.property(BV_SEND_ERROR_IN_RESPONSE, "true");
        return true;
    }
}
