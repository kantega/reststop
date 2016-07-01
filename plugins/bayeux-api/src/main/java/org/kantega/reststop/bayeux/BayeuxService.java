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
import org.cometd.bayeux.server.ServerSession;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 *
 */

public interface BayeuxService extends AutoCloseable {

    BayeuxServiceManager getBayeuxServiceManager();
    String getChannelName();

    void addListener(MessageListener listener);
    void removeListener(MessageListener listener);
    Collection<MessageListener> getListeners();

    void close();

    @FunctionalInterface
    interface MessageListener extends BiConsumer<ServerSession,ServerMessage> {}

}
