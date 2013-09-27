package org.kantega.reststop.api.jaxrs;

import org.kantega.reststop.api.DefaultReststopPlugin;

import javax.ws.rs.core.Application;
import java.util.*;

/**
 *
 */
public class DefaultJaxRsPlugin extends DefaultReststopPlugin implements JaxRsPlugin {
    private final JaxRsApplication application = new JaxRsApplication();

    protected void addJaxRsSingletonResource(Object resource) {
        application.addJaxRsSingletonResource(resource);
    }

    @Override
    public Collection<Application> getJaxRsApplications() {
        return Collections.<Application>singletonList(application);
    }

    protected void addJaxRsContainerClass(Class<?> clazz) {
        application.addJaxRsContainerClass(clazz);
    }

    private class JaxRsApplication extends Application {
        private final List<Object> jaxRsSingletonResources = new ArrayList<>();
        private final List<Class<?>> jaxRsContainerClasses = new ArrayList<>();

        protected void addJaxRsSingletonResource(Object resource) {
            jaxRsSingletonResources.add(resource);
        }

        protected void addJaxRsContainerClass(Class<?> clazz) {
            jaxRsContainerClasses.add(clazz);
        }

        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(jaxRsContainerClasses);
        }

        @Override
        public Set<Object> getSingletons() {
            return new HashSet<>(jaxRsSingletonResources);
        }
    }
}
