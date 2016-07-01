/*
 * Copyright 2016 Kantega AS
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
package org.kantega.reststop.bayeux;

import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.AbstractService;
import org.eclipse.jetty.util.thread.ThreadPool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 *
 */
public class ReststopBayeuxServiceManager implements BayeuxServiceManager {

    private final ActualService actualService;
    private Map<String,ReststopBayeuxService> services;

    /**
     * <p>Instantiate a CometD service with the given name and max number of pooled threads.</p>
     *
     * @param bayeux     The BayeuxServer instance.
     * @param name       The name of the service (used as client ID prefix).
     * @param maxThreads The max size of a ThreadPool to create to handle messages.
     */
    public ReststopBayeuxServiceManager(BayeuxServer bayeux, String name, Integer maxThreads) {
        this.actualService = new ActualService(bayeux, name, nonNull(maxThreads) ? maxThreads : 0);
        this.services = new LinkedHashMap<>();
    }

    @Override
    public synchronized BayeuxService createService(String channelName) {
        ReststopBayeuxService service = services.get(channelName);
        if (isNull(service)) {
            actualService.addService(channelName, "accept");
            service = new ReststopBayeuxService(this, channelName);
            services.put(channelName, service);
        }
        return service;
    }

    protected synchronized BayeuxService destroyService(String channelName) {
        BayeuxService bayeuxService = services.remove(channelName);
        if (nonNull(bayeuxService)) {
            actualService.removeService(channelName, "accept");
        }
        return bayeuxService;
    }

    @Override
    public Collection<BayeuxService> getServices() {
        return new LinkedList<>(services.values());
    }

    @Override
    public BayeuxServer getBayeux() {
        return actualService.getBayeux();
    }

    @Override
    public LocalSession getLocalSession() {
        return actualService.getLocalSession();
    }

    @Override
    public ServerSession getServerSession() {
        return actualService.getServerSession();
    }

    @Override
    public ThreadPool getThreadPool() {
        return actualService.getThreadPool();
    }

    @Override
    public void setThreadPool(ThreadPool pool) {
        actualService.setThreadPool(pool);
    }

    @Override
    public boolean isSeeOwnPublishes() {
        return actualService.isSeeOwnPublishes();
    }

    @Override
    public void setSeeOwnPublishes(boolean seeOwnPublishes) {
        actualService.setSeeOwnPublishes(seeOwnPublishes);
    }

    public class ActualService extends AbstractService {

        private ActualService(BayeuxServer bayeux, String name, int maxThreads) {
            super(bayeux, name, maxThreads);
        }

        @Override
        protected void addService(String channelName, String methodName) {
            super.addService(channelName, methodName);
        }

        @Override
        protected void removeService(String channelName, String methodName) {
            super.removeService(channelName, methodName);
        }

        public void accept(ServerSession serverSession, ServerMessage serverMessage) {
            ReststopBayeuxService service = services.get(serverMessage.getChannel());
            if (nonNull(service)) {
                service.accept(serverSession, serverMessage);
            }
        }
    }
}
