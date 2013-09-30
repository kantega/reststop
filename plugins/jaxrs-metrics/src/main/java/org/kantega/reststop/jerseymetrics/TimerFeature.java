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

        Path path = resourceInfo.getResourceClass().getAnnotation(Path.class);
        if(path != null) {
            context.register(new TimerBeforeFilter(path.value()));
            context.register(TimerAfterFilter.class);
        }
    }
}
