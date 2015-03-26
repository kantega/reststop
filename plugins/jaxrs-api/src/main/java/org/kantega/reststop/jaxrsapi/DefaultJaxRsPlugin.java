/*
 * Copyright 2015 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kantega.reststop.jaxrsapi;

import org.kantega.reststop.api.DefaultReststopPlugin;

import javax.ws.rs.core.Application;
import java.util.*;

/**
 *
 */
public abstract class DefaultJaxRsPlugin extends DefaultReststopPlugin implements JaxRsPlugin {
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

    protected void setProperty(String name, Object value) {
        application.setProperty(name, value);
    }

    private class JaxRsApplication extends Application {
        private final List<Object> jaxRsSingletonResources = new ArrayList<>();
        private final List<Class<?>> jaxRsContainerClasses = new ArrayList<>();
        private final Map<String, Object> properties = new HashMap<>();

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

        @Override
        public Map<String, Object> getProperties() {
            return new HashMap<>(properties);
        }

        public void setProperty(String name, Object value) {
            properties.put(name, value);
        }
    }
}
