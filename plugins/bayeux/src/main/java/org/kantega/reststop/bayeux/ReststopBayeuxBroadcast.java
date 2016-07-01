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

import org.cometd.bayeux.server.ServerMessage;

/**
 *
 */
public class ReststopBayeuxBroadcast implements BayeuxBroadcast, AutoCloseable {

    private BayeuxBroadcastManager bayeuxBroadcastManager;
    private String channelName;

    public ReststopBayeuxBroadcast(BayeuxBroadcastManager bayeuxBroadcastManager, String channelName) {
        this.bayeuxBroadcastManager = bayeuxBroadcastManager;
        this.channelName = channelName;
    }

    @Override
    public BayeuxBroadcastManager getBayeuxBroadcastManager() {
        return bayeuxBroadcastManager;
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public void broadcast(Object message) {
        bayeuxBroadcastManager.getBayeux().getChannel(channelName).publish(bayeuxBroadcastManager.getServerSession(), message);
    }

    @Override
    public void broadcast(ServerMessage.Mutable message) {
        bayeuxBroadcastManager.getBayeux().getChannel(channelName).publish(bayeuxBroadcastManager.getServerSession(), message);
    }

    @Override
    public void close() throws Exception {
        this.channelName = null;
    }
}
