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
import org.cometd.bayeux.server.ServerSession;

import java.util.Collection;

/**
 *
 */
public class ReststopBayeuxBroadcastManager implements BayeuxBroadcastManager {

    private BayeuxServer bayeux;
    private LocalSession localSession;
    private ServerSession serverSession;

    public ReststopBayeuxBroadcastManager(BayeuxServer bayeux, String name) {
        this.bayeux = bayeux;
        localSession = this.bayeux.newLocalSession(name);
        localSession.handshake();
    }

    @Override
    public BayeuxBroadcast createBroadcast(String channelName) {
        return null;
    }

    @Override
    public Collection<BayeuxBroadcast> getBroadcasts() {
        return null;
    }

    @Override
    public BayeuxServer getBayeux() {
        return bayeux;
    }

    @Override
    public LocalSession getLocalSession() {
        return localSession;
    }

    @Override
    public ServerSession getServerSession() {
        return localSession.getServerSession();
    }
}
