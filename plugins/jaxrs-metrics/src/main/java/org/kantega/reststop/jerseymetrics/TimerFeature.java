package org.kantega.reststop.jerseymetrics;

import javax.ws.rs.Path;
import javax.ws.rs.container.*;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

/**
 *
 */
@Provider
public class TimerFeature implements DynamicFeature {


    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {

        Path methodPath = resourceInfo.getResourceMethod().getAnnotation(Path.class);
        Path classPath  = resourceInfo.getResourceClass().getAnnotation(Path.class);

        Path path = methodPath != null ? methodPath : classPath;
        if(path != null) {
            UriBuilder builder = methodPath != null
                    ? UriBuilder.fromResource(resourceInfo.getResourceClass()).path(resourceInfo.getResourceClass(),resourceInfo.getResourceMethod().getName())
                    : UriBuilder.fromResource(resourceInfo.getResourceClass());

            String template = builder.toTemplate();
            context.register(new TimerBeforeFilter(template));
            context.register(TimerAfterFilter.class);
        }
    }
}
