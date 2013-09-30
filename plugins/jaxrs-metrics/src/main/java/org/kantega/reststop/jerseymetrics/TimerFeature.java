package org.kantega.reststop.jerseymetrics;

import javax.ws.rs.Path;
import javax.ws.rs.container.*;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

/**
 *
 */
@Provider
public class TimerFeature implements DynamicFeature {


    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {

        if(resourceInfo.getResourceClass().getAnnotation(Path.class) != null) {
            context.register(TimerBeforeFilter.class);
            context.register(TimerAfterFilter.class);
        }
    }
}
